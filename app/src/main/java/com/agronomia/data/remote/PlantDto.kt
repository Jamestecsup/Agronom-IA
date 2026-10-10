package com.agronomia.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs para una API de IA tipo chat/visión (OpenAI-compatible):
 *   POST {AI_BASE_URL}{AI_IDENTIFY_PATH}
 *
 * Se usan con la IA (Gemini, Qwen 3.6 local, OpenAI...): identificación de
 * respaldo por visión e información textual por categorías.
 * Pl@ntNet tiene sus propios DTOs en PlantNetDto.kt.
 */

// ---------- Request ----------

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.0,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null
)

@Serializable
data class ResponseFormat(
    val type: String = "json_object"
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: List<ContentPart>
)

@Serializable
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url")
    val imageUrl: ImageUrl? = null
)

@Serializable
data class ImageUrl(
    val url: String
)

// ---------- Response ----------

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val error: ApiError? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChatResponseMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ChatResponseMessage(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ApiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

// ---------- Contenido JSON de la planta ----------

@Serializable
data class PlantResultDto(
    @SerialName("commonName")
    val commonName: String = "",
    @SerialName("scientificName")
    val scientificName: String = "",
    @SerialName("confidence")
    val confidence: Double = 0.0
)

// ---------- Información textual de la planta (IA, por categorías) ----------

@Serializable
data class PlantInfoDto(
    val family: String = "",
    val description: String = "",
    val light: String = "",
    val watering: String = "",
    val soil: String = "",
    val climate: String = "",
    val flowering: String = "",
    val uses: String = "",
    val care: String = "",
    val confidence: Double = 0.0
)

/**
 * Respuesta de UN prompt por categoría: solo el texto extenso de esa categoría.
 * Se usa en las 8 llamadas en paralelo del paso 2 de la cadena.
 */
@Serializable
data class CategoryInfoDto(
    val text: String = ""
)

/**
 * Glosario contextual de una categoría (paso 3 de la cadena): palabras
 * difíciles del texto con su significado explicado EN EL CONTEXTO de la
 * categoría, para usuarios sin conocimiento previo.
 * Forma esperada: {"terms": [{"word": "...", "meaning": "..."}]}.
 */
@Serializable
data class GlossaryDto(
    val terms: List<GlossaryTermDto> = emptyList()
)

@Serializable
data class GlossaryTermDto(
    val word: String = "",
    val meaning: String = ""
)

/**
 * Materiales y alternativas de una categoría de cuidado (paso 4 de la cadena).
 * Forma esperada: {"items": [{"name": "...", "detail": "..."}]}.
 */
@Serializable
data class MaterialsDto(
    val items: List<MaterialItemDto> = emptyList()
)

@Serializable
data class MaterialItemDto(
    val name: String = "",
    val detail: String = ""
)

/** Prompt de investigación que Gemini genera a partir de la ficha de Pl@ntNet. */
@Serializable
data class RefinedPromptDto(
    val prompt: String = ""
)
