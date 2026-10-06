package com.agronomia.data.repository

import com.agronomia.data.remote.ChatCompletionRequest
import com.agronomia.data.remote.ChatMessage
import com.agronomia.data.remote.ContentPart
import com.agronomia.data.remote.ImageUrl
import com.agronomia.data.remote.NetworkModule
import com.agronomia.data.remote.PlantApiService
import com.agronomia.data.remote.PlantResultDto
import com.agronomia.data.remote.ResponseFormat
import com.agronomia.domain.model.PlantResult
import com.agronomia.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Error de identificación con mensaje claro para mostrar al usuario. */
class PlantIdentificationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

interface PlantRepository {
    /**
     * Identifica una planta a partir de su imagen ya codificada en Base64 (JPEG).
     * @return [Result.success] con [PlantResult] o [Result.failure] con
     *         [PlantIdentificationException] cuyo mensaje es apto para UI.
     */
    suspend fun identifyPlant(imageBase64: String): Result<PlantResult>
}

class PlantRepositoryImpl(
    private val apiService: PlantApiService = NetworkModule.plantApiService,
    private val json: Json = NetworkModule.json
) : PlantRepository {

    override suspend fun identifyPlant(imageBase64: String): Result<PlantResult> =
        withContext(Dispatchers.IO) {
            if (Constants.API_KEY.isBlank()) {
                return@withContext Result.failure(
                    PlantIdentificationException(
                        "Falta la API key. Configura AI_API_KEY en local.properties."
                    )
                )
            }
            if (imageBase64.isBlank()) {
                return@withContext Result.failure(
                    PlantIdentificationException("La imagen está vacía. Vuelve a capturarla.")
                )
            }

            try {
                val response = apiService.identifyPlant(
                    authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                    request = buildRequest(imageBase64)
                )

                val content = response.choices.firstOrNull()?.message?.content
                    ?: throw PlantIdentificationException(
                        "La IA no devolvió contenido. Inténtalo de nuevo."
                    )

                Result.success(parsePlantResult(content))
            } catch (e: PlantIdentificationException) {
                Result.failure(e)
            } catch (e: HttpException) {
                Result.failure(PlantIdentificationException(mapHttpError(e), e))
            } catch (e: SocketTimeoutException) {
                Result.failure(
                    PlantIdentificationException(
                        "La solicitud tardó demasiado (timeout). Revisa tu conexión e inténtalo de nuevo.",
                        e
                    )
                )
            } catch (e: UnknownHostException) {
                Result.failure(
                    PlantIdentificationException(
                        "Sin conexión a internet. Verifica tu red e inténtalo de nuevo.",
                        e
                    )
                )
            } catch (e: ConnectException) {
                Result.failure(
                    PlantIdentificationException(
                        "No se pudo conectar con el servidor. Verifica tu conexión e inténtalo de nuevo.",
                        e
                    )
                )
            } catch (e: SerializationException) {
                Result.failure(
                    PlantIdentificationException(
                        "La respuesta del servidor no es un JSON válido o no tiene el formato esperado.",
                        e
                    )
                )
            } catch (e: IOException) {
                Result.failure(
                    PlantIdentificationException(
                        "Sin conexión a internet o error de red. Verifica tu red e inténtalo de nuevo.",
                        e
                    )
                )
            } catch (e: Exception) {
                Result.failure(
                    PlantIdentificationException(
                        "Error inesperado al identificar la planta: ${e.message ?: "inténtalo de nuevo"}.",
                        e
                    )
                )
            }
        }

    private fun buildRequest(imageBase64: String): ChatCompletionRequest =
        ChatCompletionRequest(
            model = Constants.AI_MODEL,
            temperature = 0.0,
            responseFormat = ResponseFormat(type = "json_object"),
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = listOf(
                        ContentPart(type = "text", text = Constants.IDENTIFY_PROMPT),
                        ContentPart(
                            type = "image_url",
                            imageUrl = ImageUrl(
                                url = Constants.IMAGE_DATA_URL_PREFIX + imageBase64
                            )
                        )
                    )
                )
            )
        )

    private fun parsePlantResult(rawContent: String): PlantResult {
        val cleaned = stripCodeFences(rawContent).trim()
        if (cleaned.isEmpty()) {
            throw PlantIdentificationException("La IA devolvió una respuesta vacía. Inténtalo de nuevo.")
        }

        val dto = try {
            json.decodeFromString<PlantResultDto>(cleaned)
        } catch (e: SerializationException) {
            throw PlantIdentificationException(
                "La respuesta de la IA no es un JSON válido con el formato esperado.",
                e
            )
        }

        if (dto.commonName.isBlank() && dto.scientificName.isBlank()) {
            throw PlantIdentificationException(
                "La IA no pudo identificar la planta en la imagen. Prueba con una foto más nítida."
            )
        }

        return PlantResult(
            commonName = dto.commonName.trim(),
            scientificName = dto.scientificName.trim(),
            confidence = dto.confidence.coerceIn(0.0, 1.0).toFloat()
        )
    }

    private fun stripCodeFences(content: String): String {
        val trimmed = content.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun mapHttpError(e: HttpException): String {
        val code = e.code()
        val serverMessage = extractServerMessage(e)
        return when (code) {
            401 -> "No autorizado (401): la API key es inválida o no tiene permisos. " +
                "Revisa AI_API_KEY en local.properties."
            403 -> "Acceso denegado (403): tu API key no tiene acceso a este servicio."
            404 -> "Recurso no encontrado (404): revisa la URL del endpoint en AI_BASE_URL."
            429 -> "Demasiadas solicitudes (429): espera un momento e inténtalo de nuevo."
            in 500..599 -> "El servidor de IA falló (error $code). Inténtalo más tarde." +
                if (serverMessage != null) " Detalle: $serverMessage" else ""
            else -> "El servidor respondió con error $code." +
                if (serverMessage != null) " Detalle: $serverMessage" else ""
        }
    }

    private fun extractServerMessage(e: HttpException): String? = try {
        val body = e.response()?.errorBody()?.string()
        if (body.isNullOrBlank()) {
            null
        } else {
            json.decodeFromString<com.agronomia.data.remote.ChatCompletionResponse>(body)
                .error?.message
                ?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }
}
