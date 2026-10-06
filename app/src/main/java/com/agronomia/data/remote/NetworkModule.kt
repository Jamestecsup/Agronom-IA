package com.agronomia.data.remote

import com.agronomia.util.Constants
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Fábrica central de Retrofit/OkHttp.
 *
 * La URL base y la API key se leen desde BuildConfig a través de [Constants]
 * (Constants.BASE_URL <- BuildConfig.AI_BASE_URL, Constants.API_KEY <- BuildConfig.AI_API_KEY).
 */
object NetworkModule {

    /** Json tolerante: ignora campos desconocidos y no serializa nulos. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Constants.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    fun createPlantApiService(baseUrl: String = Constants.BASE_URL): PlantApiService {
        require(baseUrl.isNotBlank()) {
            "AI_BASE_URL no está configurada en local.properties."
        }
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PlantApiService::class.java)
    }

    val plantApiService: PlantApiService by lazy { createPlantApiService() }
}
