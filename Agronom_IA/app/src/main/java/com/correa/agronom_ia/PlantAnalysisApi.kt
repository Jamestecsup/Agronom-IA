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

data class PlantAnalysis(val identification: PlantIdentification, val care: PlantCareGuide? = null)

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
        val parts = JSONObject(body).optJSONArray("parts")
            ?: throw IllegalStateException("OpenCode no devolvió las partes de la respuesta de Space Bunny.")
        val content = buildList {
            for (index in 0 until parts.length()) {
                val part = parts.optJSONObject(index) ?: continue
                if (part.optString("type") == "text") part.optString("text").takeIf { it.isNotBlank() && it != "null" }?.let(::add)
            }
        }.lastOrNull() ?: throw IllegalStateException("Space Bunny devolvió una respuesta vacía.")
        val cleanedContent = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val jsonText = cleanedContent.substringAfter('{', cleanedContent).let { "{" + it.substringBeforeLast('}', it) + "}" }
        val json = runCatching { JSONObject(jsonText) }
            .recoverCatching { JSONObject(cleanedContent) }
            .getOrElse { throw IllegalStateException("Space Bunny devolvió una guía con formato no válido.") }
        fun required(key: String, label: String): String = json.optString(key)
            .takeIf { it.isNotBlank() && it != "null" }
            ?: throw IllegalStateException("La guía de Space Bunny está incompleta: falta $label.")
        fun checked(value: String, label: String): String {
            require(value.length <= 1_500) { "La respuesta de Space Bunny es demasiado larga en $label." }
            require(!Regex("[\\u4E00-\\u9FFF\\u3040-\\u30FF\\uAC00-\\uD7AF]").containsMatchIn(value)) {
                "Space Bunny devolvió texto mezclado o ilegible en $label."
            }
            return value
        }
        return PlantCareGuide(
            watering = checked(required("watering", "riego"), "riego"),
            lighting = checked(required("lighting", "iluminación"), "iluminación"),
            petToxicity = checked(required("pet_toxicity", "toxicidad para mascotas"), "toxicidad"),
            warnings = checked(json.optString("warnings").takeUnless { it == "null" }.orEmpty(), "advertencias")
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
    private const val SPACE_BUNNY_MODEL = "space-bunny-free"
    private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
    private const val OPENCODE_TIMEOUT_MS = 120_000

    fun identify(files: List<File>, onProgress: (String) -> Unit): PlantIdentification {
        PlantAnalysisRequestValidator.validatePhotoCount(files.size)
        require(files.all { it.isFile && it.canRead() && it.length() > 0 }) { "Una imagen seleccionada ya no está disponible o no se puede leer." }
        require(files.sumOf(File::length) <= MAX_TOTAL_BYTES) { "El conjunto de imágenes supera el límite de 50 MB de Pl@ntNet." }
        val key = BuildConfig.PLANTNET_API_KEY.trim()
        require(key.isNotEmpty()) { "Configura PLANTNET_API_KEY en las propiedades locales de Gradle para habilitar Pl@ntNet." }

        onProgress("Enviando ${files.size} ${if (files.size == 1) "imagen" else "imágenes"} a Pl@ntNet…")
        return identifyWithPlantNet(files, key)
            ?: throw IllegalStateException("Pl@ntNet no encontró una identificación probable. Prueba con una foto más clara de una sola planta.")
    }

    fun generateCare(identification: PlantIdentification, onProgress: (String) -> Unit): PlantCareGuide {
        onProgress("Consultando cuidados con Space Bunny a través de OpenCode local…")
        return requestCare(identification)
    }

    private fun identifyWithPlantNet(files: List<File>, apiKey: String): PlantIdentification? {
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
        val systemPrompt = """Eres un asistente especializado en el cuidado de plantas. Responde en español y exclusivamente con un objeto JSON válido, sin Markdown ni texto fuera del JSON. Incluye exactamente estas claves con valores de texto: "watering", "lighting", "pet_toxicity", "warnings". Usa frases breves. En riego e iluminación distingue recomendaciones generales de datos propios de la especie; en toxicidad indica claramente si no hay información fiable. No inventes datos específicos, cantidades, toxicidad ni hechos. Si la identificación es incierta, adviértelo."""
        val userPrompt = buildString {
            append("Identificación hecha por Pl@ntNet. No se adjunta ni se debe inferir ninguna imagen.\n")
            append("Nombre científico: ${identification.scientificName}\n")
            identification.family?.let { append("Familia: $it\n") }
            identification.commonName?.let { append("Nombre común informado por Pl@ntNet: $it\n") }
            append("Coincidencia del candidato: ${identification.scorePercent}. Este valor es una puntuación de ordenación, no una garantía.\n")
            if (identification.uncertain) append("La identificación se considera incierta; formula la guía con cautela.\n")
            if (identification.alternatives.isNotEmpty()) append("Otros candidatos: ${identification.alternatives.joinToString()}\n")
            append("Devuelve solo un objeto JSON válido y compacto con los campos watering, lighting, pet_toxicity y warnings. No uses Markdown ni escapes o comandos de formato.")
        }
        val baseUrl = BuildConfig.OPENCODE_SERVER_BASE_URL.trim().trimEnd('/')
        val session = requestJson("$baseUrl/session", JSONObject().put("title", "Cuidados de ${identification.scientificName}"))
        val sessionId = session.optString("id").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("OpenCode local no creó una sesión para Space Bunny.")
        val payload = JSONObject()
            .put("model", JSONObject().put("providerID", "opencode").put("modelID", SPACE_BUNNY_MODEL))
            .put("system", systemPrompt)
            .put("tools", JSONObject())
            .put("parts", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", userPrompt)))
        try {
            val response = requestJson("$baseUrl/session/$sessionId/message", payload, OPENCODE_TIMEOUT_MS)
            val firstAttempt = runCatching { SpaceBunnyResponseParser.parse(response.toString()) }
            if (firstAttempt.isSuccess) return firstAttempt.getOrThrow()

            val correction = "La respuesta anterior no era JSON válido o estaba incompleta. Corrígela ahora. Devuelve únicamente un objeto JSON sintácticamente válido, compacto, sin Markdown ni texto adicional, con exactamente las claves watering, lighting, pet_toxicity y warnings. No añadas detalles no verificados."
            val retry = JSONObject()
                .put("model", JSONObject().put("providerID", "opencode").put("modelID", SPACE_BUNNY_MODEL))
                .put("system", systemPrompt)
                .put("tools", JSONObject())
                .put("parts", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", correction)))
            val correctedResponse = requestJson("$baseUrl/session/$sessionId/message", retry, OPENCODE_TIMEOUT_MS)
            return runCatching { SpaceBunnyResponseParser.parse(correctedResponse.toString()) }
                .getOrElse { throw IllegalStateException("Space Bunny no pudo devolver una guía estructurada válida después de un reintento.") }
        } finally {
            runCatching { request("DELETE", "$baseUrl/session/$sessionId") }
        }
    }

    private fun requestJson(url: String, payload: JSONObject, timeout: Int = 20_000): JSONObject {
        val body = request("POST", url, payload, timeout)
        return try { JSONObject(body) } catch (_: org.json.JSONException) {
            throw IllegalStateException("OpenCode local devolvió una respuesta no válida.")
        }
    }

    private fun request(method: String, url: String, payload: JSONObject? = null, timeout: Int = 20_000): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = timeout
            setRequestProperty("Accept", "application/json")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (payload != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw httpError("OpenCode local / Space Bunny", code, body)
            return body
        } finally { connection.disconnect() }
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
