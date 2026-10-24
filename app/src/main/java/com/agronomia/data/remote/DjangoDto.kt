package com.agronomia.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs del backend Django (servicio de usuario).
 *
 * El móvil solo manda fotos/mensajes: Django hace Pl@ntNet + Gemini con las
 * skills y devuelve la ficha ya armada.
 */

// ---------- Subida de imágenes ----------

@Serializable
data class UploadResponse(
    val success: Boolean = false,
    @SerialName("batch_id") val batchId: String = "",
    val images: List<UploadedImageDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class UploadedImageDto(
    val id: String = "",
    val path: String = "",
    @SerialName("mime_type") val mimeType: String = "",
    val size: Long = 0
)

// ---------- Ficha ----------

@Serializable
data class DjangoTermDto(
    val word: String = "",
    val meaning: String = ""
)

@Serializable
data class DjangoMaterialDto(
    val name: String = "",
    val detail: String = ""
)

@Serializable
data class DjangoSectionDto(
    val key: String = "",
    val title: String = "",
    val body: String = "",
    val terms: List<DjangoTermDto> = emptyList(),
    val materials: List<DjangoMaterialDto> = emptyList()
)

@Serializable
data class DjangoPlantDto(
    @SerialName("common_name") val commonName: String = "",
    @SerialName("scientific_name") val scientificName: String = "",
    val authorship: String = "",
    val family: String = "",
    val confidence: Double = 0.0,
    val sections: List<DjangoSectionDto> = emptyList()
)

@Serializable
data class DjangoCandidateDto(
    @SerialName("scientific_name") val scientificName: String = "",
    @SerialName("common_names") val commonNames: List<String> = emptyList(),
    val family: String = "",
    val confidence: Double = 0.0,
    val hint: String = "",
    val terms: List<DjangoTermDto> = emptyList()
)

@Serializable
data class IdentifyResponse(
    val success: Boolean = false,
    val status: String = "",
    val plant: DjangoPlantDto? = null,
    val candidates: List<DjangoCandidateDto> = emptyList(),
    @SerialName("plant_name") val plantName: String = "",
    val message: String? = null,
    val error: String? = null
)

// ---------- Chat ----------

@Serializable
data class DjangoChatResponse(
    val success: Boolean = false,
    val reply: String = "",
    val error: String? = null
)
