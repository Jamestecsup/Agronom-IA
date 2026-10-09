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
        // BASIC solo registra método, URL y código de estado.
        // NO usar Level.BODY/HEADERS: imprimiría la cabecera Authorization
        // (API key) y el Base64 completo de la imagen.
        level = HttpLoggingInterceptor.Level.BASIC
        // Defensa extra por si en el futuro se sube el nivel de logging.
        redactHeader("Authorization")
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Lectura/escritura amplias: subir la imagen y esperar a la IA puede tardar.
            .readTimeout(Constants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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

    /**
     * Cliente OkHttp para Pl@ntNet SIN interceptor de logging: su api-key viaja
     * como query en la URL y no debe quedar registrada en logcat.
     */
    private val plantNetOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    fun createPlantNetApiService(
        baseUrl: String = Constants.PLANTNET_BASE_URL
    ): PlantNetApiService {
        require(baseUrl.isNotBlank()) {
            "PLANTNET_BASE_URL no está configurada en local.properties."
        }
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(plantNetOkHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PlantNetApiService::class.java)
    }

    val plantNetApiService: PlantNetApiService by lazy { createPlantNetApiService() }
}
