package com.agronomia.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agronomia.data.remote.ChatCompletionRequest
import com.agronomia.data.remote.ChatMessage
import com.agronomia.data.remote.ContentPart
import com.agronomia.data.remote.NetworkModule
import com.agronomia.domain.model.SpeciesCandidate
import com.agronomia.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mensaje del chat (usuario o asistente). */
data class ChatMessageUi(
    val id: String,
    val isUser: Boolean,
    val text: String
)

/** Estado del chat: mensajes, envío y planta detectada. */
data class ChatUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val busy: Boolean = false,
    val lastDetected: SpeciesCandidate? = null
)

/**
 * ViewModel del chat botánico conectado a Gemini.
 *
 * - Sin planta detectada: saluda y sugiere usar el escáner.
 * - Con planta detectada: pregunta sobre ella usando la skill del chat
 *   (assets/skills/chat-assistant.md) y el contexto de la detección.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val service = NetworkModule.plantApiService
    private val json = NetworkModule.json

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Guarda la última planta detectada (desde la pantalla de resultado). */
    fun setLastDetected(candidate: SpeciesCandidate?) {
        _uiState.value = _uiState.value.copy(lastDetected = candidate)
    }

    /** Envía un mensaje de texto al chat. */
    fun send(question: String) {
        if (question.isBlank() || _uiState.value.busy) return

        val userMsg = ChatMessageUi(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = question.trim()
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            busy = true
        )

        viewModelScope.launch {
            try {
                val answer = askGemini(question.trim())
                val botMsg = ChatMessageUi(
                    id = "b_${System.currentTimeMillis()}",
                    isUser = false,
                    text = answer
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + botMsg,
                    busy = false
                )
            } catch (e: Exception) {
                val errMsg = ChatMessageUi(
                    id = "e_${System.currentTimeMillis()}",
                    isUser = false,
                    text = "Error: ${e.message}"
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + errMsg,
                    busy = false
                )
            }
        }
    }

    /** Pregunta a Gemini usando la skill del chat y el contexto de la detección. */
    private suspend fun askGemini(question: String): String {
        val app = getApplication<Application>()
        val skillText = try {
            app.assets.open("skills/chat-assistant.md").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            "Eres un asistente botánico útil. Responde en español claro."
        }
        val contextBlock = _uiState.value.lastDetected?.let {
            "\n\nContexto de la planta detectada:\n" +
                "- Nombre científico: ${it.scientificName}\n" +
                "- Nombres comunes: ${it.commonNames.joinToString(", ")}\n" +
                "- Familia: ${it.family}\n" +
                "- Similitud: ${(it.confidence * 100).toInt()}%\n"
        } ?: "\n\nAún no se ha detectado ninguna planta. Sugeriere usar el escáner."

        val request = ChatCompletionRequest(
            model = Constants.AI_MODEL,
            messages = listOf(
                ChatMessage(
                    role = "system",
                    content = listOf(ContentPart(type = "text", text = skillText))
                ),
                ChatMessage(
                    role = "user",
                    content = listOf(ContentPart(type = "text", text = "$question$contextBlock"))
                )
            )
        )
        val response = withContext(Dispatchers.IO) {
            service.identifyPlant(
                url = Constants.identifyEndpointUrl(),
                authorization = "${Constants.AUTH_HEADER_PREFIX} ${Constants.API_KEY}",
                request = request
            )
        }
        return response.choices.firstOrNull()?.message?.content ?: "Sin respuesta."
    }
}
