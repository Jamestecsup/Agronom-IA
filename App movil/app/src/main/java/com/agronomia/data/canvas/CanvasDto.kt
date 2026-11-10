package com.agronomia.data.canvas

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

object StringOrNumberSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringOrNumber", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) {
            encoder.encodeString(value)
        } else {
            encoder.encodeNull()
        }
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
        return if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            if (element is JsonNull) {
                null
            } else if (element is JsonPrimitive) {
                element.content
            } else {
                element.toString()
            }
        } else {
            try {
                decoder.decodeString()
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Serializable
data class CanvasProfileDto(
    @Serializable(with = StringOrNumberSerializer::class) val id: String? = null,
    val name: String? = null,
    @SerialName("short_name") val shortName: String? = null,
    @SerialName("sortable_name") val sortableName: String? = null,
    @SerialName("primary_email") val primaryEmail: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class CanvasAssignmentDto(
    @Serializable(with = StringOrNumberSerializer::class) val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("points_possible") val pointsPossible: Double? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @Serializable(with = StringOrNumberSerializer::class) @SerialName("course_id") val courseId: String? = null
)

@Serializable
data class CanvasCalendarEventDto(
    @Serializable(with = StringOrNumberSerializer::class) val id: String? = null,
    val title: String? = null,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    val type: String? = null, // "event" o "assignment"
    val description: String? = null,
    @SerialName("context_name") val contextName: String? = null,
    @SerialName("context_code") val contextCode: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("all_day") val allDay: Boolean? = null,
    val assignment: CanvasAssignmentDto? = null
)

@Serializable
data class CanvasPlannableDto(
    @Serializable(with = StringOrNumberSerializer::class) val id: String? = null,
    val title: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("points_possible") val pointsPossible: Double? = null
)

@Serializable
data class CanvasPlannerItemDto(
    @Serializable(with = StringOrNumberSerializer::class) @SerialName("course_id") val courseId: String? = null,
    @Serializable(with = StringOrNumberSerializer::class) @SerialName("plannable_id") val plannableId: String? = null,
    @SerialName("plannable_type") val plannableType: String? = null,
    @SerialName("plannable_date") val plannableDate: String? = null,
    @SerialName("context_name") val contextName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val plannable: CanvasPlannableDto? = null
)
