package com.agronomia.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Servicio Retrofit para la API de IA institucional.
 *
 * La URL base proviene de [com.agronomia.util.Constants.BASE_URL] (que a su vez
 * lee BuildConfig.AI_BASE_URL). El endpoint es relativo a esa base y debe
 * terminar en "/".
 *
 * Si tu proveedor usa otro path, cambia [IDENTIFY_PATH] (sin "/" inicial).
 */
interface PlantApiService {

    @POST(IDENTIFY_PATH)
    suspend fun identifyPlant(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse

    companion object {
        const val IDENTIFY_PATH = "v1/chat/completions"
    }
}
