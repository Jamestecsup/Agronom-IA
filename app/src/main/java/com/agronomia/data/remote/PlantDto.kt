package com.agronomia.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs para una API de IA tipo chat/visión (OpenAI-compatible):
 *   POST {AI_BASE_URL}{AI_IDENTIFY_PATH}
 *
 * Se usan con Gemini/OpenAI (identificación de respaldo por visión e información
 * textual). Pl@ntNet tiene sus propios DTOs en PlantNetDto.kt.
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

// ---------- Información textual de la planta (Gemini) ----------

@Serializable
data class PlantInfoDto(
    val family: String = "",
    val description: String = "",
    val uses: String = "",
    val care: String = "",
    val confidence: Double = 0.0
)
