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
data class DjangoLoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class DjangoLoginResponse(
    val success: Boolean = false,
    val username: String = "",
    val role: String = "user",
    val token: String = "",
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

@Serializable
data class IdentifiedPlantDto(
    val id: String = "",
    @SerialName("common_name") val commonName: String = "",
    @SerialName("scientific_name") val scientificName: String = "",
    val family: String = "",
    val confidence: Double = 0.0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("image_url") val imageUrl: String = ""
)

@Serializable
data class IdentifiedPlantsResponse(
    val success: Boolean = false,
    val plants: List<IdentifiedPlantDto> = emptyList(),
    val error: String? = null
)

// ---------- Chat ----------

@Serializable
data class DjangoChatMessageDto(
    val id: String = "",
    val role: String = "",
    val content: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("image_url") val imageUrl: String? = null
)

@Serializable
data class ChatProgressImageDto(
    val id: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    val caption: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class ChatProgressImagesResponse(
    val success: Boolean = false,
    val images: List<ChatProgressImageDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class DjangoChatHistoryResponse(
    val success: Boolean = false,
    val messages: List<DjangoChatMessageDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class DjangoChatResponse(
    val success: Boolean = false,
    val reply: String = "",
    val error: String? = null
)

@Serializable
data class WeeklyClassDto(
    @SerialName("course_code") val courseCode: String = "",
    @SerialName("course_name") val courseName: String = "",
    @SerialName("day_of_week") val dayOfWeek: Int = 1,
    @SerialName("start_time") val startTime: String = "08:00",
    @SerialName("end_time") val endTime: String = "09:00",
    val section: String = ""
)

@Serializable
data class CarePlanTaskDto(
    val id: String = "",
    @SerialName("plant_id") val plantId: String = "",
    @SerialName("plant_name") val plantName: String = "",
    val title: String = "",
    val instructions: String = "",
    @SerialName("day_of_week") val dayOfWeek: Int = 1,
    @SerialName("start_time") val startTime: String = "08:00",
    @SerialName("duration_minutes") val durationMinutes: Int = 20
)

@Serializable
data class PendingScheduleDto(
    val id: String = "",
    val classes: List<WeeklyClassDto> = emptyList()
)

@Serializable
data class WeeklyScheduleResponse(
    val success: Boolean = false,
    val classes: List<WeeklyClassDto> = emptyList(),
    @SerialName("care_tasks") val careTasks: List<CarePlanTaskDto> = emptyList(),
    @SerialName("pending_import") val pendingImport: PendingScheduleDto? = null,
    val error: String? = null
)

@Serializable
data class ScheduleImportResponse(
    val success: Boolean = false,
    @SerialName("import_id") val importId: String = "",
    val classes: List<WeeklyClassDto> = emptyList(),
    @SerialName("pdf_removed") val pdfRemoved: Boolean = false,
    val error: String? = null
)

@Serializable
data class ScheduleClassesRequest(val classes: List<WeeklyClassDto>)

@Serializable
data class CarePlanRequest(val tasks: List<CarePlanTaskDto>)

@Serializable
data class CarePlanResponse(
    val success: Boolean = false,
    val tasks: List<CarePlanTaskDto> = emptyList(),
    @SerialName("care_tasks") val careTasks: List<CarePlanTaskDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class CareTaskTimeRequest(
    @SerialName("day_of_week") val dayOfWeek: Int,
    @SerialName("start_time") val startTime: String
)

@Serializable
data class CarePlanTaskResponse(
    val success: Boolean = false,
    @SerialName("care_task") val careTask: CarePlanTaskDto? = null,
    val error: String? = null
)

@Serializable
data class SimpleActionResponse(val success: Boolean = false, val error: String? = null)
