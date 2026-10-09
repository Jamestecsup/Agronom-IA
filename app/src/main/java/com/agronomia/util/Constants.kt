package com.agronomia.util

import com.agronomia.BuildConfig

object Constants {
    // ---------------- Proveedor de IA de texto/visión (compatible con OpenAI) ----------------
    val BASE_URL: String = BuildConfig.AI_BASE_URL
    val API_KEY: String = BuildConfig.AI_API_KEY
    val AI_MODEL: String = BuildConfig.AI_MODEL

    /**
     * Ruta del endpoint de chat relativa a [BASE_URL].
     * OpenAI: "v1/chat/completions"  |  Gemini: "chat/completions"
     */
    val IDENTIFY_PATH: String = BuildConfig.AI_IDENTIFY_PATH

    // ---------------- Pl@ntNet (identificación de especie por imagen) ----------------
    val PLANTNET_BASE_URL: String = BuildConfig.PLANTNET_BASE_URL
    val PLANTNET_API_KEY: String = BuildConfig.PLANTNET_API_KEY
    val PLANTNET_PROJECT: String = BuildConfig.PLANTNET_PROJECT
    val PLANTNET_LANG: String = BuildConfig.PLANTNET_LANG

    /** Número máximo de especies candidatas a pedir a Pl@ntNet. */
    const val PLANTNET_MAX_RESULTS = 5

    /**
     * Timeouts de red. Subir la imagen y esperar a la IA puede tardar, por eso
     * read/write son más amplios que el de conexión.
     */
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 90L
    const val WRITE_TIMEOUT_SECONDS = 90L

    /**
     * Umbral mínimo de confianza para aceptar la identificación de Pl@ntNet.
     * Si la mejor coincidencia queda por debajo, se usa Gemini (visión) como respaldo.
     */
    const val PLANTNET_MIN_CONFIDENCE = 0.2f

    /**
     * En el respaldo con Gemini (visión) exigimos certeza total (100 %).
     * Si no la alcanza, se pide al usuario que envíe más imágenes de la planta
     * (flor, hoja, tallo) para una mejor verificación.
     * Baja este valor (p. ej. 0.9f) si quieres ser menos estricto.
     */
    const val GEMINI_FALLBACK_MIN_CONFIDENCE = 1.0f

    /** Cabecera de autorización enviada a la API de IA (Gemini/OpenAI). */
    const val AUTH_HEADER_PREFIX = "Bearer"

    /** Prefijo MIME de la imagen enviada como data URL en Base64 (respaldo con visión). */
    const val IMAGE_DATA_URL_PREFIX = "data:image/jpeg;base64,"

    /**
     * Prompt de identificación (respaldo con Gemini visión).
     * Fuerza una respuesta JSON con los campos requeridos.
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
     * Prompt de información (Gemini texto). Se completa con el nombre de la planta.
     * Fuerza un JSON estructurado con la información a mostrar.
     */
    val INFO_PROMPT: String = """
        Actúa como botánico experto. Te doy el nombre de una planta ya identificada
        y debes dar información breve, clara y en español sobre ella.

        Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
        bloques de código Markdown, con exactamente estas claves:
        {
          "family": "familia botánica",
          "description": "descripción breve de la planta (2 o 3 frases)",
          "uses": "usos principales",
          "care": "cuidados básicos",
          "confidence": 0.0
        }
        "confidence" es un número entre 0 y 1 que indica tu certeza sobre la información.
        Si no conoces la planta, usa "unknown" en los textos y 0 en confidence.
    """.trimIndent()

    /**
     * Construye la URL completa del endpoint de IA (texto/visión) a partir de
     * [BASE_URL] y [IDENTIFY_PATH]. Evita duplicar o perder barras.
     */
    fun identifyEndpointUrl(): String {
        val base = BASE_URL.trim().let { if (it.endsWith("/")) it else "$it/" }
        val path = IDENTIFY_PATH.trim().trimStart('/')
        return base + path
    }

    /**
     * Construye la URL completa de identificación de Pl@ntNet:
     * `{PLANTNET_BASE_URL}v2/identify/{project}?api-key=...&lang=...&nb-results=...`
     *
     * Nota: Pl@ntNet recibe la api-key como parámetro de consulta (no en cabecera),
     * por eso el cliente OkHttp de Pl@ntNet no tiene logging (ver NetworkModule).
     */
    fun plantNetIdentifyUrl(): String {
        val base = PLANTNET_BASE_URL.trim().let { if (it.endsWith("/")) it else "$it/" }
        val project = PLANTNET_PROJECT.trim().ifBlank { "all" }
        val lang = PLANTNET_LANG.trim().ifBlank { "es" }
        return buildString {
            append(base)
            append("v2/identify/")
            append(project)
            append("?api-key=")
            append(PLANTNET_API_KEY.trim())
            append("&lang=")
            append(lang)
            append("&nb-results=")
            append(PLANTNET_MAX_RESULTS)
        }
    }
}
