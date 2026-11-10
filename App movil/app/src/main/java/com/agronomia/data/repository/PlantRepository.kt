package com.agronomia.data.repository

import com.agronomia.data.remote.DjangoApiService
import com.agronomia.data.remote.DjangoCandidateDto
import com.agronomia.data.remote.DjangoPlantDto
import com.agronomia.data.remote.NetworkModule
import com.agronomia.domain.model.MaterialItem
import com.agronomia.domain.model.PlantResult
import com.agronomia.domain.model.PlantSection
import com.agronomia.domain.model.SpeciesCandidate
import com.agronomia.domain.model.WordMeaning
import com.agronomia.util.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/*
 * Repositorio de identificación.
 *
 * Todo el análisis (Pl@ntNet + Gemini con las skills) ocurre en el backend
 * Django: la app solo sube las fotos, pide la identificación y, si hay dudas,
 * confirma la candidata elegida. Las claves de IA viven en el `.env` del
 * backend y no viajan en el APK.
 */

/** Error de identificación con mensaje claro para mostrar al usuario. */
class PlantIdentificationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * La especie se identificó, pero la IA no tiene información de cuidado
 * suficiente. No es un fallo de red: la UI lo explica sin pedir reintento.
 */
class InsufficientInfoException(val plantName: String, message: String) : Exception(message)

interface PlantRepository {
    /**
     * Analiza las fotos y decide el camino:
     * [AnalysisResult.Identified] (50% o más, con toda la cadena),
     * [AnalysisResult.Ambiguous] (20% a 49%: el agricultor elige),
     * [AnalysisResult.Unidentified] (menos de 20%: solo la guía),
     * [AnalysisResult.Insufficient] (identificada, sin datos de cuidado) o
     * [AnalysisResult.Failed] (mensaje apto para UI).
     *
     * @param images JPEGs ya preparados (1 a 5).
     * @param organs órgano por foto, en el mismo orden (auto/flower/leaf/fruit/bark).
     */
    suspend fun analyze(images: List<ByteArray>, organs: List<String>): AnalysisResult

    /**
     * Genera la información completa para la candidata elegida por el
     * agricultor. [siblings] son las otras candidatas (dan contexto).
     */
    suspend fun confirmCandidate(
        candidate: SpeciesCandidate,
        siblings: List<SpeciesCandidate>
    ): Result<PlantResult>
}

/** Resultado del análisis: identificación, desambiguación, guía o fallo. */
sealed interface AnalysisResult {
    /** Identificación confiable (50% o más), con toda la información. */
    data class Identified(val plant: PlantResult) : AnalysisResult

    /** Con dudas (20% a 49%): el agricultor elige entre estas candidatas. */
    data class Ambiguous(val candidates: List<SpeciesCandidate>) : AnalysisResult

    /**
     * Sin detección (menos de 20%): solo la guía de cómo tomar mejores fotos,
     * sin lista de candidatas.
     */
    data class Unidentified(val message: String) : AnalysisResult

    /**
     * La especie se identificó, pero la IA no tiene datos de cuidado
     * suficientes: se avisa sin mostrar una ficha vacía.
     */
    data class Insufficient(val plantName: String, val message: String) : AnalysisResult

    /** No se pudo identificar por un error (mensaje apto para UI). */
    data class Failed(val message: String) : AnalysisResult
}

private val TEXT_PLAIN = "text/plain".toMediaType()

