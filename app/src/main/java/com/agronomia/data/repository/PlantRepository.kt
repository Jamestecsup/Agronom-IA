package com.agronomia.data.repository

import com.agronomia.data.remote.ChatCompletionRequest
import com.agronomia.data.remote.ChatMessage
import com.agronomia.data.remote.ContentPart
import com.agronomia.data.remote.ImageUrl
import com.agronomia.data.remote.NetworkModule
import com.agronomia.data.remote.PlantApiService
import com.agronomia.data.remote.PlantInfoDto
import com.agronomia.data.remote.PlantNetApiService
import com.agronomia.data.remote.PlantNetSpecies
import com.agronomia.data.remote.PlantResultDto
import com.agronomia.data.remote.RefinedPromptDto
import com.agronomia.data.remote.ResponseFormat
import com.agronomia.domain.model.PlantResult
import com.agronomia.util.Constants
import com.agronomia.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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
     * Identifica una planta y obtiene información sobre ella.
     *
     * Flujo:
     *  1. Pl@ntNet identifica la especie y devuelve su ficha (nombre, familia, IDs).
     *  2. Gemini genera un prompt optimizado a partir de esa ficha.
     *  3. Gemini ejecuta ese prompt y devuelve el JSON final con la información.
     *
     * Si Pl@ntNet no identifica con confianza suficiente, se usa Gemini (visión)
     * como respaldo (la cadena de prompts se aplica igual con lo que se tenga).
     *
     * @return [Result.success] con [PlantResult] o [Result.failure] con
     *         [PlantIdentificationException] cuyo mensaje es apto para UI.
     */
    suspend fun identifyPlant(imageBytes: ByteArray): Result<PlantResult>
}

