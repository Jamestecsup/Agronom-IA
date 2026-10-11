package com.agronomia.util

import com.agronomia.BuildConfig
import com.agronomia.domain.model.WordMeaning

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
    const val PLANTNET_MAX_RESULTS = 10

    /**
     * Órganos que Pl@ntNet acepta (se envía uno por foto). El usuario elige uno
     * por cada foto en la pantalla de captura para subir la precisión.
     */
    val ORGAN_OPTIONS: List<Pair<String, String>> = listOf(
        "auto" to "Auto",
        "flower" to "Flor",
        "leaf" to "Hoja",
        "fruit" to "Fruto",
        "bark" to "Corteza"
    )

    /**
     * Qué significa cada órgano, en contexto de foto: se muestra bajo los
     * chips de cada foto seleccionada.
     */
    val ORGAN_DESCRIPTIONS: Map<String, String> = mapOf(
        "auto" to "La app detecta sola qué parte de la planta es.",
        "flower" to "Foto de la flor, de cerca y con buena luz.",
        "leaf" to "Foto de una hoja sola, sin contraluz.",
        "fruit" to "Foto del fruto o de las semillas.",
        "bark" to "Foto del tallo o tronco, de cerca."
    )

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
     * Regla acordada: 20% o menos = error o guía; 21% a 49% = dudas (el
     * agricultor elige entre similares); 50% o más = validada.
     */
    const val PLANTNET_CONFIDENT_CONFIDENCE = 0.5f

    /**
     * En el respaldo con IA de visión exigimos certeza total (100 %).
     * Si no la alcanza, se pide al usuario que envíe más imágenes de la planta
     * (flor, hoja, tallo) para una mejor verificación.
     * Baja este valor (p. ej. 0.9f) si quieres ser menos estricto.
     */
    const val AI_FALLBACK_MIN_CONFIDENCE = 1.0f

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
     * Prompt paso 1 de la cadena (IA de texto): recibe la ficha extendida de
     * Pl@ntNet y genera un prompt optimizado para investigar ESA planta concreta.
     * El prompt generado es interno (no se muestra al usuario): alimenta los 8
     * prompts por categoría del paso 2.
     */
    val REFINE_PROMPT: String = """
        Actúa como experto en botánica y jardinería. Te doy la ficha de
        identificación de una planta (datos de Pl@ntNet). Genera un prompt
        optimizado, en español, para investigar ESA planta concreta: descripción,
        luz, riego, suelo, clima, floración, usos y cuidados; incluye cualquier
        dato importante para alguien que la tiene o quiere cultivarla.

        Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
        bloques de código Markdown, con exactamente esta clave:
        {"prompt": "el prompt de investigación generado"}
        No inventes datos de la planta: apóyate solo en la ficha dada y en el nombre.
    """.trimIndent()

    /**
     * Paso 2 de la cadena (IA de texto): un prompt independiente POR CATEGORÍA
     * de cuidado. Cada uno se ejecuta en paralelo con la ficha extendida de
     * Pl@ntNet + el prompt de investigación del paso 1, y devuelve SOLO su
     * categoría en JSON {"text": "..."} con texto extenso (no una frase corta).
     */
    val CATEGORY_ORDER: List<String> = listOf(
        "description", "light", "watering", "soil",
        "climate", "flowering", "uses", "care"
    )

    /** Título mostrado en pantalla para cada categoría. */
    val CATEGORY_TITLES: Map<String, String> = mapOf(
        "description" to "Descripción",
        "light" to "Luz",
        "watering" to "Riego",
        "soil" to "Suelo",
        "climate" to "Clima",
        "flowering" to "Floración",
        "uses" to "Usos",
        "care" to "Cuidados"
    )

    val CATEGORY_PROMPTS: Map<String, String> = mapOf(
        "description" to """
            Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de
            investigación que te dan, describe EN EXTENSO la planta: qué es, porte,
            tallos, hojas, flores/frutos y rasgos para reconocerla (4 a 6 frases), con datos concretos y prácticos: horarios exactos (de mañana o de noche), frecuencia por estación, cantidades y medidas (litros, centímetros), distancias y ejemplos; nada de consejos vagos.
            Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
            bloques de código Markdown, con exactamente esta clave:
            {"text": "descripción extensa de la planta"}
            Si no conoces la planta, responde {"text":"unknown"}.
        """.trimIndent(),
        "light" to """
            Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de
            investigación que te dan, explica EN EXTENSO los requisitos de luz de
            la planta: sol directo/semisombra/sombra, horas al día, orientación y
            qué pasa con luz insuficiente o excesiva (4 a 6 frases), con datos concretos y prácticos: horarios exactos (de mañana o de noche), frecuencia por estación, cantidades y medidas (litros, centímetros), distancias y ejemplos; nada de consejos vagos.
            Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
            bloques de código Markdown, con exactamente esta clave:
            {"text": "requisitos de luz en extenso"}
            Si no conoces la planta, responde {"text":"unknown"}.
        """.trimIndent(),
        "watering" to """
            Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de
            investigación que te dan, explica EN EXTENSO el riego de la planta:
            frecuencia por estación, cantidad, método, drenaje y señales de exceso
            o falta de agua (4 a 6 frases), con datos concretos y prácticos: horarios exactos (de mañana o de noche), frecuencia por estación, cantidades y medidas (litros, centímetros), distancias y ejemplos; nada de consejos vagos.
            Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
            bloques de código Markdown, con exactamente esta clave:
            {"text": "requisitos de riego en extenso"}
            Si no conoces la planta, responde {"text":"unknown"}.
        """.trimIndent(),
        "soil" to """
            Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de
            investigación que te dan, explica EN EXTENSO el suelo ideal de la planta:
            tipo, textura, pH, materia orgánica, drenaje y maceta o sustrato
            recomendado (4 a 6 frases), con datos concretos y prácticos: horarios exactos (de mañana o de noche), frecuencia por estación, cantidades y medidas (litros, centímetros), distancias y ejemplos; nada de consejos vagos.
            Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
            bloques de código Markdown, con exactamente esta clave:
            {"text": "suelo y sustrato ideales en extenso"}
            Si no conoces la planta, responde {"text":"unknown"}.
        """.trimIndent(),
        "climate" to """
            Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de
            investigación que te dan, explica EN EXTENSO el clima de la planta:
            temperatura ideal y límites, humedad, resistencia al frío/calor y
            época de siembra o trasplante (4 a 6 frases), con datos concretos y prácticos: horarios exactos (de mañana o de noche), frecuencia por estación, cantidades y medidas (litros, centímetros), distancias y ejemplos; nada de consejos vagos.
            Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
            bloques de código Markdown, con exactamente esta clave:
            {"text": "clima y temperatura en extenso"}
            Si no conoces la planta, responde {"text":"unknown"}.
        """.trimIndent(),
        "flowering" to """
            Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de
            investigación que te dan, explica EN EXTENSO la floración de la planta:
            época, duración, características de flores/frutos y cómo favorecerla
            (4 a 6 frases), con datos concretos y prácticos: horarios exactos (de mañana o de noche), frecuencia por estación, cantidades y medidas (litros, centímetros), distancias y ejemplos; nada de consejos vagos.
            Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
            bloques de código Markdown, con exactamente esta clave:
            {"text": "floración en extenso"}
            Si no conoces la planta, responde {"text":"unknown"}.
        """.trimIndent(),
        "uses" to """
            Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de
            investigación que te dan, explica EN EXTENSO los usos de la planta:
            ornamental, alimenticio, medicinal, ecológico u otros, con ejemplos
            concretos (4 a 6 frases), con datos concretos y prácticos: horarios exactos (de mañana o de noche), frecuencia por estación, cantidades y medidas (litros, centímetros), distancias y ejemplos; nada de consejos vagos.
            Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
            bloques de código Markdown, con exactamente esta clave:
            {"text": "usos principales en extenso"}
            Si no conoces la planta, responde {"text":"unknown"}.
        """.trimIndent(),
        "care" to """
            Actúa como botánico experto. Con la ficha de Pl@ntNet y el prompt de
            investigación que te dan, explica EN EXTENSO los cuidados de la planta:
            fertilización, poda, trasplante, plagas y enfermedades comunes y cómo
            prevenirlas (4 a 6 frases), con datos concretos y prácticos: horarios exactos (de mañana o de noche), frecuencia por estación, cantidades y medidas (litros, centímetros), distancias y ejemplos; nada de consejos vagos. Si la planta solo crece en cierta estación, cierra con una alternativa viable para seguir cuidándola fuera de estación (por ejemplo, forzado en ambiente controlado).
            Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
            bloques de código Markdown, con exactamente esta clave:
            {"text": "cuidados básicos en extenso"}
            Si no conoces la planta, responde {"text":"unknown"}.
        """.trimIndent()
    )

    /**
     * Paso 3 de la cadena (IA de texto): con la planta, la categoría y el texto
     * ya generado, elige las palabras difíciles y explica cada una DE FORMA
     * SENCILLA y DENTRO DEL CONTEXTO de la categoría, para un agricultor sin
     * conocimiento previo. Se ejecuta en paralelo, una vez por categoría.
     */
    val GLOSSARY_PROMPT: String = """
        Actúas dentro de una app que explica plantas a agricultores y personas sin
        conocimiento previo. Te doy la planta, la categoría de cuidado y el texto
        ya generado para esa categoría. Elige de 3 a 8 palabras difíciles de ese
        texto y explica cada una de forma sencilla y DENTRO DEL CONTEXTO de la
        categoría (qué significa ahí, no una definición genérica de diccionario),
        en 1 o 2 frases cortas por palabra.

        Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
        bloques de código Markdown, con exactamente esta forma:
        {"terms": [{"word": "palabra tal como aparece en el texto", "meaning": "significado sencillo en contexto"}]}
        Si no hay palabras difíciles, responde {"terms": []}.
    """.trimIndent()

    /**
     * Categorías de cuidado que piden materiales y alternativas (paso 4).
     * Descripción, floración y usos no llevan materiales.
     */
    val MATERIAL_CATEGORIES: List<String> = listOf(
        "light", "watering", "soil", "climate", "care"
    )

    /**
     * Paso 4 de la cadena (IA de texto): con la planta, la categoría y el texto
     * ya generado, lista materiales, productos y herramientas útiles más
     * alternativas viables (p. ej. forzado en invernadero si no es la estación).
     * Se ejecuta en paralelo, una vez por categoría de cuidado.
     */
    val MATERIALS_PROMPT: String = """
        Actúas dentro de una app que ayuda a agricultores a cuidar plantas. Te doy
        la planta, la categoría de cuidado y el texto ya generado para esa
        categoría. Lista de 3 a 6 materiales, productos o herramientas concretos
        que se mencionan o sirven para esa categoría, con nombres genéricos y 1 o
        2 marcas comerciales de ejemplo como referencia cuando aplique. Incluye
        también alternativas viables cuando la estación o el clima no acompañen
        (por ejemplo, forzado de plantas en ambiente controlado, mallas, riego
        tecnificado). Cada ítem en 1 o 2 frases cortas y prácticas.

        Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional y sin
        bloques de código Markdown, con exactamente esta forma:
        {"items": [{"name": "nombre del material o alternativa", "detail": "para qué sirve y ejemplo de uso"}]}
        Si no hay materiales, responde {"items": []}.
    """.trimIndent()

    /**
     * Guía ligada a la desambiguación: explica QUÉ fotos tomar para identificar
     * mejor (qué órgano y cómo), con palabras resaltadas como el glosario.
     */
    val DISAMBIGUATION_GUIDE_TEXT: String = """
        La app encontró varias plantas parecidas. Para afinar, toma estas fotos:

        1) La flor en plano detalle y con buena luz.
        2) Una hoja sola, sin contraluz.
        3) El tallo o el fruto si los tiene.

        Evita fotos movidas o muy oscuras: con 2 o 3 fotos de órganos distintos la identificación mejora mucho.
    """.trimIndent()

    /** Términos resaltados de la guía, con su significado en este contexto. */
    val DISAMBIGUATION_GUIDE_TERMS: List<WordMeaning> = listOf(
        WordMeaning(
            "plano detalle",
            "Foto tomada muy de cerca, donde la flor o la hoja llena casi toda la imagen."
        ),
        WordMeaning(
            "órgano",
            "Cada parte de la planta: flor, hoja, fruto o tallo. Fotografiar varios órganos ayuda a identificar."
        ),
        WordMeaning(
            "contraluz",
            "Cuando la luz viene de frente a la cámara y la planta sale oscura. Ponte de espaldas al sol."
        )
    )

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
