package com.correa.agronom_ia

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

data class PlantIdentification(
    val scientificName: String,
    val family: String?,
    val commonName: String?,
    val score: Double,
    val alternatives: List<String>,
    val uncertain: Boolean
) {
    val scorePercent: String get() = String.format(Locale.getDefault(), "%.1f%%", score * 100)
}

data class PlantCareGuide(
    val watering: String,
    val lighting: String,
    val petToxicity: String,
    val warnings: String
)

data class PlantAnalysis(val identification: PlantIdentification, val care: PlantCareGuide)

internal object PlantNetResponseParser {
    fun parse(body: String): PlantIdentification? {
        val results = JSONObject(body).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val top = results.optJSONObject(0) ?: return null
        val species = top.optJSONObject("species") ?: return null
        val scientificName = species.scientificName() ?: return null
        val score = top.optDouble("score", Double.NaN)
        require(score.isFinite() && score in 0.0..1.0) { "Pl@ntNet devolvió un porcentaje de coincidencia inválido." }
        val family = species.optJSONObject("family")?.optString("scientificName")?.nonBlank()
        val commonName = species.optJSONArray("commonNames")?.firstNonBlankString()
        val alternatives = buildList {
            for (index in 1 until results.length()) {
                val name = results.optJSONObject(index)?.optJSONObject("species")?.scientificName()
                if (name != null) add(name)
                if (size == 2) break
            }
        }
        val secondScore = results.optJSONObject(1)?.optDouble("score", 0.0) ?: 0.0
        // A score is a ranking signal, not a calibrated probability of correctness.
        val uncertain = score < 0.5 || (results.length() > 1 && score - secondScore < 0.08)
        return PlantIdentification(scientificName, family, commonName, score, alternatives, uncertain)
    }

    private fun JSONObject.scientificName(): String? = optString("scientificNameWithoutAuthor").nonBlank()
        ?: optString("scientificName").nonBlank()

    private fun String.nonBlank(): String? = takeIf { it.isNotBlank() && it != "null" }

    private fun JSONArray.firstNonBlankString(): String? {
        for (index in 0 until length()) optString(index).takeIf { it.isNotBlank() && it != "null" }?.let { return it }
        return null
    }
}

internal object SpaceBunnyResponseParser {
    fun parse(body: String): PlantCareGuide {
        val content = JSONObject(body).optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content")
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Space Bunny devolvió una respuesta vacía.")
        val jsonText = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = runCatching { JSONObject(jsonText) }
            .getOrElse { throw IllegalStateException("Space Bunny devolvió una guía con formato no válido.") }
        fun required(key: String, label: String): String = json.optString(key)
            .takeIf { it.isNotBlank() && it != "null" }
            ?: throw IllegalStateException("La guía de Space Bunny está incompleta: falta $label.")
        return PlantCareGuide(
            watering = required("watering", "riego"),
            lighting = required("lighting", "iluminación"),
            petToxicity = required("pet_toxicity", "toxicidad para mascotas"),
            warnings = json.optString("warnings").takeUnless { it == "null" }.orEmpty()
        )
    }
}

internal object PlantAnalysisRequestValidator {
    fun validatePhotoCount(count: Int) {
        require(count >= 1) { "Selecciona al menos una imagen para identificar la planta." }
        require(count <= 5) { "Pl@ntNet admite como máximo cinco imágenes por identificación." }
    }
}

internal object PlantAnalysisApi {
    private const val PLANTNET_ENDPOINT = "https://my-api.plantnet.org/v2/identify/all"
    private const val OPENCODE_ENDPOINT = "https://opencode.ai/inference/v1/chat/completions"
    private const val SPACE_BUNNY_MODEL = "space-bunny-free"
    private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024

    fun analyze(files: List<File>, onProgress: (String) -> Unit): PlantAnalysis {
        PlantAnalysisRequestValidator.validatePhotoCount(files.size)
        require(files.all { it.isFile && it.canRead() && it.length() > 0 }) { "Una imagen seleccionada ya no está disponible o no se puede leer." }
        require(files.sumOf(File::length) <= MAX_TOTAL_BYTES) { "El conjunto de imágenes supera el límite de 50 MB de Pl@ntNet." }
        val key = BuildConfig.PLANTNET_API_KEY.trim()
        require(key.isNotEmpty()) { "Configura PLANTNET_API_KEY en las propiedades locales de Gradle para habilitar Pl@ntNet." }

        onProgress("Enviando ${files.size} ${if (files.size == 1) "imagen" else "imágenes"} a Pl@ntNet…")
        val identification = identify(files, key)
            ?: throw IllegalStateException("Pl@ntNet no encontró una identificación probable. Prueba con una foto más clara de una sola planta.")
        onProgress("Consultando cuidados con Space Bunny…")
        val care = requestCare(identification)
        return PlantAnalysis(identification, care)
    }

