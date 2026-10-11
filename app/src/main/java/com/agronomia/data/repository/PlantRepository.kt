package com.agronomia.data.repository

import com.agronomia.data.remote.CategoryInfoDto
import com.agronomia.data.remote.ChatCompletionRequest
import com.agronomia.data.remote.ChatMessage
import com.agronomia.data.remote.ContentPart
import com.agronomia.data.remote.GlossaryDto
import com.agronomia.data.remote.GlossaryTermDto
import com.agronomia.data.remote.ImageUrl
import com.agronomia.data.remote.MaterialItemDto
import com.agronomia.data.remote.MaterialsDto
import com.agronomia.data.remote.NetworkModule
import com.agronomia.data.remote.PlantApiService
import com.agronomia.data.remote.PlantInfoDto
import com.agronomia.data.remote.PlantNetApiService
import com.agronomia.data.remote.PlantNetSpecies
import com.agronomia.data.remote.PlantResultDto
import com.agronomia.data.remote.RefinedPromptDto
import com.agronomia.data.remote.ResponseFormat
import com.agronomia.domain.model.PlantResult
import com.agronomia.domain.model.PlantSection
import com.agronomia.domain.model.MaterialItem
import com.agronomia.domain.model.SpeciesCandidate
import com.agronomia.domain.model.WordMeaning
import com.agronomia.util.Constants
import com.agronomia.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

/** Error de identificación con mensaje claro para mostrar al usuario. */
class PlantIdentificationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

interface PlantRepository {
    /**
     * Analiza las fotos y decide el camino:
     * [AnalysisResult.Identified] (50% o más, con toda la cadena),
     * [AnalysisResult.Ambiguous] (21% a 49%: el agricultor elige),
     * [AnalysisResult.Unidentified] (20% o menos: solo la guía) o
     * [AnalysisResult.Failed] (mensaje apto para UI).
     *
     * @param images JPEGs ya preparados (1 a [ImageUtils.MAX_IMAGES]).
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
    /** Identificación confiable (50% o más, o visión al 100%), con toda la información. */
    data class Identified(val plant: PlantResult) : AnalysisResult

    /** Con dudas (21% a 49%): el agricultor elige entre estas candidatas. */
    data class Ambiguous(val candidates: List<SpeciesCandidate>) : AnalysisResult

    /**
     * Sin detección (20% o menos y sin respaldo): solo la guía de cómo tomar
     * mejores fotos, sin lista de candidatas.
     */
    data class Unidentified(val message: String) : AnalysisResult

    /** No se pudo identificar por un error (mensaje apto para UI). */
    data class Failed(val message: String) : AnalysisResult
}

