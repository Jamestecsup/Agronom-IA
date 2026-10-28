package com.agronomia.domain.canvas

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class CanvasItemType(val label: String) {
    ASSIGNMENT("Tarea / Entrega"),
    EVENT("Evento / Clase"),
    QUIZ("Evaluación / Examen")
}

data class CanvasCalendarItem(
    val id: String,
    val title: String,
    val courseName: String,
    val dateIso: String, // "YYYY-MM-DD"
    val timeFormatted: String, // "23:59"
    val fullFormattedDate: String, // "31 oct, 23:59"
    val type: CanvasItemType,
    val points: String?,
    val htmlUrl: String?,
    val description: String?,
    val isPast: Boolean
) {
    companion object {
        private val outputDateFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale("es", "PE"))
        private val outputTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "PE"))
        private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM, hh:mm a", Locale("es", "PE"))

        fun parseFromDates(
            id: String,
            rawTitle: String?,
            contextName: String?,
            dateString: String?,
            itemType: String?,
            points: Double?,
            htmlUrl: String?,
            description: String?
        ): CanvasCalendarItem {
            val title = rawTitle?.takeIf { it.isNotBlank() } ?: "Actividad sin título"
            val course = contextName?.takeIf { it.isNotBlank() } ?: "Curso general"
            val typeEnum = when {
                itemType.equals("assignment", ignoreCase = true) -> CanvasItemType.ASSIGNMENT
                rawTitle?.contains("examen", ignoreCase = true) == true ||
                rawTitle?.contains("evaluac", ignoreCase = true) == true ||
                rawTitle?.contains("quiz", ignoreCase = true) == true -> CanvasItemType.QUIZ
                else -> CanvasItemType.EVENT
            }

            if (dateString.isNullOrBlank()) {
                val today = LocalDate.now()
                return CanvasCalendarItem(
                    id = id,
                    title = title,
                    courseName = course,
                    dateIso = today.toString(),
                    timeFormatted = "Sin hora fija",
                    fullFormattedDate = "Fecha abierta",
                    type = typeEnum,
                    points = points?.let { String.format(Locale.ROOT, "%.0f pts", it) },
                    htmlUrl = htmlUrl,
                    description = description,
                    isPast = false
                )
            }

            return try {
                val instant = Instant.parse(dateString)
                val zone = ZoneId.systemDefault()
                val zonedDateTime = instant.atZone(zone)
                val localDate = zonedDateTime.toLocalDate()
                val now = ZonedDateTime.now(zone)

                val timeStr = zonedDateTime.format(outputTimeFormatter)
                val fullStr = zonedDateTime.format(shortDateFormatter)

                CanvasCalendarItem(
                    id = id,
                    title = title,
                    courseName = course,
                    dateIso = localDate.toString(),
                    timeFormatted = timeStr,
                    fullFormattedDate = fullStr,
                    type = typeEnum,
                    points = points?.let { String.format(Locale.ROOT, "%.0f pts", it) },
                    htmlUrl = htmlUrl,
                    description = description,
                    isPast = zonedDateTime.isBefore(now)
                )
            } catch (e: Exception) {
                val today = LocalDate.now()
                CanvasCalendarItem(
                    id = id,
                    title = title,
                    courseName = course,
                    dateIso = today.toString(),
                    timeFormatted = dateString,
                    fullFormattedDate = dateString,
                    type = typeEnum,
                    points = points?.let { String.format(Locale.ROOT, "%.0f pts", it) },
                    htmlUrl = htmlUrl,
                    description = description,
                    isPast = false
                )
            }
        }
    }
}
