package com.agronomia.util

import com.agronomia.BuildConfig

object Constants {
    val BASE_URL: String = BuildConfig.AI_BASE_URL
    val API_KEY: String = BuildConfig.AI_API_KEY
    val AI_MODEL: String = BuildConfig.AI_MODEL

    /**
     * Ruta del endpoint de chat relativa a [BASE_URL].
     * OpenAI: "v1/chat/completions"  |  Gemini: "chat/completions"
     */
    val IDENTIFY_PATH: String = BuildConfig.AI_IDENTIFY_PATH

    /**
     * Timeouts de red. La subida de una imagen puede tardar más que una petición
     * normal, por eso read/write son más amplios que el de conexión.
     */
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 60L
    const val WRITE_TIMEOUT_SECONDS = 60L

    /** Umbral mínimo de confianza para aceptar una identificación. */
    const val MIN_CONFIDENCE = 0.5f

    /** Cabecera de autorización enviada a la API. */
    const val AUTH_HEADER_PREFIX = "Bearer"

    /** Prefijo MIME de la imagen enviada como data URL en Base64. */
    const val IMAGE_DATA_URL_PREFIX = "data:image/jpeg;base64,"

    /**
     * Prompt que fuerza una respuesta JSON con los campos requeridos.
     * Si la IA no está segura, debe devolver "unknown" en vez de inventar.
     */
    val IDENTIFY_PROMPT: String = """
        Actúa como botánico experto. Analiza la imagen e identifica la planta.

        Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional, sin
        explicaciones y sin bloques de código Markdown, con exactamente estas claves:
        {
          "commonName": "nombre común de la planta",
          "scientificName": "nombre científico de la planta",
          "confidence": 0.0
        }
        Donde "confidence" es un número entre 0 y 1 que indica tu nivel de confianza.

        Si no estás seguro, si la imagen no muestra una planta o si no puedes
        identificarla, devuelve exactamente:
        {"commonName":"unknown","scientificName":"unknown","confidence":0.0}
    """.trimIndent()

    /**
     * Construye la URL completa del endpoint de identificación a partir de
     * [BASE_URL] y [IDENTIFY_PATH]. Evita duplicar o perder barras.
     */
    fun identifyEndpointUrl(): String {
        val base = BASE_URL.trim().let { if (it.endsWith("/")) it else "$it/" }
        val path = IDENTIFY_PATH.trim().trimStart('/')
        return base + path
    }
}
