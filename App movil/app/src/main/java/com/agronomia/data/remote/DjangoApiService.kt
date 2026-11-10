package com.agronomia.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Servicio del backend Django (servicio de usuario).
 *
 * La URL completa se pasa con [Url] porque la base se configura en
 * `local.properties` (BACKEND_BASE_URL), distinta en emulador y móvil físico.
 */
interface DjangoApiService {

    /** Inicia sesión para que Django asocie fotos, identificaciones y chat. */
    @POST("agronomia-api/api/login")
    suspend fun login(@Body credentials: DjangoLoginRequest): DjangoLoginResponse

    /** Sube 1 a 5 fotos como `photos[]` y devuelve el `batch_id`. */
    @Multipart
    @POST
    suspend fun uploadImages(
        @Url url: String,
        @Part photos: List<MultipartBody.Part>
    ): UploadResponse

    /** Ejecuta Pl@ntNet + Gemini con las skills y devuelve la ficha. */
    @Multipart
    @POST
    suspend fun identify(
        @Url url: String,
        @Part("batch_id") batchId: RequestBody,
        @Part("organ") organ: RequestBody
    ): IdentifyResponse

    /** Genera la ficha de la candidata elegida por el agricultor. */
    @Multipart
    @POST
    suspend fun confirm(
        @Url url: String,
        @Part("batch_id") batchId: RequestBody,
        @Part("scientific_name") scientificName: RequestBody
    ): IdentifyResponse

    /** Lista las identificaciones guardadas para la cuenta autenticada. */
    @GET("agronomia-api/api/plants")
    suspend fun identifiedPlants(): IdentifiedPlantsResponse

    /** Recupera el historial persistente de una planta del usuario. */
    @GET("agronomia-api/api/chat/history")
    suspend fun chatHistory(
        @Query("plant_id") plantId: String
    ): DjangoChatHistoryResponse

    /** Fotos de progreso adjuntas a la conversación de una planta. */
    @GET("agronomia-api/api/chat/images")
    suspend fun chatProgressImages(
        @Query("plant_id") plantId: String
    ): ChatProgressImagesResponse

    /** Chat post-detección (Django usa la skill chat-assistant). */
    @Multipart
    @POST
    suspend fun chat(
        @Url url: String,
        @Part("message") message: RequestBody,
        @Part("plant_id") plantId: RequestBody,
        @Part image: MultipartBody.Part? = null
    ): DjangoChatResponse
}
