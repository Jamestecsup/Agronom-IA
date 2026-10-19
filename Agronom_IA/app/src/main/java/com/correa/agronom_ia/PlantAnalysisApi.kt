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

data class PlantAnalysis(val identification: PlantIdentification, val care: String? = null)
data class ChatTurn(val role: String, val text: String)

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

internal object PlantAnalysisRequestValidator {
    fun validatePhotoCount(count: Int) {
        require(count >= 1) { "Selecciona al menos una imagen para identificar la planta." }
        require(count <= 5) { "Pl@ntNet admite como máximo cinco imágenes por identificación." }
    }
}

/** Keeps image identification independent from the selected conversational model. */
internal object PlantAnalysisApi {
    private const val PLANTNET_ENDPOINT = "https://my-api.plantnet.org/v2/identify/all"
    private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024

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
            if (code !in 200..299) throw httpError(code, body)
            return try {
                PlantNetResponseParser.parse(body)
            } catch (error: org.json.JSONException) {
                throw IllegalStateException("Pl@ntNet devolvió una respuesta que no se pudo interpretar.")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpError(code: Int, body: String): IllegalStateException {
        val detail = runCatching { JSONObject(body).optString("message").takeIf(String::isNotBlank) }.getOrNull()
        val message = when (code) {
            401, 403 -> "Pl@ntNet rechazó la clave configurada (HTTP $code)."
            413 -> "Las imágenes superan el límite permitido por Pl@ntNet."
            429 -> "Pl@ntNet alcanzó su límite de uso. Espera e inténtalo de nuevo."
            in 500..599 -> "Pl@ntNet presenta un problema temporal."
            else -> detail ?: "Pl@ntNet respondió con HTTP $code."
        }
        return IllegalStateException(message)
    }
}
