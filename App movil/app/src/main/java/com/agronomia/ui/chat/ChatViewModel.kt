package com.agronomia.ui.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agronomia.data.remote.ChatProgressImageDto
import com.agronomia.data.remote.IdentifiedPlantDto
import com.agronomia.data.remote.NetworkModule
import com.agronomia.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Mensaje del chat (usuario o asistente). */
data class ChatMessageUi(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val imageRef: String? = null
)

/** Estado del chat y de las identificaciones guardadas en la cuenta. */
data class ChatUiState(
    val plants: List<IdentifiedPlantDto> = emptyList(),
    val selectedPlantId: String? = null,
    val messages: List<ChatMessageUi> = emptyList(),
    val progressImagesByPlant: Map<String, List<ChatProgressImageDto>> = emptyMap(),
    val loadingGalleryPlantIds: Set<String> = emptySet(),
    val loadingPlants: Boolean = true,
    val loadingHistory: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)

/** Chat por identificación; el servidor conserva el historial en la cuenta. */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val service = NetworkModule.djangoApiService
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var historyRequestId = 0
    private var plantsRequestId = 0
    private var ownerToken = NetworkModule.currentUserToken()

    init {
        refreshPlants()
    }

    /** Actualiza la lista al volver al chat después de identificar una planta. */
    fun refreshPlants() {
        val token = NetworkModule.currentUserToken()
        if (token != ownerToken) {
            ownerToken = token
            historyRequestId += 1
            _uiState.value = ChatUiState()
        }
        plantsRequestId += 1
        val requestId = plantsRequestId
        _uiState.value = _uiState.value.copy(loadingPlants = true, error = null)
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { service.identifiedPlants() }
                if (requestId != plantsRequestId) return@launch
                if (!response.success) throw IllegalStateException(response.error ?: "No se cargaron las plantas.")
                val current = _uiState.value.selectedPlantId
                _uiState.value = _uiState.value.copy(
                    plants = response.plants,
                    loadingPlants = false,
                    error = null
                )
                val next = response.plants.firstOrNull { it.id == current }
                    ?: response.plants.firstOrNull()
                if (next?.id != current) selectPlant(next?.id)
            } catch (error: Exception) {
                if (requestId != plantsRequestId) return@launch
                _uiState.value = _uiState.value.copy(
                    loadingPlants = false,
                    error = "No se pudieron cargar tus plantas. Revisa tu conexión e inténtalo de nuevo."
                )
            }
        }
    }

    /** Selecciona una planta y recupera su conversación persistente. */
    fun selectPlant(plantId: String?) {
        historyRequestId += 1
        val requestId = historyRequestId
        if (plantId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                selectedPlantId = null,
                messages = emptyList(),
                loadingHistory = false,
                busy = false,
                error = null
            )
            return
        }
        if (_uiState.value.selectedPlantId == plantId && _uiState.value.messages.isNotEmpty()) return
        _uiState.value = _uiState.value.copy(
            selectedPlantId = plantId,
            messages = emptyList(),
            loadingHistory = true,
            busy = false,
            error = null
        )
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { service.chatHistory(plantId) }
                if (!response.success) throw IllegalStateException(response.error ?: "No se recuperó la conversación.")
                if (requestId == historyRequestId) {
                    _uiState.value = _uiState.value.copy(
                        messages = response.messages.map { message ->
                            ChatMessageUi(
                                id = message.id,
                                isUser = message.role == "user",
                                text = message.content,
                                imageRef = message.imageUrl?.let { Constants.backendUrl(it) }
                            )
                        },
                        loadingHistory = false,
                        error = null
                    )
                }
            } catch (error: Exception) {
                if (requestId == historyRequestId) {
                    _uiState.value = _uiState.value.copy(
                        loadingHistory = false,
                        error = "No se pudo recuperar esta conversación. Inténtalo de nuevo."
                    )
                }
            }
        }
    }

    /** Envía una pregunta al chat asociado a la planta seleccionada. */
    fun loadGallery(plantId: String, force: Boolean = false) {
        if (!force && _uiState.value.progressImagesByPlant.containsKey(plantId)) return
        _uiState.value = _uiState.value.copy(
            loadingGalleryPlantIds = _uiState.value.loadingGalleryPlantIds + plantId
        )
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { service.chatProgressImages(plantId) }
                _uiState.value = _uiState.value.copy(
                    progressImagesByPlant = _uiState.value.progressImagesByPlant + (plantId to response.images),
                    loadingGalleryPlantIds = _uiState.value.loadingGalleryPlantIds - plantId
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    progressImagesByPlant = _uiState.value.progressImagesByPlant + (plantId to emptyList()),
                    loadingGalleryPlantIds = _uiState.value.loadingGalleryPlantIds - plantId
                )
            }
        }
    }

    /** Envía un texto y, opcionalmente, una foto adjunta al chat de la planta. */
    fun send(question: String, imageUri: Uri? = null) {
        val plantId = _uiState.value.selectedPlantId ?: return
        if ((question.isBlank() && imageUri == null) || _uiState.value.busy) return
        val trimmedQuestion = question.trim()
        val userMsg = ChatMessageUi(
            id = "u_${System.currentTimeMillis()}",
            isUser = true,
            text = trimmedQuestion.ifBlank { "Foto de progreso" },
            imageRef = imageUri?.toString()
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            busy = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val imagePart = imageUri?.let { uri ->
                        val resolver = getApplication<Application>().contentResolver
                        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("No se pudo leer la imagen seleccionada.")
                        val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
                        MultipartBody.Part.createFormData(
                            "image", "progreso.jpg", bytes.toRequestBody(mime.toMediaType())
                        )
                    }
                    service.chat(
                        url = Constants.backendUrl(Constants.BACKEND_CHAT_PATH),
                        message = trimmedQuestion.toRequestBody(TEXT_PLAIN),
                        plantId = plantId.toRequestBody(TEXT_PLAIN),
                        image = imagePart
                    )
                }
                if (!response.success || response.reply.isBlank()) {
                    throw IllegalStateException(response.error ?: "Sin respuesta del servidor.")
                }
                if (_uiState.value.selectedPlantId == plantId) {
                    if (imageUri != null) loadGallery(plantId, force = true)
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + ChatMessageUi(
                            id = "b_${System.currentTimeMillis()}",
                            isUser = false,
                            text = response.reply
                        ),
                        busy = false
                    )
                }
            } catch (error: Exception) {
                if (_uiState.value.selectedPlantId == plantId) {
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        error = "No se pudo enviar tu pregunta. Revisa tu conexión e inténtalo de nuevo."
                    )
                }
            }
        }
    }

    private companion object {
        val TEXT_PLAIN = "text/plain".toMediaType()
    }
}