class PlantRepositoryImpl(
    private val json: Json = NetworkModule.json
) : PlantRepository {

    // Resolución perezosa: si la URL no está configurada preferimos devolver un
    // error legible en [identifyPlant] en lugar de lanzar al construir la clase.
    // Es genérico OpenAI-compatible: sirve para Gemini, Qwen 3.6 local, etc.
    private val aiService: PlantApiService by lazy { NetworkModule.plantApiService }
    private val plantNetService: PlantNetApiService by lazy { NetworkModule.plantNetApiService }

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
            val validImages = pairs.map { it.first }
            val validOrgans = pairs.map { it.second }

            try {
                // 1. Pl@ntNet con todas las fotos, cada una con su órgano.
                val plantNetMatch = identifyWithPlantNetOrNull(validImages, validOrgans)
                val plantNetScore = plantNetMatch?.confidence ?: 0f

                // 50% o más: validada, cadena completa.
                if (plantNetMatch != null &&
                    plantNetScore >= Constants.PLANTNET_CONFIDENT_CONFIDENCE
                ) {
                    val chain = runInfoChain(plantNetMatch)
                    return@withContext AnalysisResult.Identified(
                        plantNetMatch.toPlantResult(chain.info, chain.sections)
                    )
                }

                // 2. Respaldo con IA de visión (primera foto).
                val visionMatch = identifyWithVisionOrNull(validImages.first())
                if (visionMatch != null) {
                    val chain = runInfoChain(visionMatch)
                    return@withContext AnalysisResult.Identified(
                        visionMatch.toPlantResult(chain.info, chain.sections)
                    )
                }

                // 21% a 49%: con dudas, el agricultor elige entre similares.
                if (plantNetScore >= Constants.PLANTNET_MIN_CONFIDENCE) {
                    val candidates = plantNetMatch?.toCandidates().orEmpty()
                    if (candidates.isNotEmpty()) {
                        return@withContext AnalysisResult.Ambiguous(
                            enrichWithReferenceImages(candidates)
                        )
                    }
                }

                // 20% o menos: solo la guía de cómo tomar mejores fotos.
                AnalysisResult.Unidentified(
                    "La coincidencia es muy baja. Toma mejores fotos e inténtalo de nuevo."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                AnalysisResult.Failed(mapHttpError(e))
            } catch (e: SocketTimeoutException) {
                AnalysisResult.Failed(
                    "La solicitud tardó demasiado (timeout). Revisa tu conexión e inténtalo de nuevo."
                )
            } catch (e: UnknownHostException) {
                AnalysisResult.Failed(
                    "Sin conexión a internet. Verifica tu red e inténtalo de nuevo."
                )
            } catch (e: ConnectException) {
                AnalysisResult.Failed(
                    "No se pudo conectar con el servidor. Verifica tu conexión e inténtalo de nuevo."
                )
            } catch (e: SerializationException) {
                AnalysisResult.Failed(
                    "La respuesta del servidor no es un JSON válido o no tiene el formato esperado."
                )
            } catch (e: IOException) {
                AnalysisResult.Failed(
                    "Sin conexión a internet o error de red. Verifica tu red e inténtalo de nuevo."
                )
            } catch (e: Exception) {
                AnalysisResult.Failed(
                    "Error inesperado al identificar la planta: ${e.message ?: "inténtalo de nuevo"}."
                )
            }
        }

    /**
     * Ejecuta la cadena completa de información para la candidata elegida por
     * el agricultor. [siblings] son las otras candidatas (dan contexto).
     */
    override suspend fun confirmCandidate(
        candidate: SpeciesCandidate,
        siblings: List<SpeciesCandidate>
    ): Result<PlantResult> =
        withContext(Dispatchers.IO) {
            try {
                val species = SpeciesMatch(
                    commonName = candidate.commonNames.firstOrNull().orEmpty(),
                    scientificName = candidate.scientificName,
                    family = candidate.family,
                    confidence = candidate.confidence,
                    plantNetSpecies = null,
                    bestMatch = "",
                    candidates = siblings
                        .filter { it.scientificName != candidate.scientificName }
                        .take(3).map {
                            PlantCandidate(
                                scientificName = it.scientificName,
                                score = it.confidence,
                                commonName = it.commonNames.firstOrNull().orEmpty()
                            )
                        },
                    gbifId = candidate.gbifId,
                    powoId = candidate.powoId,
                    iucnCategory = candidate.iucnCategory
                )
                val chain = runInfoChain(species)
                Result.success(species.toPlantResult(chain.info, chain.sections))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(
                    PlantIdentificationException(
                        "No se pudo generar la información. Revisa tu conexión e inténtalo de nuevo.",
                        e
                    )
                )
            }
        }

    /**
     * Busca una foto de referencia por candidata en Wikimedia Commons (sin
     * clave, en paralelo). Best-effort: si no hay foto, la tarjeta sale igual.
     */
    private suspend fun enrichWithReferenceImages(
        candidates: List<SpeciesCandidate>
    ): List<SpeciesCandidate> = coroutineScope {
        candidates.map { candidate ->
            async {
                candidate.copy(imageUrl = fetchCandidateImageUrl(candidate.scientificName))
            }
        }.awaitAll()
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

    /**
     * Pl@ntNet sin lanzar: devuelve null ante cualquier fallo para intentar el
     * respaldo con visión.
     */
    private suspend fun identifyWithPlantNetOrNull(
        images: List<ByteArray>,
        organs: List<String>
    ): SpeciesMatch? = try {
        identifyWithPlantNet(images, organs)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * Respaldo con IA de visión sin lanzar: devuelve null si no alcanza la
     * certeza exigida, para pasar a la desambiguación con candidatas.
     */
    private suspend fun identifyWithVisionOrNull(imageBytes: ByteArray): SpeciesMatch? = try {
        val match = identifyWithAiVision(imageBytes)
        val hasName = match.scientificName.isNotBlank() &&
            !match.scientificName.equals("unknown", true)
        if (!hasName || match.confidence < Constants.AI_FALLBACK_MIN_CONFIDENCE) null else match
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * Llama a Pl@ntNet y devuelve la mejor coincidencia con su ficha extendida
     * (o null si no hay resultados). Guarda también las candidatas alternativas
     * para enriquecer la ficha que alimenta a la IA.
     */
    private suspend fun identifyWithPlantNet(images: List<ByteArray>, organs: List<String>): SpeciesMatch? {
        // Una parte "images" + una parte "organs" por cada foto, en orden.
        // Cada foto aporta el órgano elegido por el usuario para ella.
        val imageParts = images.mapIndexed { index, bytes ->
            MultipartBody.Part.createFormData(
                "images",
                "plant$index.jpg",
                bytes.toRequestBody("image/jpeg".toMediaType())
            )
        }
        val organParts = images.mapIndexed { index, _ ->
            MultipartBody.Part.createFormData(
                "organs",
                organs.getOrElse(index) { "auto" }.ifBlank { "auto" }
            )
        }

        val response = plantNetService.identify(
            Constants.plantNetIdentifyUrl(), imageParts, organParts
        )
        val ranked = response.results.sortedByDescending { it.score }
        val best = ranked.firstOrNull() ?: return null
        val species = best.species ?: return null
        val scientificName = species.scientificNameWithoutAuthor.ifBlank { species.scientificName }
        if (scientificName.isBlank()) return null

        // Candidatas alternativas (hasta 3, sin repetir la mejor) para la ficha.
        val candidates = ranked.drop(1).mapNotNull { result ->
            val name = result.species?.scientificNameWithoutAuthor
                ?.ifBlank { result.species?.scientificName }.orEmpty()
            if (name.isBlank()) null else PlantCandidate(
                scientificName = name,
                score = result.score.coerceIn(0.0, 1.0).toFloat(),
                commonName = result.species?.commonNames?.firstOrNull().orEmpty()
            )
        }.take(3)

        return SpeciesMatch(
            commonName = species.commonNames.firstOrNull().orEmpty(),
            scientificName = scientificName,
            family = species.family?.scientificNameWithoutAuthor.orEmpty(),
            confidence = best.score.coerceIn(0.0, 1.0).toFloat(),
            plantNetSpecies = species,
            bestMatch = response.bestMatch.orEmpty(),
            candidates = candidates,
            gbifId = best.gbif?.id.orEmpty(),
            powoId = best.powo?.id.orEmpty(),
            iucnCategory = best.iucn?.category.orEmpty()
        )
    }

    /** Respaldo: pide a la IA de visión que identifique la planta en la imagen. */
    private suspend fun identifyWithAiVision(imageBytes: ByteArray): SpeciesMatch {
        val response = aiService.identifyPlant(
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
     * Reintenta una llamada a la IA ante errores transitorios (429 por
     * rate-limit/cuota o 5xx del servidor), con espera creciente y un poco de
     * azar para no reintentar todas las categorías a la vez. Si se agotan los
     * intentos, propaga la última excepción (el llamador la trata best-effort).
     */
    private suspend fun <T> withAiRetry(
        attempts: Int = 3,
        initialDelayMs: Long = 2000L,
        block: suspend () -> T
    ): T {
        var delayMs = initialDelayMs
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val retryable = e.code() == 429 || e.code() in 500..599
                if (!retryable || attempt == attempts - 1) throw e
            }
            delay(delayMs + (0..500).random())
            delayMs *= 2
        }
        error("Reintentos de IA agotados")
    }

    /**
     * Cadena de información:
     *  1. La IA genera un prompt optimizado de investigación (contexto base).
     *  2. La IA ejecuta UN prompt por categoría de cuidado, en paralelo, cada
     *     uno con la ficha extendida + el prompt de investigación.
     *  3. Con el texto de cada categoría, la IA genera su glosario contextual
     *     (palabras difíciles + significado), también en paralelo.
     *  4. Solo en categorías de cuidado, la IA lista materiales, productos y
     *     alternativas viables (p. ej. forzado fuera de estación), en paralelo.
     * Es "best-effort" por categoría: si algo falla, esa parte queda vacía
     * pero lo demás se muestra igual.
     */
    private suspend fun runInfoChain(species: SpeciesMatch): InfoChainResult = coroutineScope {
        val refinedPrompt = generateRefinedPrompt(species)
        val ficha = buildPlantNetFicha(species)
        val texts = Constants.CATEGORY_ORDER.associateWith { category ->
            async { generateCategoryInfo(category, ficha, refinedPrompt) }
        }.mapValues { (_, job) -> job.await() }
        val glossaries = Constants.CATEGORY_ORDER.associateWith { category ->
            async { generateGlossary(category, texts[category].orEmpty(), species) }
        }.mapValues { (_, job) -> job.await() }
        val materials = Constants.MATERIAL_CATEGORIES.associateWith { category ->
            async { generateMaterials(category, texts[category].orEmpty(), species) }
        }.mapValues { (_, job) -> job.await() }
        val info = PlantInfoDto(
            description = texts["description"].orEmpty(),
            light = texts["light"].orEmpty(),
            watering = texts["watering"].orEmpty(),
            soil = texts["soil"].orEmpty(),
            climate = texts["climate"].orEmpty(),
            flowering = texts["flowering"].orEmpty(),
            uses = texts["uses"].orEmpty(),
            care = texts["care"].orEmpty()
        )
        val sections = Constants.CATEGORY_ORDER.map { key ->
            PlantSection(
                key = key,
                title = Constants.CATEGORY_TITLES[key].orEmpty(),
                body = texts[key].orEmpty(),
                terms = glossaries[key].orEmpty(),
                materials = materials[key].orEmpty()
            )
        }
        InfoChainResult(info, sections)
    }

    /** Resultado interno de la cadena: textos por categoría + secciones con glosario. */
    private data class InfoChainResult(
        val info: PlantInfoDto,
        val sections: List<PlantSection>
    )

    /** Paso 1: pide a la IA que refine la ficha en un prompt de investigación. */
    private suspend fun generateRefinedPrompt(species: SpeciesMatch): String {
        return try {
            val message = Constants.REFINE_PROMPT + "\n\n" + buildPlantNetFicha(species)
            val response = withAiRetry {
                aiService.identifyPlant(
                    url = Constants.identifyEndpointUrl(),
                    authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                    request = buildTextRequest(message)
                )
            }
            val content = response.choices.firstOrNull()?.message?.content ?: return ""
            val dto = parseRefinedPromptDto(content) ?: return ""
            dto.prompt.cleanInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Paso 2: ejecuta el prompt de UNA categoría (ver [Constants.CATEGORY_PROMPTS])
     * y devuelve su texto extenso (o "" si falla: best-effort por categoría).
     */
    private suspend fun generateCategoryInfo(
        category: String,
        ficha: String,
        refinedPrompt: String
    ): String {
        val categoryPrompt = Constants.CATEGORY_PROMPTS[category] ?: return ""
        return try {
            val message = buildString {
                append(categoryPrompt)
                append("\n\nFicha extendida de Pl@ntNet:\n").append(ficha)
                if (refinedPrompt.isNotBlank()) {
                    append("\n\nPrompt de investigación:\n").append(refinedPrompt)
                }
            }
            val response = withAiRetry {
                aiService.identifyPlant(
                    url = Constants.identifyEndpointUrl(),
                    authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                    request = buildTextRequest(message)
                )
            }
            val content = response.choices.firstOrNull()?.message?.content ?: return ""
            parseCategoryDto(content).text.cleanInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Paso 3: con el texto ya generado de UNA categoría, pide a la IA su
     * glosario contextual (palabras difíciles + significado sencillo).
     * Best-effort: si falla, la sección se muestra sin palabras resaltadas.
     */
    private suspend fun generateGlossary(
        category: String,
        categoryText: String,
        species: SpeciesMatch
    ): List<WordMeaning> {
        if (categoryText.isBlank()) return emptyList()
        val title = Constants.CATEGORY_TITLES[category].orEmpty().ifBlank { category }
        val plantLabel = species.scientificName.ifBlank { species.commonName }.ifBlank { "planta" }
        return try {
            val message = buildString {
                append(Constants.GLOSSARY_PROMPT)
                append("\n\nPlanta: ").append(plantLabel)
                if (species.commonName.isNotBlank() && species.scientificName.isNotBlank()) {
                    append(" (").append(species.commonName).append(")")
                }
                append("\nCategoría: ").append(title)
                append("\n\nTexto de la categoría:\n").append(categoryText)
            }
            val response = withAiRetry {
                aiService.identifyPlant(
                    url = Constants.identifyEndpointUrl(),
                    authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                    request = buildTextRequest(message)
                )
            }
            val content = response.choices.firstOrNull()?.message?.content ?: return emptyList()
            parseGlossaryDto(content).terms.mapNotNull { term ->
                val word = term.word.cleanInfo()
                val meaning = term.meaning.cleanInfo()
                if (word.length < 3 || meaning.isBlank()) null else WordMeaning(word, meaning)
            }.take(8)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Paso 4: con el texto ya generado de UNA categoría de cuidado, pide a la
     * IA sus materiales, productos y alternativas viables.
     * Best-effort: si falla, la sección se muestra sin materiales.
     */
    private suspend fun generateMaterials(
        category: String,
        categoryText: String,
        species: SpeciesMatch
    ): List<MaterialItem> {
        if (categoryText.isBlank()) return emptyList()
        val title = Constants.CATEGORY_TITLES[category].orEmpty().ifBlank { category }
        val plantLabel = species.scientificName.ifBlank { species.commonName }.ifBlank { "planta" }
        return try {
            val message = buildString {
                append(Constants.MATERIALS_PROMPT)
                append("\n\nPlanta: ").append(plantLabel)
                if (species.commonName.isNotBlank() && species.scientificName.isNotBlank()) {
                    append(" (").append(species.commonName).append(")")
                }
                append("\nCategoría: ").append(title)
                append("\n\nTexto de la categoría:\n").append(categoryText)
            }
            val response = withAiRetry {
                aiService.identifyPlant(
                    url = Constants.identifyEndpointUrl(),
                    authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                    request = buildTextRequest(message)
                )
            }
            val content = response.choices.firstOrNull()?.message?.content ?: return emptyList()
            parseMaterialsDto(content).items.mapNotNull { item ->
                val name = item.name.cleanInfo()
                val detail = item.detail.cleanInfo()
                if (name.isBlank()) null else MaterialItem(name, detail)
            }.take(6)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parsea los materiales {"items": [{"name": "...", "detail": "..."}]}.
     * Tolerante: acepta también la clave "materials" y descarta vacíos.
     */
    private fun parseMaterialsDto(rawContent: String): MaterialsDto {
        val jsonText = extractJsonObject(rawContent)
        if (jsonText.isBlank()) return MaterialsDto()
        return try {
            val root = json.parseToJsonElement(jsonText) as? JsonObject ?: return MaterialsDto()
            val array = (root["items"] ?: root["materials"]) as? JsonArray ?: return MaterialsDto()
            val items = array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                val detail = (obj["detail"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (name.isBlank() || detail.equals("unknown", true)) null
                else MaterialItemDto(name, detail)
            }
            MaterialsDto(items)
        } catch (_: Exception) {
            MaterialsDto()
        }
    }

    /**
     * Parsea el glosario {"terms": [{"word": "...", "meaning": "..."}]}.
     * Tolerante: acepta también la clave "glossary" y descarta entradas vacías.
     */
    private fun parseGlossaryDto(rawContent: String): GlossaryDto {
        val jsonText = extractJsonObject(rawContent)
        if (jsonText.isBlank()) return GlossaryDto()
        return try {
            val root = json.parseToJsonElement(jsonText) as? JsonObject ?: return GlossaryDto()
            val array = (root["terms"] ?: root["glossary"]) as? JsonArray ?: return GlossaryDto()
            val terms = array.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val word = (obj["word"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                val meaning = (obj["meaning"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (word.isBlank() || meaning.isBlank() || meaning.equals("unknown", true)) null
                else GlossaryTermDto(word, meaning)
            }
            GlossaryDto(terms)
        } catch (_: Exception) {
            GlossaryDto()
        }
    }

    /**
     * Construye la ficha extendida de Pl@ntNet: mejor coincidencia con todos sus
     * datos (nombres comunes completos, autoría, género, familia, GBIF/POWO/IUCN)
     * más las especies candidatas alternativas con su score.
     */
    private fun buildPlantNetFicha(species: SpeciesMatch): String {
        val sp = species.plantNetSpecies
        return buildString {
            append("Ficha de la planta (mejor coincidencia):\n")
            if (species.scientificName.isNotBlank()) {
                append("- Nombre científico: ").append(species.scientificName)
                sp?.scientificNameAuthorship?.takeIf { it.isNotBlank() }?.let {
                    append(" ").append(it)
                }
                append("\n")
            }
            sp?.commonNames?.takeIf { it.isNotEmpty() }?.let {
                append("- Nombres comunes (todos): ").append(it.joinToString(", ")).append("\n")
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
            species.gbifId.takeIf { it.isNotBlank() }?.let {
                append("- GBIF ID: ").append(it).append("\n")
            }
            species.powoId.takeIf { it.isNotBlank() }?.let {
                append("- POWO ID: ").append(it).append("\n")
            }
            species.iucnCategory.takeIf { it.isNotBlank() }?.let {
                append("- IUCN: ").append(it).append("\n")
            }
            if (species.bestMatch.isNotBlank()) {
                append("- Mejor coincidencia (bestMatch): ").append(species.bestMatch).append("\n")
            }
            if (species.candidates.isNotEmpty()) {
                append("Especies candidatas alternativas:\n")
                species.candidates.forEachIndexed { index, candidate ->
                    append("  ").append(index + 1).append(". ")
                        .append(candidate.scientificName)
                        .append(" (").append((candidate.score * 100).toInt()).append("%)")
                    if (candidate.commonName.isNotBlank()) {
                        append(" — ").append(candidate.commonName)
                    }
                    append("\n")
                }
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

    /** Parsea la respuesta JSON {"text": "..."} de un prompt por categoría. */
    private fun parseCategoryDto(rawContent: String): CategoryInfoDto {
        val jsonText = extractJsonObject(rawContent)
        if (jsonText.isBlank()) return CategoryInfoDto()
        try {
            val dto = json.decodeFromString<CategoryInfoDto>(jsonText)
            if (dto.text.isNotBlank()) return dto
        } catch (_: SerializationException) {
            // Se intenta el respaldo tolerante de abajo.
        }
        // Respaldo tolerante: la IA a veces devuelve otra clave
        // (p. ej. {"flowering": "..."}); se toma el primer texto no vacío.
        return try {
            val map = json.decodeFromString<Map<String, JsonElement>>(jsonText)
            val first = map.values
                .filterIsInstance<JsonPrimitive>()
                .firstOrNull { it.isString && it.content.isNotBlank() }
                ?.content.orEmpty()
            CategoryInfoDto(first)
        } catch (_: SerializationException) {
            CategoryInfoDto()
        }
    }

    /**
     * Extrae el primer objeto JSON del texto devuelto por la IA.
     * Tolera bloques Markdown ```json ... ``` y texto adicional antes o después,
     * siempre que el JSON sea un objeto plano.
     *
     * Además neutraliza caracteres de control literales (U+0000–U+001F): la IA a
     * veces devuelve escapes \u00XX que, tras decodificar la respuesta externa,
     * quedan como controles crudos dentro del texto; esos bytes son ilegales en
     * un segundo parse JSON y vaciaban categorías aleatorias. Se cambian por
     * espacios (la estructura JSON solo usa caracteres >= 0x20).
     */
    internal fun extractJsonObject(content: String): String {
        val trimmed = content.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        val raw = if (start >= 0 && end > start) {
            trimmed.substring(start, end + 1)
        } else {
            trimmed
        }
        return raw.map { c -> if (c < ' ') ' ' else c }.joinToString("")
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
     * Coincidencia de especie (identificación) con la ficha extendida de Pl@ntNet
     * cuando está disponible; se usa para alimentar la cadena por categorías.
     */
    private data class SpeciesMatch(
        val commonName: String,
        val scientificName: String,
        val family: String,
        val confidence: Float,
        val plantNetSpecies: PlantNetSpecies? = null,
        val bestMatch: String = "",
        val candidates: List<PlantCandidate> = emptyList(),
        val gbifId: String = "",
        val powoId: String = "",
        val iucnCategory: String = ""
    ) {
        fun toPlantResult(info: PlantInfoDto, sections: List<PlantSection>): PlantResult =
            PlantResult(
                commonName = commonName.trim(),
                scientificName = scientificName.trim(),
                family = family.trim().ifBlank { info.family.cleanInfo() },
                confidence = confidence,
                sections = sections
            )

        /**
         * Candidatas para desambiguación: la mejor primero y luego las
         * alternativas, con los datos disponibles de cada una.
         */
        fun toCandidates(): List<SpeciesCandidate> {
            val best = SpeciesCandidate(
                scientificName = scientificName,
                authorship = plantNetSpecies?.scientificNameAuthorship.orEmpty(),
                commonNames = plantNetSpecies?.commonNames.orEmpty(),
                genus = plantNetSpecies?.genus?.scientificNameWithoutAuthor.orEmpty(),
                family = family,
                confidence = confidence,
                gbifId = gbifId,
                powoId = powoId,
                iucnCategory = iucnCategory
            )
            val rest = candidates.map {
                SpeciesCandidate(
                    scientificName = it.scientificName,
                    commonNames = listOf(it.commonName).filter { name -> name.isNotBlank() },
                    confidence = it.score
                )
            }
            return (listOf(best) + rest).filter { it.scientificName.isNotBlank() }
        }
    }

    /** Especie candidata alternativa de Pl@ntNet (para la ficha extendida). */
    private data class PlantCandidate(
        val scientificName: String,
        val score: Float,
        val commonName: String = ""
    )
}

/** Normaliza un texto devuelto por la IA: sin espacios y tratando "unknown" como vacío. */
private fun String.cleanInfo(): String =
    trim().takeUnless { it.equals("unknown", true) }.orEmpty()