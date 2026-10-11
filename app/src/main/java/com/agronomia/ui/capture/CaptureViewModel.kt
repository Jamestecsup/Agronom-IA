package com.agronomia.ui.capture

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agronomia.data.repository.AnalysisResult
import com.agronomia.data.repository.PlantRepository
import com.agronomia.data.repository.PlantRepositoryImpl
import com.agronomia.domain.model.PlantResult
import com.agronomia.domain.model.SpeciesCandidate
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

    /**
     * Las fotos tienen problemas de calidad (oscuras o movidas): se muestra
     * qué mejorar, con opción de continuar de todos modos.
     */
    data class QualityWarning(val issues: List<String>) : IdentificationUiState

    /**
     * Sin certeza suficiente: el agricultor elige entre estas candidatas
     * (con guía de qué fotos tomar para afinar).
     */
    data class Disambiguation(val candidates: List<SpeciesCandidate>) : IdentificationUiState

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

    /** Órgano por foto (uri → auto/flower/leaf/fruit/bark). */
    private val _organs = MutableStateFlow<Map<String, String>>(emptyMap())
    val organs: StateFlow<Map<String, String>> = _organs.asStateFlow()

    /** Candidatas de la última desambiguación (para confirmar la elegida). */
    private var lastCandidates: List<SpeciesCandidate> = emptyList()

    /** Permite saltar el chequeo de calidad una vez ("continuar de todos modos"). */
    private var skipQualityOnce = false

    /** Órgano de una foto (auto si no se eligió). */
    fun organFor(uri: Uri): String = _organs.value[uri.toString()] ?: "auto"

    fun setOrgan(uri: Uri, organ: String) {
        val key = uri.toString()
        if (_organs.value[key] != organ) {
            _organs.value = _organs.value + (key to organ)
            resetIdentification()
        }
    }

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

    /** Quita una foto de la selección (y su órgano elegido). */
    fun removeImage(uri: Uri) {
        if (uri in _imageUris.value) {
            _imageUris.value = _imageUris.value - uri
            _organs.value = _organs.value - uri.toString()
            resetIdentification()
        }
    }

    /** Limpia la selección para tomar/elegir otras fotos. */
    fun clearImages() {
        resetIdentification()
        _imageUris.value = emptyList()
        _organs.value = emptyMap()
        lastCandidates = emptyList()
        skipQualityOnce = false
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
     * Procesa cada imagen (fuera del hilo principal, vía [ImageUtils]), revisa
     * su calidad y la envía al repositorio. Es idempotente mientras hay una
     * identificación en curso, para evitar envíos duplicados si se pulsa
     * "Identificar" varias veces.
     */
    fun identify(uris: List<Uri>) {
        if (_uiState.value is IdentificationUiState.Loading) return
        if (uris.isEmpty()) return

        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            _uiState.value = IdentificationUiState.Loading

            // 1. Procesar en orden; si una falla se informa cuál (1-based).
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

            // 2. Chequeo de calidad previo (se puede saltar una vez).
            if (!skipQualityOnce) {
                val issues = bytesList.flatMapIndexed { index, bytes ->
                    ImageUtils.qualityIssues(bytes).map { "Foto ${index + 1} $it" }
                }
                if (issues.isNotEmpty()) {
                    _uiState.value = IdentificationUiState.QualityWarning(issues)
                    return@launch
                }
            }
            skipQualityOnce = false

            // 3. Análisis: identificación, desambiguación o fallo.
            // Cada foto aporta su órgano elegido, en el mismo orden.
            val organs = uris.map { organFor(it) }
            when (val result = repository.analyze(bytesList, organs)) {
                is AnalysisResult.Identified -> {
                    _uiState.value = IdentificationUiState.Success(result.plant)
                }
                is AnalysisResult.Ambiguous -> {
                    lastCandidates = result.candidates
                    _uiState.value = IdentificationUiState.Disambiguation(result.candidates)
                }
                is AnalysisResult.Failed -> {
                    _uiState.value = IdentificationUiState.Error(result.message)
                }
            }
        }
    }

    /** Continúa la identificación aunque haya aviso de calidad. */
    fun proceedDespiteQuality() {
        skipQualityOnce = true
        identify(_imageUris.value)
    }

    /** Genera la información completa de la candidata elegida. */
    fun chooseCandidate(candidate: SpeciesCandidate) {
        if (_uiState.value is IdentificationUiState.Loading) return
        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            _uiState.value = IdentificationUiState.Loading
            _uiState.value = mapResult(
                repository.confirmCandidate(candidate, lastCandidates)
            )
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
