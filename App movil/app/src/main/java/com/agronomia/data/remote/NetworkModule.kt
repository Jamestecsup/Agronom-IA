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
 * Solo hay un cliente: el backend Django ([Constants.BACKEND_BASE_URL]). Las
 * claves de Pl@ntNet y de la IA viven en el `.env` del backend, nunca aquí.
 */
object NetworkModule {

    @Volatile
    private var userToken: String? = null

    fun setUserToken(token: String?) {
        userToken = token?.takeIf { it.isNotBlank() }
    }

    fun currentUserToken(): String? = userToken

    /** Json tolerante: ignora campos desconocidos y no serializa nulos. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // BASIC solo registra método, URL y código de estado.
        // NO usar Level.BODY: imprimiría cuerpos con datos del usuario.
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Lectura/escritura amplias: subir fotos y esperar el análisis tarda.
            .readTimeout(Constants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    userToken?.let { header("Authorization", "Bearer $it") }
                }.build()
                chain.proceed(request)
            }
            .build()
    }

    // ---------------- Backend Django (servicio de usuario) ----------------

    /**
     * Cliente del backend Django. El token de usuario identifica al dueño de
     * cada registro; las claves de IA siguen solo en el `.env` del servidor.
     */
    fun createDjangoApiService(
        baseUrl: String = Constants.BACKEND_BASE_URL
    ): DjangoApiService {
        require(baseUrl.isNotBlank()) {
            "BACKEND_BASE_URL no está configurada en local.properties."
        }
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DjangoApiService::class.java)
    }



    val djangoApiService: DjangoApiService by lazy { createDjangoApiService() }

    /** Canvas usa su propio token; no se debe enviar el token de la cuenta Django. */
    fun createCanvasApiService(baseUrl: String): com.agronomia.data.canvas.CanvasApiService {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(com.agronomia.data.canvas.CanvasApiService::class.java)
    }
}