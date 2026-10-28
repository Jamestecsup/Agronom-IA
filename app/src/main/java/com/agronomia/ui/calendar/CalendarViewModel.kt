package com.agronomia.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agronomia.data.canvas.CanvasAuthState
import com.agronomia.data.canvas.CanvasPreferences
import com.agronomia.data.canvas.CanvasRepository
import com.agronomia.domain.canvas.CanvasCalendarItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CalendarUiState(
    val authState: CanvasAuthState = CanvasAuthState(),
    val isLoading: Boolean = false,
    val isConnecting: Boolean = false,
    val connectError: String? = null,
    val loadError: String? = null,
    val allEvents: List<CanvasCalendarItem> = emptyList(),
    val selectedDateIso: String = LocalDate.now().toString()
) {
    val eventsForSelectedDate: List<CanvasCalendarItem>
        get() = allEvents.filter { it.dateIso == selectedDateIso }

    val upcomingEvents: List<CanvasCalendarItem>
        get() = allEvents.filter { !it.isPast }

    val datesWithEvents: Set<String>
        get() = allEvents.map { it.dateIso }.toSet()
}

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CanvasRepository(application)
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        CanvasPreferences.init(application)
        viewModelScope.launch {
            CanvasPreferences.authState.collect { auth ->
                _uiState.value = _uiState.value.copy(
                    authState = auth,
                    connectError = null
                )
                if (auth.isConnected && _uiState.value.allEvents.isEmpty()) {
                    loadEvents()
                }
            }
        }
    }

    fun connectCanvas(domain: String, token: String) {
        val trimmedToken = token.trim()
        val trimmedDomain = domain.trim().ifBlank { CanvasAuthState.DEFAULT_CANVAS_DOMAIN }

        if (trimmedToken.isBlank()) {
            _uiState.value = _uiState.value.copy(connectError = "Ingresa tu token de acceso de Canvas.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true, connectError = null)
            val result = repository.testConnection(trimmedDomain, trimmedToken)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isConnecting = false, connectError = null)
                    loadEvents()
                },
                onFailure = { error ->
                    val message = when {
                        error.message?.contains("401", ignoreCase = true) == true ->
                            "Token no autorizado (401). Verifica que lo hayas copiado completo y esté activo en Canvas."
                        error.message?.contains("404", ignoreCase = true) == true ->
                            "No se encontró el servidor de Canvas en la URL especificada."
                        else ->
                            "Error al conectar con Canvas: ${error.localizedMessage ?: "Revisa tu conexión a internet."}"
                    }
                    _uiState.value = _uiState.value.copy(isConnecting = false, connectError = message)
                }
            )
        }
    }

    fun loadEvents() {
        if (!_uiState.value.authState.isConnected) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            val result = repository.getCalendarEvents()
            result.fold(
                onSuccess = { events ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allEvents = events,
                        loadError = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadError = "No se pudieron sincronizar los eventos: ${error.localizedMessage}"
                    )
                }
            )
        }
    }

    fun selectDate(dateIso: String) {
        _uiState.value = _uiState.value.copy(selectedDateIso = dateIso)
    }

    fun disconnect() {
        repository.disconnect()
        _uiState.value = _uiState.value.copy(
            allEvents = emptyList(),
            loadError = null,
            connectError = null
        )
    }
}
