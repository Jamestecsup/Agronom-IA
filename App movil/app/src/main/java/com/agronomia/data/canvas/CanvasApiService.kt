package com.agronomia.data.canvas

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface CanvasApiService {

    @GET("api/v1/users/self/profile")
    suspend fun getProfile(
        @Header("Authorization") authorization: String
    ): CanvasProfileDto

    @GET("api/v1/calendar_events")
    suspend fun getCalendarEvents(
        @Header("Authorization") authorization: String,
        @Query("type") type: String? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("all_events") allEvents: Boolean = true,
        @Query("per_page") perPage: Int = 100
    ): List<CanvasCalendarEventDto>

    @GET("api/v1/users/self/upcoming_events")
    suspend fun getUpcomingEvents(
        @Header("Authorization") authorization: String
    ): List<CanvasCalendarEventDto>

    @GET("api/v1/planner/items")
    suspend fun getPlannerItems(
        @Header("Authorization") authorization: String,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
        @Query("per_page") perPage: Int = 100
    ): List<CanvasPlannerItemDto>
}
