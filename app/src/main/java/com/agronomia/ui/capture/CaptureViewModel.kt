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
 * Mantiene la imagen seleccionada y el estado de la identificación. Toda la
 * lógica de red vive en [PlantRepository]; la UI solo observa [uiState].
 */
class CaptureViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: PlantRepository = PlantRepositoryImpl()
) : AndroidViewModel(application) {

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    private val _uiState = MutableStateFlow<IdentificationUiState>(IdentificationUiState.Idle)
    val uiState: StateFlow<IdentificationUiState> = _uiState.asStateFlow()

    private var identifyJob: Job? = null

    fun onImageSelected(uri: Uri?) {
        if (uri != null) {
            _imageUri.value = uri
            // Al elegir una imagen nueva se descarta el resultado anterior.
            resetIdentification()
        }
    }

    fun clearImage() {
        resetIdentification()
        _imageUri.value = null
    }

    /** Reinicia el estado para permitir una nueva identificación. */
    fun resetIdentification() {
        identifyJob?.cancel()
        identifyJob = null
        _uiState.value = IdentificationUiState.Idle
    }

    /** Lanza la identificación de la imagen actualmente seleccionada. */
    fun identifyCurrentImage() {
        _imageUri.value?.let(::identify)
    }

    /**
     * Procesa la imagen (fuera del hilo principal, vía [ImageUtils]) y la envía
     * al repositorio. Es idempotente mientras hay una identificación en curso,
     * para evitar envíos duplicados si se pulsa "Identificar" varias veces.
     */
    fun identify(uri: Uri) {
        if (_uiState.value is IdentificationUiState.Loading) return

        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            _uiState.value = IdentificationUiState.Loading

            when (val processed = ImageUtils.uriToJpegBytes(getApplication(), uri)) {
                is Resource.Error -> {
                    _uiState.value = IdentificationUiState.Error(processed.message)
                }
                is Resource.Success -> {
                    _uiState.value = mapResult(repository.identifyPlant(processed.data))
                }
                Resource.Loading -> Unit // No aplica: uriToJpegBytes no emite Loading.
            }
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
