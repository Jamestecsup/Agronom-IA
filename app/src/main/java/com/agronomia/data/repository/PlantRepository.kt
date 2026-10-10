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
import com.agronomia.domain.model.WordMeaning
import com.agronomia.util.Constants
import com.agronomia.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
     *  1. Pl@ntNet identifica la especie y devuelve su ficha extendida
     *     (mejor coincidencia + candidatas alternativas, GBIF/POWO/IUCN).
     *  2. La IA genera un prompt optimizado de investigación a partir de esa ficha.
     *  3. La IA ejecuta UN prompt por categoría de cuidado (descripción, luz,
     *     riego, suelo, clima, floración, usos, cuidados), en paralelo, y cada
     *     uno devuelve el texto extenso de su categoría.
     *
     * Si Pl@ntNet no identifica con confianza suficiente, se usa la IA de visión
     * como respaldo (la cadena se aplica igual con lo que se tenga).
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
    // Es genérico OpenAI-compatible: sirve para Gemini, Qwen 3.6 local, etc.
    private val aiService: PlantApiService by lazy { NetworkModule.plantApiService }
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
                val chain = runInfoChain(species)
                Result.success(species.toPlantResult(chain.info, chain.sections))
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
     * cae al respaldo con IA de visión (que exige la certeza configurada).
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

        // Respaldo: IA de visión (misma imagen ya preparada en JPEG).
        val visionMatch = identifyWithAiVision(imageBytes)
        val hasName = visionMatch.scientificName.isNotBlank() &&
            !visionMatch.scientificName.equals("unknown", true)
        if (!hasName || visionMatch.confidence < Constants.AI_FALLBACK_MIN_CONFIDENCE) {
            throw PlantIdentificationException(
                "No se pudo verificar la planta con certeza. Envía más imágenes " +
                    "(flor, hoja o tallo) para una mejor identificación."
            )
        }
        return visionMatch
    }

    /**
     * Llama a Pl@ntNet y devuelve la mejor coincidencia con su ficha extendida
     * (o null si no hay resultados). Guarda también las candidatas alternativas
     * para enriquecer la ficha que alimenta a la IA.
     */
    private suspend fun identifyWithPlantNet(imageBytes: ByteArray): SpeciesMatch? {
        val imagePart = MultipartBody.Part.createFormData(
            "images",
            "plant.jpg",
            imageBytes.toRequestBody("image/jpeg".toMediaType())
        )
        val organs = "auto".toRequestBody("text/plain".toMediaType())

        val response = plantNetService.identify(Constants.plantNetIdentifyUrl(), imagePart, organs)
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
            val response = aiService.identifyPlant(
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
            val response = aiService.identifyPlant(
                url = Constants.identifyEndpointUrl(),
                authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                request = buildTextRequest(message)
            )
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
            val response = aiService.identifyPlant(
                url = Constants.identifyEndpointUrl(),
                authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                request = buildTextRequest(message)
            )
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
            val response = aiService.identifyPlant(
                url = Constants.identifyEndpointUrl(),
                authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                request = buildTextRequest(message)
            )
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