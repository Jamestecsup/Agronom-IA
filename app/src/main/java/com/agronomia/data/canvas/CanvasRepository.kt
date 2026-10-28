package com.agronomia.data.canvas

import android.content.Context
import android.util.Log
import com.agronomia.data.remote.NetworkModule
import com.agronomia.domain.canvas.CanvasCalendarItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate

class CanvasRepository(private val context: Context) {

    private val tag = "CanvasRepository"

    suspend fun testConnection(domain: String, token: String): Result<CanvasProfileDto> =
        withContext(Dispatchers.IO) {
            try {
                val cleanDomain = domain.trim().trimEnd('/')
                val cleanToken = token.trim()
                if (cleanToken.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("El token de Canvas no puede estar vacío."))
                }

                val service = NetworkModule.createCanvasApiService(cleanDomain)
                val authHeader = "Bearer $cleanToken"
                val profile = service.getProfile(authHeader)

                CanvasPreferences.saveSession(
                    context = context,
                    domain = cleanDomain,
                    token = cleanToken,
                    userName = profile.name ?: profile.shortName ?: "Estudiante",
                    userEmail = profile.primaryEmail ?: ""
                )

                Result.success(profile)
            } catch (e: Exception) {
                Log.e(tag, "Error validando token de Canvas", e)
                Result.failure(e)
            }
        }

    suspend fun getCalendarEvents(): Result<List<CanvasCalendarItem>> = withContext(Dispatchers.IO) {
        try {
            val domain = CanvasPreferences.getDomain(context)
            val token = CanvasPreferences.getToken(context)

            if (token.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No hay un token de Canvas configurado."))
            }

            val service = NetworkModule.createCanvasApiService(domain)
            val authHeader = "Bearer $token"

            val today = LocalDate.now()
            val startDate = today.minusDays(30).toString()
            val endDate = today.plusDays(60).toString()

            coroutineScope {
                // 1. Entregas de tareas por calendario
                val assignmentsDeferred = async {
                    try {
                        service.getCalendarEvents(
                            authorization = authHeader,
                            type = "assignment",
                            startDate = startDate,
                            endDate = endDate,
                            allEvents = true,
                            perPage = 100
                        )
                    } catch (e: Exception) {
                        Log.w(tag, "Error al obtener assignments", e)
                        emptyList()
                    }
                }

                // 2. Eventos de calendario generales (clases, recordatorios)
                val eventsDeferred = async {
                    try {
                        service.getCalendarEvents(
                            authorization = authHeader,
                            type = "event",
                            startDate = startDate,
                            endDate = endDate,
                            allEvents = true,
                            perPage = 100
                        )
                    } catch (e: Exception) {
                        Log.w(tag, "Error al obtener events", e)
                        emptyList()
                    }
                }

                // 3. Próximos eventos (Upcoming)
                val upcomingDeferred = async {
                    try {
                        service.getUpcomingEvents(authorization = authHeader)
                    } catch (e: Exception) {
                        Log.w(tag, "Error al obtener upcoming events", e)
                        emptyList()
                    }
                }

                // 4. Planner items (Planificador de Canvas)
                val plannerDeferred = async {
                    try {
                        service.getPlannerItems(
                            authorization = authHeader,
                            startDate = startDate,
                            endDate = endDate,
                            perPage = 100
                        )
                    } catch (e: Exception) {
                        Log.w(tag, "Error al obtener planner items", e)
                        emptyList()
                    }
                }

                val assignments = assignmentsDeferred.await()
                val events = eventsDeferred.await()
                val upcoming = upcomingDeferred.await()
                val planner = plannerDeferred.await()

                Log.d(tag, "Canvas data fetched -> assignments: ${assignments.size}, events: ${events.size}, upcoming: ${upcoming.size}, planner: ${planner.size}")

                val calendarItems = (assignments + events + upcoming)
                    .filter { it.id != null }
                    .map { dto ->
                        val date = dto.assignment?.dueAt ?: dto.endAt ?: dto.startAt
                        val title = dto.assignment?.name ?: dto.title
                        val desc = dto.assignment?.description ?: dto.description
                        val url = dto.assignment?.htmlUrl ?: dto.htmlUrl
                        val points = dto.assignment?.pointsPossible
                        val type = if (dto.assignment != null) "assignment" else (dto.type ?: "event")

                        CanvasCalendarItem.parseFromDates(
                            id = dto.id.toString(),
                            rawTitle = title,
                            contextName = dto.contextName,
                            dateString = date,
                            itemType = type,
                            points = points,
                            htmlUrl = url,
                            description = desc
                        )
                    }

                val plannerItems = planner.mapNotNull { p ->
                    val plannable = p.plannable
                    val date = plannable?.dueAt ?: p.plannableDate
                    val title = plannable?.title
                    if (date.isNullOrBlank() && title.isNullOrBlank()) return@mapNotNull null

                    CanvasCalendarItem.parseFromDates(
                        id = "planner_${p.plannableId ?: plannable?.id ?: p.courseId ?: title}",
                        rawTitle = title,
                        contextName = p.contextName,
                        dateString = date,
                        itemType = p.plannableType ?: "assignment",
                        points = plannable?.pointsPossible,
                        htmlUrl = p.htmlUrl,
                        description = null
                    )
                }

                // Unificar y deduplicar por título y fecha
                val combined = (calendarItems + plannerItems)
                    .distinctBy { "${it.title.trim().lowercase()}_${it.dateIso}" }
                    .sortedWith(compareBy({ it.dateIso }, { it.timeFormatted }))

                Log.i(tag, "Total actividades combinadas y deduplicadas: ${combined.size}")
                Result.success(combined)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error general obteniendo calendario de Canvas", e)
            Result.failure(e)
        }
    }

    fun disconnect() {
        CanvasPreferences.clearSession(context)
    }
}
