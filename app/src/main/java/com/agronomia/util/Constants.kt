package com.agronomia.util

import com.agronomia.BuildConfig
import com.agronomia.domain.model.WordMeaning

/**
 * Constantes de la app.
 *
 * Las claves de Pl@ntNet y de la IA viven en `backend-django/.env`: aquí solo
 * está la URL del backend, porque la app no habla con esos servicios.
 */
object Constants {
    // ---------------- Backend Django (servicio de usuario) ----------------
    /**
     * URL base del backend Django. En el emulador, 10.0.2.2 apunta al host.
     * En un móvil físico se configura BACKEND_BASE_URL en local.properties.
     */
    val BACKEND_BASE_URL: String = BuildConfig.BACKEND_BASE_URL
    const val BACKEND_UPLOAD_PATH = "agronomia-api/api/images.php"
    const val BACKEND_IDENTIFY_PATH = "agronomia-api/api/identify"
    const val BACKEND_CONFIRM_PATH = "agronomia-api/api/confirm"
    const val BACKEND_CHAT_PATH = "agronomia-api/api/chat"

    /** Une la base del backend con una ruta relativa. */
    fun backendUrl(path: String): String {
        val base = if (BACKEND_BASE_URL.endsWith("/")) BACKEND_BASE_URL else "$BACKEND_BASE_URL/"
        return base + path.removePrefix("/")
    }

    /**
     * Órganos que acepta Pl@ntNet (se envía uno por foto). El usuario elige uno
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
     * Timeouts de red. Subir las fotos y esperar el análisis puede tardar, por
     * eso lectura/escritura son más amplios que el de conexión.
     */
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 180L
    const val WRITE_TIMEOUT_SECONDS = 120L

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
     * User-Agent identificatorio exigido por Wikimedia (sin él responde 403):
     * se envía tanto al buscar fotos como al descargarlas con Coil.
     */
    const val WIKIMEDIA_USER_AGENT = "Agronom-IA/1.0 (Android)"
}
