package com.correa.agronom_ia

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Public model metadata received from Spring; no provider URL or credential reaches the client. */
data class AiModelOption(
    val id: String,
    val name: String,
    val provider: String,
    val modelId: String,
    val available: Boolean? = null,
    val unavailableReason: String? = null,
    val welcomeMessage: String
)

internal object AiModelCatalogParser {
    const val DEEPSEEK_ID = "deepseek-opencode"
    const val QWEN_ID = "qwen-local"

    val defaults = listOf(
        AiModelOption(
            DEEPSEEK_ID, "DeepSeek V4.1 Flash Free", "OpenCode", "deepseek-v4.1-flash-free",
            welcomeMessage = "¡Hola! Soy DeepSeek, tu asistente de inteligencia artificial. Estoy listo para ayudarte a resolver dudas, analizar información, escribir código y explorar nuevas ideas. ¿Qué te gustaría hacer hoy?"
        ),
        AiModelOption(
            QWEN_ID, "Qwen 3.6 35B A3B FP8", "Open WebUI", "Qwen/Qwen3.6-35B-A3B-FP8",
            welcomeMessage = "¡Hola! Soy Qwen, tu asistente de inteligencia artificial local. Puedo ayudarte a responder preguntas, desarrollar ideas, analizar información y resolver problemas. ¿En qué te gustaría que trabajemos hoy?"
        )
    )

    fun parse(body: String): List<AiModelOption> {
        val models = JSONObject(body).optJSONArray("models") ?: return defaults
        val byId = defaults.associateBy { it.id }
        val parsed = buildList {
            for (index in 0 until models.length()) {
                val item = models.optJSONObject(index) ?: continue
                val fallback = byId[item.optString("id")] ?: continue
                add(
                    fallback.copy(
                        name = item.optString("name").takeIf(String::isNotBlank) ?: fallback.name,
                        provider = item.optString("provider").takeIf(String::isNotBlank) ?: fallback.provider,
                        modelId = item.optString("modelId").takeIf(String::isNotBlank) ?: fallback.modelId,
                        available = if (item.has("available")) item.optBoolean("available") else null,
                        unavailableReason = item.optString("unavailableReason").takeIf(String::isNotBlank) ?: null,
                        welcomeMessage = item.optString("welcomeMessage").takeIf(String::isNotBlank) ?: fallback.welcomeMessage
                    )
                )
            }
        }
        val parsedById = parsed.associateBy { it.id }
        return defaults.map { fallback -> parsedById[fallback.id] ?: fallback }
    }
}

internal sealed class AiSseEvent {
    data class Delta(val text: String) : AiSseEvent()
    data object Done : AiSseEvent()
    data class Error(val message: String) : AiSseEvent()
    data object Ignored : AiSseEvent()
}

internal object AiSseEventParser {
    fun parse(name: String, data: String): AiSseEvent = when (name) {
        "delta" -> {
            val text = try { JSONObject(data).optString("text") }
            catch (_: JSONException) { throw IllegalStateException("El backend transmitió un fragmento de respuesta no válido.") }
            if (text.isEmpty()) AiSseEvent.Ignored else AiSseEvent.Delta(text)
        }
        "done" -> AiSseEvent.Done
        "error" -> {
            val message = try { JSONObject(data).optString("message") }
            catch (_: JSONException) { null }
            AiSseEvent.Error(message?.takeIf(String::isNotBlank) ?: "El proveedor no pudo completar la respuesta.")
        }
        else -> AiSseEvent.Ignored
    }
}

/** Android talks only to the allow-listed Spring chat API. Spring owns provider URLs and secrets. */
internal object AiChatApi {
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 210_000

    fun fetchModels(baseUrl: String): List<AiModelOption> {
        val connection = open(baseUrl.trimEnd('/') + "/api/ai/models", "GET", "application/json")
        try {
            val code = connection.responseCode
            val body = responseBody(connection, code)
            if (code !in 200..299) throw apiError(code, body)
            return try {
                AiModelCatalogParser.parse(body)
            } catch (_: JSONException) {
                throw IllegalStateException("El backend devolvió un catálogo de modelos no válido.")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun streamReply(
        baseUrl: String,
        providerId: String,
        messages: List<ChatTurn>,
        onDelta: (String) -> Unit
    ): String {
        val request = requestPayload(providerId, messages)
        val connection = open(baseUrl.trimEnd('/') + "/api/ai/chat", "POST", "text/event-stream")
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { it.write(request.toString()) }

            val code = connection.responseCode
            if (code !in 200..299) throw apiError(code, responseBody(connection, code))

            val response = StringBuilder()
            var eventName = "message"
            val eventData = mutableListOf<String>()
            var completed = false
            fun dispatchEvent() {
                if (eventData.isEmpty()) {
                    eventName = "message"
                    return
                }
                when (val event = AiSseEventParser.parse(eventName, eventData.joinToString("\n"))) {
                    is AiSseEvent.Delta -> {
                        response.append(event.text)
                        onDelta(event.text)
                    }
                    is AiSseEvent.Error -> throw IllegalStateException(event.message)
                    AiSseEvent.Done -> completed = true
                    AiSseEvent.Ignored -> Unit
                }
                eventName = "message"
                eventData.clear()
            }

            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.isEmpty() -> dispatchEvent()
                        line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                        line.startsWith("data:") -> eventData.add(line.substringAfter(':').trimStart())
                        line.startsWith(":") -> Unit // SSE comment/keep-alive.
                    }
                    if (completed) break
                }
                if (!completed) dispatchEvent()
            }
            check(completed) { "La conexión terminó antes de que el backend confirmara el final de la respuesta. Reintenta." }
            return response.toString().trim().takeIf(String::isNotEmpty)
                ?: throw IllegalStateException("El modelo terminó sin generar una respuesta. Puedes reintentar.")
        } finally {
            connection.disconnect()
        }
    }

    internal fun requestPayload(providerId: String, messages: List<ChatTurn>): JSONObject {
        require(AiModelCatalogParser.defaults.any { it.id == providerId }) { "El modelo seleccionado no está autorizado." }
        require(messages.isNotEmpty()) { "Incluye al menos un mensaje." }
        return JSONObject().put("providerId", providerId).put("messages", JSONArray().apply {
            messages.forEach { message ->
                put(JSONObject().put("role", message.role).put("content", message.text))
            }
        })
    }

    private fun open(url: String, method: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", accept)
        }

    private fun responseBody(connection: HttpURLConnection, code: Int): String =
        (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()

    private fun apiError(code: Int, body: String): IllegalStateException {
        val detail = runCatching { JSONObject(body).optString("message").takeIf(String::isNotBlank) }.getOrNull()
        val message = when (code) {
            400 -> detail ?: "La solicitud de chat no es válida."
            401, 403 -> "El backend rechazó la solicitud. Comprueba la configuración de acceso."
            429 -> "Se alcanzó el límite de uso del modelo. Espera un momento y reintenta."
            502, 503, 504 -> detail ?: "El modelo seleccionado no está disponible en este momento."
            in 500..599 -> "El backend tuvo un problema temporal (HTTP $code)."
            else -> detail ?: "El backend respondió con HTTP $code."
        }
        return IllegalStateException(message)
    }
}