class PlantRepositoryImpl(
    private val json: Json = NetworkModule.json
) : PlantRepository {

    /** Último lote subido: lo usa [confirmCandidate] para reutilizar Pl@ntNet. */
    private var lastBatchId: String? = null

    private val djangoService: DjangoApiService by lazy { NetworkModule.djangoApiService }

    override suspend fun analyze(images: List<ByteArray>, organs: List<String>): AnalysisResult =
        withContext(Dispatchers.IO) {
            if (images.isEmpty() || images.all { it.isEmpty() }) {
                return@withContext AnalysisResult.Failed(
                    "No hay imágenes para identificar. Vuelve a capturarla."
                )
            }
            // Se descartan bytes vacíos sin romper el orden (con su órgano).
            val pairs = images.mapIndexedNotNull { index, bytes ->
                if (bytes.isEmpty()) null
                else bytes to organs.getOrElse(index) { "auto" }.ifBlank { "auto" }
            }
            if (pairs.isEmpty()) {
                return@withContext AnalysisResult.Failed(
                    "No hay imágenes para identificar. Vuelve a capturarla."
                )
            }

            try {
                // 1. Subir las fotos al backend Django (1 a 5).
                val parts = pairs.mapIndexed { index, pair ->
                    MultipartBody.Part.createFormData(
                        "photos[]",
                        "foto_$index.jpg",
                        pair.first.toRequestBody("image/jpeg".toMediaType())
                    )
                }
                val upload = djangoService.uploadImages(
                    Constants.backendUrl(Constants.BACKEND_UPLOAD_PATH),
                    parts
                )
                if (!upload.success || upload.batchId.isBlank()) {
                    return@withContext AnalysisResult.Failed(
                        "No se pudieron subir las fotos. Revisa el formato e inténtalo de nuevo."
                    )
                }
                lastBatchId = upload.batchId

                // 2. Django identifica (Pl@ntNet) y genera la ficha (Gemini).
                // El backend usa un solo órgano por lote: si hay mezcla, auto.
                val organ = pairs.map { it.second }.distinct().singleOrNull() ?: "auto"
                val response = djangoService.identify(
                    Constants.backendUrl(Constants.BACKEND_IDENTIFY_PATH),
                    upload.batchId.toRequestBody(TEXT_PLAIN),
                    organ.toRequestBody(TEXT_PLAIN)
                )

                when (response.status) {
                    "identified" -> {
                        val plant = response.plant
                        if (plant != null) {
                            AnalysisResult.Identified(plant.toPlantResult())
                        } else {
                            AnalysisResult.Failed(
                                "No pudimos preparar la ficha de la planta. Inténtalo de nuevo."
                            )
                        }
                    }

                    "ambiguous" -> {
                        val candidates = response.candidates.map { it.toSpeciesCandidate() }
                        if (candidates.isEmpty()) {
                            AnalysisResult.Unidentified(
                                response.message
                                    ?: "La coincidencia es muy baja. Toma mejores fotos e inténtalo de nuevo."
                            )
                        } else {
                            AnalysisResult.Ambiguous(candidates)
                        }
                    }

                    "unidentified" -> AnalysisResult.Unidentified(
                        response.message
                            ?: "La coincidencia es muy baja. Toma mejores fotos e inténtalo de nuevo."
                    )

                    "insufficient" -> AnalysisResult.Insufficient(
                        plantName = response.plantName,
                        message = response.message
                            ?: "Identificamos la planta, pero no encontramos información " +
                            "suficiente sobre sus cuidados. Prueba con otra foto."
                    )

                    else -> AnalysisResult.Failed(
                        "No pudimos analizar la planta. Revisa la foto e inténtalo de nuevo."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                AnalysisResult.Failed(mapHttpError(e))
            } catch (e: SocketTimeoutException) {
                AnalysisResult.Failed(
                    "La solicitud está tardando más de lo esperado. Inténtalo de nuevo."
                )
            } catch (e: UnknownHostException) {
                AnalysisResult.Failed(
                    "No pudimos conectar. Revisa tu conexión e inténtalo de nuevo."
                )
            } catch (e: ConnectException) {
                AnalysisResult.Failed(
                    "No pudimos conectar con el servicio. Revisa tu conexión e inténtalo de nuevo."
                )
            } catch (e: SerializationException) {
                AnalysisResult.Failed(
                    "No pudimos completar el análisis. Inténtalo de nuevo."
                )
            } catch (e: IOException) {
                AnalysisResult.Failed(
                    "No pudimos conectar. Revisa tu conexión e inténtalo de nuevo."
                )
            } catch (e: Exception) {
                AnalysisResult.Failed(
                    "No pudimos analizar la planta. Revisa la foto y vuelve a intentarlo."
                )
            }
        }

    /**
     * Ejecuta la cadena completa para la candidata elegida por el agricultor.
     * Django reutiliza el JSON de Pl@ntNet del lote y genera la ficha.
     */
    override suspend fun confirmCandidate(
        candidate: SpeciesCandidate,
        siblings: List<SpeciesCandidate>
    ): Result<PlantResult> =
        withContext(Dispatchers.IO) {
            try {
                val batchId = lastBatchId
                    ?: return@withContext Result.failure(
                        PlantIdentificationException(
                            "Vuelve a escanear la planta para generar su información."
                        )
                    )
                val response = djangoService.confirm(
                    Constants.backendUrl(Constants.BACKEND_CONFIRM_PATH),
                    batchId.toRequestBody(TEXT_PLAIN),
                    candidate.scientificName.toRequestBody(TEXT_PLAIN)
                )
                val plant = response.plant
                if (response.status == "insufficient") {
                    Result.failure(
                        InsufficientInfoException(
                            plantName = response.plantName,
                            message = response.message
                                ?: "No encontramos información suficiente sobre esta planta."
                        )
                    )
                } else if (response.success && plant != null) {
                    Result.success(plant.toPlantResult())
                } else {
                    Result.failure(
                        PlantIdentificationException(
                            "No se pudo preparar la ficha. Inténtalo de nuevo en unos minutos."
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(
                    PlantIdentificationException(
                        "No pudimos preparar la ficha. Inténtalo de nuevo o prueba con otra foto.",
                        e
                    )
                )
            }
        }

    /** Convierte la ficha del backend Django al modelo de la app. */
    private fun DjangoPlantDto.toPlantResult(): PlantResult = PlantResult(
        commonName = commonName,
        scientificName = scientificName,
        family = family,
        confidence = confidence.toFloat(),
        sections = sections.map { section ->
            PlantSection(
                key = section.key,
                title = section.title,
                body = section.body,
                terms = section.terms.map { WordMeaning(it.word, it.meaning) },
                materials = section.materials.map { MaterialItem(it.name, it.detail) }
            )
        }
    )

    /** Convierte una candidata del backend y le añade foto de referencia. */
    private fun DjangoCandidateDto.toSpeciesCandidate(): SpeciesCandidate {
        val referenceImage = try {
            if (scientificName.isBlank()) "" else fetchCandidateImageUrl(scientificName)
        } catch (_: Exception) {
            ""
        }
        return SpeciesCandidate(
            scientificName = scientificName,
            commonNames = commonNames,
            family = family,
            confidence = confidence.toFloat(),
            imageUrl = referenceImage,
            hint = hint,
            hintTerms = terms.map { WordMeaning(it.word, it.meaning) }
        )
    }

    /**
     * Foto de referencia del nombre científico en Wikimedia Commons (miniatura).
     * No usa Pl@ntNet: es solo una referencia visual de internet. Reintenta una
     * vez ante límites momentáneos; si no hay foto, devuelve "" sin bloquear.
     */
    private fun fetchCandidateImageUrl(scientificName: String): String {
        if (scientificName.isBlank()) return ""
        repeat(2) { attempt ->
            val url = tryFetchCandidateImageUrl(scientificName)
            if (url.isNotBlank()) return url
            // Una espera breve antes del segundo intento (límite momentáneo).
            if (attempt == 0) {
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return ""
                }
            }
        }
        return ""
    }

    private fun tryFetchCandidateImageUrl(scientificName: String): String {
        var connection: HttpURLConnection? = null
        return try {
            val query = URLEncoder.encode(scientificName, "UTF-8")
            // Se piden varios resultados y se usa el primero CON miniatura:
            // el primero no siempre trae imagen (y la API a veces limita).
            val apiUrl = "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                "&generator=search&gsrsearch=$query&gsrlimit=5&gsrnamespace=6" +
                "&prop=imageinfo&iiprop=url&iiurlwidth=400"
            connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            // Wikimedia exige User-Agent identificatorio (sin él responde 403).
            connection.setRequestProperty("User-Agent", Constants.WIKIMEDIA_USER_AGENT)
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(text) as? JsonObject ?: return ""
            val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject ?: return ""
            for ((_, pageElement) in pages) {
                val info = (pageElement as? JsonObject)?.get("imageinfo") as? JsonArray
                    ?: continue
                val first = info.firstOrNull() as? JsonObject ?: continue
                val thumb = (first["thumburl"] as? JsonPrimitive)?.contentOrNull
                    ?: (first["url"] as? JsonPrimitive)?.contentOrNull
                if (!thumb.isNullOrBlank()) return thumb
            }
            ""
        } catch (_: Exception) {
            ""
        } finally {
            connection?.disconnect()
        }
    }

    /** Traduce los errores HTTP del backend a mensajes para el agricultor. */
    private fun mapHttpError(e: HttpException): String {
        return when (e.code()) {
            400 -> "No pudimos procesar las fotos. Revísalas e inténtalo de nuevo."
            401 -> "Tu sesión terminó. Inicia sesión nuevamente."
            403 -> "No tienes permiso para realizar esta acción."
            404 -> "No encontramos la planta solicitada. Vuelve a intentarlo."
            413 -> "La foto es demasiado pesada. Elige una imagen más ligera."
            415, 422 -> "No pudimos usar esa foto. Comprueba que sea JPG o PNG y que no pese demasiado."
            429 -> "El servicio está ocupado. Espera un momento y vuelve a intentarlo."
            in 500..599 -> "No pudimos analizar la planta. Revisa la foto e inténtalo de nuevo."
            else -> "Ocurrió un problema al analizar la planta. Inténtalo de nuevo."
        }
    }
}
