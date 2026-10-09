package com.agronomia.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Servicio Retrofit para la API de IA institucional.
 *
 * La URL completa del endpoint se recibe en [identifyPlant] mediante [Url]
 * porque la ruta depende del proveedor:
 *  - OpenAI-compatible: "v1/chat/completions"
 *  - Gemini (OpenAI compatibility): "chat/completions"
 *
 * Se construye con [com.agronomia.util.Constants.identifyEndpointUrl].
 */
interface PlantApiService {

    @POST
    suspend fun identifyPlant(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}
