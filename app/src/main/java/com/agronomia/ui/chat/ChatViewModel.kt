package com.agronomia.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agronomia.data.remote.NetworkModule
import com.agronomia.domain.model.SpeciesCandidate
import com.agronomia.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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
 * ViewModel del chat botánico.
 *
 * Llama al backend Django (`/agronomia-api/api/chat`), que aplica la skill
 * `chat-assistant.md` y usa como contexto la planta detectada. Así las claves
 * de IA no viajan en el APK.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val service = NetworkModule.djangoApiService

    /** Sesión del chat: Django guarda el historial por este identificador. */
    private val sessionId = "android_${System.currentTimeMillis()}"

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
                val answer = askBackend(question.trim())
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
                    text = "No se pudo responder. Revisa tu conexión e inténtalo de nuevo."
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + errMsg,
                    busy = false
                )
            }
        }
    }

    /** Pregunta al backend Django con el contexto de la planta detectada. */
    private suspend fun askBackend(question: String): String {
        val detected = _uiState.value.lastDetected
        val contextBlock = detected?.let {
            "Contexto de la planta detectada:\n" +
                "- Nombre científico: ${it.scientificName}\n" +
                "- Nombres comunes: ${it.commonNames.joinToString(", ")}\n" +
                "- Familia: ${it.family}\n" +
                "- Similitud: ${(it.confidence * 100).toInt()}%\n"
        } ?: "Aún no se ha detectado ninguna planta. Sugiere usar el escáner."

        val response = withContext(Dispatchers.IO) {
            service.chat(
                url = Constants.backendUrl(Constants.BACKEND_CHAT_PATH),
                message = question.toRequestBody(TEXT_PLAIN),
                sessionId = sessionId.toRequestBody(TEXT_PLAIN),
                context = contextBlock.toRequestBody(TEXT_PLAIN)
            )
        }
        if (!response.success || response.reply.isBlank()) {
            throw IllegalStateException(response.error ?: "Sin respuesta del servidor.")
        }
        return response.reply
    }

    private companion object {
        val TEXT_PLAIN = "text/plain".toMediaType()
    }
}
