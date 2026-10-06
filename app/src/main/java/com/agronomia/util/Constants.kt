package com.agronomia.util

import com.agronomia.BuildConfig

object Constants {
    val BASE_URL: String = BuildConfig.AI_BASE_URL
    val API_KEY: String = BuildConfig.AI_API_KEY
    val AI_MODEL: String = BuildConfig.AI_MODEL

    const val NETWORK_TIMEOUT_SECONDS = 30L

    /** Cabecera de autorización enviada a la API. */
    const val AUTH_HEADER_PREFIX = "Bearer"

    /** Prefijo MIME de la imagen enviada como data URL en Base64. */
    const val IMAGE_DATA_URL_PREFIX = "data:image/jpeg;base64,"

    /** Ruta del endpoint de identificación relativa a la base URL. */
    const val IDENTIFY_PATH = "v1/chat/completions"

    /** Prompt que fuerza una respuesta JSON con los campos requeridos. */
    val IDENTIFY_PROMPT: String = """
        Actúa como botánico experto. Analiza la imagen y identifica la planta.
        Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional,
        sin explicaciones y sin bloques de código Markdown, con exactamente
        estas claves:
        {
          "commonName": "nombre común de la planta",
          "scientificName": "nombre científico de la planta",
          "confidence": 0.0
        }
        Donde "confidence" es un número entre 0 y 1 que indica tu nivel de
        confianza en la identificación.
    """.trimIndent()
}