class PlantRepositoryImpl(
    private val json: Json = NetworkModule.json
) : PlantRepository {

    // Resolución perezosa: si la URL no está configurada preferimos devolver un
    // error legible en [identifyPlant] en lugar de lanzar al construir la clase.
    private val geminiService: PlantApiService by lazy { NetworkModule.plantApiService }
    private val plantNetService: PlantNetApiService by lazy { NetworkModule.plantNetApiService }

    override suspend fun identifyPlant(imageBytes: ByteArray): Result<PlantResult> =
        withContext(Dispatchers.IO) {
            if (imageBytes.isEmpty()) {
                return@withContext Result.failure(
                    PlantIdentificationException("La imagen está vacía. Vuelve a capturarla.")
                )
            }

            try {
                val species = identifySpecies(imageBytes)
                val info = runInfoChain(species)
                Result.success(species.toPlantResult(info))
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

    /**
     * Identifica la especie: primero Pl@ntNet; si no da una coincidencia confiable,
     * cae al respaldo con Gemini visión (que exige la certeza configurada).
     */
    private suspend fun identifySpecies(imageBytes: ByteArray): SpeciesMatch {
        val plantNetMatch = try {
            identifyWithPlantNet(imageBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // Cualquier fallo de Pl@ntNet → se intenta el respaldo.
        }

        if (plantNetMatch != null && plantNetMatch.confidence >= Constants.PLANTNET_MIN_CONFIDENCE) {
            return plantNetMatch
        }

        // Respaldo: Gemini visión.
        val geminiMatch = identifyWithGeminiVision(imageBytes)
        val hasName = geminiMatch.scientificName.isNotBlank() &&
            !geminiMatch.scientificName.equals("unknown", true)
        if (!hasName || geminiMatch.confidence < Constants.GEMINI_FALLBACK_MIN_CONFIDENCE) {
            throw PlantIdentificationException(
                "No se pudo verificar la planta con certeza. Envía más imágenes " +
                    "(flor, hoja o tallo) para una mejor identificación."
            )
        }
        return geminiMatch
    }

    /**
     * Llama a Pl@ntNet y devuelve la mejor coincidencia con su ficha completa
     * (o null si no hay resultados).
     */
    private suspend fun identifyWithPlantNet(imageBytes: ByteArray): SpeciesMatch? {
        val imagePart = MultipartBody.Part.createFormData(
            "images",
            "plant.jpg",
            imageBytes.toRequestBody("image/jpeg".toMediaType())
        )
        val organs = "auto".toRequestBody("text/plain".toMediaType())

        val response = plantNetService.identify(Constants.plantNetIdentifyUrl(), imagePart, organs)
        val best = response.results.maxByOrNull { it.score } ?: return null
        val species = best.species ?: return null
        val scientificName = species.scientificNameWithoutAuthor.ifBlank { species.scientificName }
        if (scientificName.isBlank()) return null

        return SpeciesMatch(
            commonName = species.commonNames.firstOrNull().orEmpty(),
            scientificName = scientificName,
            family = species.family?.scientificNameWithoutAuthor.orEmpty(),
            confidence = best.score.coerceIn(0.0, 1.0).toFloat(),
            plantNetSpecies = species,
            bestMatch = response.bestMatch.orEmpty()
        )
    }

    /** Respaldo: pide a Gemini que identifique la planta en la imagen. */
    private suspend fun identifyWithGeminiVision(imageBytes: ByteArray): SpeciesMatch {
        val response = geminiService.identifyPlant(
            url = Constants.identifyEndpointUrl(),
            authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
            request = buildVisionRequest(imageBytes)
        )

        // Algunos servidores responden 200 con un objeto "error" en el cuerpo.
        response.error?.let { apiError ->
            throw PlantIdentificationException(
                apiError.message?.takeIf { it.isNotBlank() }
                    ?: "La IA devolvió un error al procesar la imagen."
            )
        }

        val content = response.choices.firstOrNull()?.message?.content
            ?: throw PlantIdentificationException("La IA no devolvió contenido. Inténtalo de nuevo.")

        val dto = parsePlantDto(content)
        return SpeciesMatch(
            commonName = dto.commonName.trim(),
            scientificName = dto.scientificName.trim(),
            family = "",
            confidence = dto.confidence.coerceIn(0.0, 1.0).toFloat(),
            plantNetSpecies = null,
            bestMatch = ""
        )
    }

    /**
     * Cadena de información: Gemini paso 1 genera un prompt optimizado a partir
     * de la ficha; Gemini paso 2 ejecuta ese prompt y devuelve el JSON final.
     * Es "best-effort": si algún paso falla, se devuelve la identificación sin
     * información extra (o la información parcial).
     */
    private suspend fun runInfoChain(species: SpeciesMatch): PlantInfoDto {
        val refinedPrompt = generateRefinedPrompt(species)
        if (refinedPrompt.isBlank()) return PlantInfoDto()
        return generatePlantInfo(refinedPrompt)
    }

    /** Paso 1: pide a Gemini que refine la ficha en un prompt de investigación. */
    private suspend fun generateRefinedPrompt(species: SpeciesMatch): String {
        return try {
            val message = Constants.REFINE_PROMPT + "\n\n" + buildPlantNetFicha(species)
            val response = geminiService.identifyPlant(
                url = Constants.identifyEndpointUrl(),
                authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                request = buildTextRequest(message)
            )
            val content = response.choices.firstOrNull()?.message?.content ?: return ""
            val dto = parseRefinedPromptDto(content) ?: return ""
            dto.prompt.cleanInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    /** Paso 2: pide a Gemini la información final (JSON estructurado). */
    private suspend fun generatePlantInfo(refinedPrompt: String): PlantInfoDto {
        return try {
            val message = Constants.INFO_PROMPT + "\n\nPrompt de investigación:\n" + refinedPrompt
            val response = geminiService.identifyPlant(
                url = Constants.identifyEndpointUrl(),
                authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                request = buildTextRequest(message)
            )
            val content = response.choices.firstOrNull()?.message?.content ?: return PlantInfoDto()
            parseInfoDto(content)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PlantInfoDto()
        }
    }

    /** Construye un texto legible con la ficha de Pl@ntNet para alimentar el prompt. */
    private fun buildPlantNetFicha(species: SpeciesMatch): String {
        val sp = species.plantNetSpecies
        return buildString {
            append("Ficha de la planta:\n")
            if (species.scientificName.isNotBlank()) {
                append("- Nombre científico: ").append(species.scientificName)
                sp?.scientificNameAuthorship?.takeIf { it.isNotBlank() }?.let {
                    append(" ").append(it)
                }
                append("\n")
            }
            sp?.commonNames?.takeIf { it.isNotEmpty() }?.let {
                append("- Nombres comunes: ").append(it.joinToString(", ")).append("\n")
            }
            sp?.genus?.scientificNameWithoutAuthor?.takeIf { it.isNotBlank() }?.let {
                append("- Género: ").append(it).append("\n")
            }
            if (species.family.isNotBlank()) {
                append("- Familia: ").append(species.family).append("\n")
            }
            append("- Similitud (score Pl@ntNet): ")
                .append((species.confidence * 100).toInt())
                .append("%\n")
            sp?.gbif?.id?.takeIf { it.isNotBlank() }?.let {
                append("- GBIF ID: ").append(it).append("\n")
            }
            sp?.powo?.id?.takeIf { it.isNotBlank() }?.let {
                append("- POWO ID: ").append(it).append("\n")
            }
            sp?.iucn?.let { iucn ->
                val parts = listOfNotNull(
                    iucn.id?.takeIf { it.isNotBlank() },
                    iucn.category?.takeIf { it.isNotBlank() }
                )
                if (parts.isNotEmpty()) {
                    append("- IUCN: ").append(parts.joinToString(" | ")).append("\n")
                }
            }
            if (species.bestMatch.isNotBlank()) {
                append("- Mejor coincidencia (bestMatch): ").append(species.bestMatch).append("\n")
            }
            if (sp == null) {
                append("(Identificada por IA de visión; no hay ficha completa de Pl@ntNet.)\n")
            }
        }.trim()
    }

    private fun buildVisionRequest(imageBytes: ByteArray): ChatCompletionRequest =
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
                                url = Constants.IMAGE_DATA_URL_PREFIX +
                                    ImageUtils.toBase64Jpeg(imageBytes)
                            )
                        )
                    )
                )
            )
        )

    private fun buildTextRequest(prompt: String): ChatCompletionRequest =
        ChatCompletionRequest(
            model = Constants.AI_MODEL,
            temperature = 0.0,
            responseFormat = ResponseFormat(type = "json_object"),
            messages = listOf(
                ChatMessage(
                    role = "user",
                    content = listOf(ContentPart(type = "text", text = prompt))
                )
            )
        )

    private fun parsePlantDto(rawContent: String): PlantResultDto {
        val jsonText = extractJsonObject(rawContent)
        if (jsonText.isBlank()) {
            throw PlantIdentificationException("La IA devolvió una respuesta vacía. Inténtalo de nuevo.")
        }
        return try {
            json.decodeFromString<PlantResultDto>(jsonText)
        } catch (e: SerializationException) {
            throw PlantIdentificationException(
                "La respuesta de la IA no es un JSON válido con el formato esperado.",
                e
            )
        }
    }

    private fun parseRefinedPromptDto(rawContent: String): RefinedPromptDto? {
        val jsonText = extractJsonObject(rawContent)
        if (jsonText.isBlank()) return null
        return try {
            json.decodeFromString<RefinedPromptDto>(jsonText)
        } catch (_: SerializationException) {
            null
        }
    }

    private fun parseInfoDto(rawContent: String): PlantInfoDto {
        val jsonText = extractJsonObject(rawContent)
        if (jsonText.isBlank()) return PlantInfoDto()
        return try {
            json.decodeFromString<PlantInfoDto>(jsonText)
        } catch (_: SerializationException) {
            PlantInfoDto()
        }
    }

    /**
     * Extrae el primer objeto JSON del texto devuelto por la IA.
     * Tolera bloques Markdown ```json ... ``` y texto adicional antes o después,
     * siempre que el JSON sea un objeto plano.
     */
    internal fun extractJsonObject(content: String): String {
        val trimmed = content.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            trimmed.substring(start, end + 1)
        } else {
            trimmed
        }
    }

    private fun mapHttpError(e: HttpException): String {
        val code = e.code()
        val serverMessage = extractServerMessage(e)
        return when (code) {
            400 -> "Solicitud inválida (400). Revisa la imagen e inténtalo de nuevo." +
                if (serverMessage != null) " Detalle: $serverMessage" else ""
            401 -> "No autorizado (401): una de las API keys es inválida o no tiene permisos. " +
                "Revisa PLANTNET_API_KEY / AI_API_KEY en local.properties."
            403 -> "Acceso denegado (403): tu API key no tiene acceso a este servicio."
            404 -> "Recurso no encontrado (404): revisa la URL del endpoint en local.properties."
            429 -> "Demasiadas solicitudes (429): se agotó la cuota o pediste demasiado. " +
                "Espera un momento e inténtalo de nuevo."
            in 500..599 -> "El servidor falló (error $code). Inténtalo más tarde." +
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
            // Intenta el formato de Gemini/OpenAI; si no, busca un "message" simple.
            json.decodeFromString<com.agronomia.data.remote.ChatCompletionResponse>(body)
                .error?.message?.takeIf { it.isNotBlank() }
                ?: extractSimpleMessage(body)
        }
    } catch (_: Exception) {
        null
    }

    private fun extractSimpleMessage(body: String): String? =
        Regex("\"(?:message|error)\"\\s*:\\s*\"([^\"]+)\"")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)

    /**
     * Coincidencia de especie (identificación) con la ficha de Pl@ntNet cuando
     * está disponible; se usa para alimentar la cadena de información.
     */
    private data class SpeciesMatch(
        val commonName: String,
        val scientificName: String,
        val family: String,
        val confidence: Float,
        val plantNetSpecies: PlantNetSpecies? = null,
        val bestMatch: String = ""
    ) {
        fun toPlantResult(info: PlantInfoDto): PlantResult = PlantResult(
            commonName = commonName.trim(),
            scientificName = scientificName.trim(),
            family = family.trim().ifBlank { info.family.cleanInfo() },
            confidence = confidence,
            description = info.description.cleanInfo(),
            light = info.light.cleanInfo(),
            watering = info.watering.cleanInfo(),
            flowering = info.flowering.cleanInfo(),
            uses = info.uses.cleanInfo(),
            care = info.care.cleanInfo()
        )
    }
}

/** Normaliza un texto devuelto por la IA: sin espacios y tratando "unknown" como vacío. */
private fun String.cleanInfo(): String =
    trim().takeUnless { it.equals("unknown", true) }.orEmpty()