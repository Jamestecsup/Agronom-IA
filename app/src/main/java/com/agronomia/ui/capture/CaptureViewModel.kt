package com.agronomia.ui.capture

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agronomia.data.repository.PlantRepository
import com.agronomia.data.repository.PlantRepositoryImpl
import com.agronomia.domain.model.PlantResult
import com.agronomia.util.ImageUtils
import com.agronomia.util.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estados posibles de la identificación de una planta. */
sealed interface IdentificationUiState {
    /** Aún no se ha iniciado una identificación. */
    data object Idle : IdentificationUiState

    /** La imagen se está procesando, se identifica la especie o se busca su información. */
    data object Loading : IdentificationUiState

    /** Se obtuvo una identificación con su información. */
    data class Success(val plant: PlantResult) : IdentificationUiState

    /** Ocurrió un error (red, formato, sin certeza, etc.). */
    data class Error(val message: String) : IdentificationUiState
}

/**
 * ViewModel de captura e identificación.
 *
 * Mantiene las imágenes seleccionadas (hasta [ImageUtils.MAX_IMAGES]: ideal
 * flor, hoja y tallo) y el estado de la identificación. Toda la lógica de red
 * vive en [PlantRepository]; la UI solo observa [uiState].
 */
class CaptureViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PlantRepository = PlantRepositoryImpl()
) : AndroidViewModel(application) {

    private val _imageUris = MutableStateFlow<List<Uri>>(emptyList())

    /** Fotos elegidas para identificar, en orden. Vacía = sin imagen. */
    val imageUris: StateFlow<List<Uri>> = _imageUris.asStateFlow()

    private val _uiState = MutableStateFlow<IdentificationUiState>(IdentificationUiState.Idle)
    val uiState: StateFlow<IdentificationUiState> = _uiState.asStateFlow()

    private var identifyJob: Job? = null

    /**
     * Agrega fotos a la selección (galería múltiple o cámara), sin duplicados
     * y hasta [ImageUtils.MAX_IMAGES]. Al cambiar la selección se descarta el
     * resultado anterior.
     *
     * @return cuántas se agregaron realmente (0 si ya estaba llena).
     */
    fun onImagesAdded(uris: List<Uri>): Int {
        val fresh = uris.filter { it !in _imageUris.value }
        if (fresh.isEmpty()) return 0
        val room = ImageUtils.MAX_IMAGES - _imageUris.value.size
        if (room <= 0) return 0
        _imageUris.value = _imageUris.value + fresh.take(room)
        // Al elegir imágenes nuevas se descarta el resultado anterior.
        resetIdentification()
        return minOf(fresh.size, room)
    }

    /** Agrega la foto recién tomada con la cámara. */
    fun onCameraPhotoTaken(uri: Uri): Int = onImagesAdded(listOf(uri))

    /** Quita una foto de la selección. */
    fun removeImage(uri: Uri) {
        if (uri in _imageUris.value) {
            _imageUris.value = _imageUris.value - uri
            resetIdentification()
        }
    }

    /** Limpia la selección para tomar/elegir otras fotos. */
    fun clearImages() {
        resetIdentification()
        _imageUris.value = emptyList()
    }

    /** Reinicia el estado para permitir una nueva identificación. */
    fun resetIdentification() {
        identifyJob?.cancel()
        identifyJob = null
        _uiState.value = IdentificationUiState.Idle
    }

    /** Lanza la identificación de las imágenes actualmente seleccionadas. */
    fun identifyCurrentImage() {
        identify(_imageUris.value)
    }

    /**
     * Procesa cada imagen (fuera del hilo principal, vía [ImageUtils]) y envía
     * todas al repositorio. Es idempotente mientras hay una identificación en
     * curso, para evitar envíos duplicados si se pulsa "Identificar" varias veces.
     */
    fun identify(uris: List<Uri>) {
        if (_uiState.value is IdentificationUiState.Loading) return
        if (uris.isEmpty()) return

        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            _uiState.value = IdentificationUiState.Loading

            // Se procesan en orden; si una falla se informa cuál (1-based).
            val bytesList = mutableListOf<ByteArray>()
            for ((index, uri) in uris.withIndex()) {
                when (val processed = ImageUtils.uriToJpegBytes(getApplication(), uri)) {
                    is Resource.Error -> {
                        _uiState.value = IdentificationUiState.Error(
                            "Imagen ${index + 1}: ${processed.message}"
                        )
                        return@launch
                    }
                    is Resource.Success -> bytesList.add(processed.data)
                    Resource.Loading -> Unit // No aplica: uriToJpegBytes no emite Loading.
                }
            }
            _uiState.value = mapResult(repository.identifyPlant(bytesList))
        }
    }

    /** Traduce el [Result] del repositorio al estado de la UI. */
    private fun mapResult(result: Result<PlantResult>): IdentificationUiState =
        result.fold(
            onSuccess = { IdentificationUiState.Success(it) },
            onFailure = { error ->
                IdentificationUiState.Error(
                    error.message ?: "No se pudo identificar la planta. Inténtalo de nuevo."
                )
            }
        )
}