    private fun identify(files: List<File>, apiKey: String): PlantIdentification? {
        val boundary = "PlantNet-${UUID.randomUUID()}"
        val endpoint = Uri.parse(PLANTNET_ENDPOINT).buildUpon()
            .appendQueryParameter("api-key", apiKey)
            .appendQueryParameter("lang", "es")
            .appendQueryParameter("nb-results", "3")
            .build().toString()
        val parts = files.map { file ->
            val header = "--$boundary\r\nContent-Disposition: form-data; name=\"images\"; filename=\"${file.name}\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8)
            header to file
        }
        val footer = "--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val contentLength = parts.sumOf { (header, file) -> header.size.toLong() + file.length() + 2 } + footer.size
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
            setFixedLengthStreamingMode(contentLength)
        }
        try {
            connection.outputStream.buffered().use { output ->
                parts.forEach { (header, file) ->
                    output.write(header)
                    file.inputStream().buffered().use { it.copyTo(output, 32 * 1024) }
                    output.write("\r\n".toByteArray())
                }
                output.write(footer)
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw httpError("Pl@ntNet", code, body)
            return try {
                PlantNetResponseParser.parse(body)
            } catch (error: org.json.JSONException) {
                throw IllegalStateException("Pl@ntNet devolvió una respuesta que no se pudo interpretar.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun requestCare(identification: PlantIdentification): PlantCareGuide {
        val systemPrompt = """Eres un asistente especializado en el cuidado de plantas. Responde en español y únicamente con un objeto JSON válido, sin Markdown, con las cadenas "watering", "lighting", "pet_toxicity" y "warnings". Explica frecuencia y recomendaciones de riego; luz; toxicidad para perros y gatos, o indica claramente que no puede confirmarse. Distingue hechos conocidos de recomendaciones generales. No inventes información específica de una especie. Si la identificación es incierta, adviértelo y no presentes recomendaciones como definitivas. En caso de información insuficiente para un campo, indícalo explícitamente."""
        val userPrompt = buildString {
            append("Identificación hecha por Pl@ntNet. No se adjunta ni se debe inferir ninguna imagen.\n")
            append("Nombre científico: ${identification.scientificName}\n")
            identification.family?.let { append("Familia: $it\n") }
            identification.commonName?.let { append("Nombre común informado por Pl@ntNet: $it\n") }
            append("Coincidencia del candidato: ${identification.scorePercent}. Este valor es una puntuación de ordenación, no una garantía.\n")
            if (identification.uncertain) append("La identificación se considera incierta; formula la guía con cautela.\n")
            if (identification.alternatives.isNotEmpty()) append("Otros candidatos: ${identification.alternatives.joinToString()}\n")
            append("Devuelve JSON con esos cuatro campos.")
        }
        val payload = JSONObject()
            .put("model", SPACE_BUNNY_MODEL)
            .put("stream", false)
            .put("max_tokens", 450)
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", userPrompt)))
        val connection = (URL(OPENCODE_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            BuildConfig.OPENCODE_API_KEY.trim().takeIf(String::isNotEmpty)?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw httpError("Space Bunny", code, body)
            return try {
                SpaceBunnyResponseParser.parse(body)
            } catch (error: org.json.JSONException) {
                throw IllegalStateException("Space Bunny devolvió una respuesta que no se pudo interpretar.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpError(service: String, code: Int, body: String): IllegalStateException {
        val detail = runCatching { JSONObject(body).optString("message").takeIf(String::isNotBlank) }.getOrNull()
        val explanation = when (code) {
            400, 415 -> "revisa el formato y el contenido de la imagen o solicitud"
            401, 403 -> "revisa la clave o los permisos configurados"
            413 -> "el tamaño total de la solicitud excede el límite del servicio"
            429 -> "se alcanzó el límite de uso; espera y vuelve a intentarlo"
            in 500..599 -> "el servicio presenta un problema temporal"
            else -> detail ?: "respuesta HTTP $code"
        }
        return IllegalStateException("$service: $explanation (HTTP $code).")
    }
}
