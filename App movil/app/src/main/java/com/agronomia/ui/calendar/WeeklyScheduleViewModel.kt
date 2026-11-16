package com.agronomia.ui.calendar

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agronomia.data.remote.CarePlanRequest
import com.agronomia.data.remote.CarePlanTaskDto
import com.agronomia.data.remote.CareTaskTimeRequest
import com.agronomia.data.remote.DjangoApiService
import com.agronomia.data.remote.NetworkModule
import com.agronomia.data.remote.ScheduleClassesRequest
import com.agronomia.data.remote.WeeklyClassDto
import com.agronomia.data.remote.WeeklyScheduleResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalTime

data class WeeklyScheduleUiState(
    val isLoading: Boolean = true,
    val isUploading: Boolean = false,
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val classes: List<WeeklyClassDto> = emptyList(),
    val careTasks: List<CarePlanTaskDto> = emptyList(),
    val pendingImportId: String? = null,
    val editingClasses: List<WeeklyClassDto>? = null,
    val draftCareTasks: List<CarePlanTaskDto>? = null,
    val error: String? = null,
    val notice: String? = null
) {
    val classConflicts: List<Pair<CarePlanTaskDto, WeeklyClassDto>>
        get() = careTasks.mapNotNull { task ->
            classes.firstOrNull { block -> overlaps(task.dayOfWeek, task.startTime, task.durationMinutes, block) }
                ?.let { task to it }
        }

    companion object {
        private fun overlaps(day: Int, start: String, duration: Int, block: WeeklyClassDto): Boolean {
            if (day != block.dayOfWeek) return false
            return try {
                val taskStart = LocalTime.parse(start).toSecondOfDay() / 60
                val taskEnd = taskStart + duration
                val classStart = LocalTime.parse(block.startTime).toSecondOfDay() / 60
                val classEnd = LocalTime.parse(block.endTime).toSecondOfDay() / 60
                taskStart < classEnd && taskEnd > classStart
            } catch (_: Exception) {
                false
            }
        }
    }
}

class WeeklyScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val api: DjangoApiService = NetworkModule.djangoApiService
    private val _uiState = MutableStateFlow(WeeklyScheduleUiState())
    val uiState: StateFlow<WeeklyScheduleUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = withContext(Dispatchers.IO) { api.weeklySchedule() }
                applySchedule(data)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = error.userMessage())
            }
        }
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null, notice = null)
            try {
                val resolver = getApplication<Application>().contentResolver
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalArgumentException("No se pudo leer el PDF seleccionado.")
                }
                require(bytes.isNotEmpty() && bytes.size <= 15 * 1024 * 1024) {
                    "El PDF debe pesar menos de 15 MB."
                }
                require(bytes.take(5).toByteArray().decodeToString() == "%PDF-") {
                    "El archivo seleccionado no es un PDF válido."
                }
                val part = MultipartBody.Part.createFormData(
                    "pdf", "horario.pdf", bytes.toRequestBody("application/pdf".toMediaType())
                )
                val result = withContext(Dispatchers.IO) { api.importSchedulePdf(part) }
                if (!result.success || result.importId.isBlank()) error("No se pudo preparar la revisión del horario.")
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    pendingImportId = result.importId,
                    editingClasses = result.classes,
                    notice = "Revisa y corrige los bloques detectados antes de guardar el horario."
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isUploading = false, error = error.userMessage())
            }
        }
    }

    fun updateEditingClass(index: Int, update: (WeeklyClassDto) -> WeeklyClassDto) {
        val current = _uiState.value.editingClasses ?: return
        if (index !in current.indices) return
        _uiState.value = _uiState.value.copy(editingClasses = current.toMutableList().also { it[index] = update(it[index]) })
    }

    fun addEditingClass() {
        val current = _uiState.value.editingClasses ?: return
        _uiState.value = _uiState.value.copy(
            editingClasses = current + WeeklyClassDto(courseName = "Nuevo curso", dayOfWeek = 1)
        )
    }

    fun setMessage(message: String) {
        _uiState.value = _uiState.value.copy(notice = message, error = null)
    }

    fun removeEditingClass(index: Int) {
        val current = _uiState.value.editingClasses ?: return
        _uiState.value = _uiState.value.copy(editingClasses = current.filterIndexed { itemIndex, _ -> itemIndex != index })
    }

    fun cancelImport() {
        val importId = _uiState.value.pendingImportId ?: return
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { api.cancelScheduleImport(importId) } } catch (_: Exception) { }
            _uiState.value = _uiState.value.copy(pendingImportId = null, editingClasses = null, notice = null)
        }
    }

    fun confirmSchedule() {
        val importId = _uiState.value.pendingImportId ?: return
        val classes = _uiState.value.editingClasses ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) { api.confirmScheduleImport(importId, ScheduleClassesRequest(classes)) }
                if (!result.success) error("No se pudo guardar el horario.")
                val refreshed = withContext(Dispatchers.IO) { api.weeklySchedule() }
                applySchedule(refreshed, "Horario confirmado. El PDF original se eliminó del servidor.")
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = error.userMessage())
            }
        }
    }

    fun generateCarePlan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null, notice = null)
            try {
                val result = withContext(Dispatchers.IO) { api.draftCarePlan() }
                if (!result.success) error("No se pudo generar el plan de cuidados.")
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    draftCareTasks = result.tasks,
                    notice = if (result.tasks.isEmpty()) "La IA no encontró acciones de cuidado con datos suficientes." else "Revisa y ajusta el plan antes de guardarlo."
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isGenerating = false, error = error.userMessage())
            }
        }
    }

    fun updateDraftTask(index: Int, update: (CarePlanTaskDto) -> CarePlanTaskDto) {
        val current = _uiState.value.draftCareTasks ?: return
        if (index !in current.indices) return
        _uiState.value = _uiState.value.copy(draftCareTasks = current.toMutableList().also { it[index] = update(it[index]) })
    }

    fun removeDraftTask(index: Int) {
        val current = _uiState.value.draftCareTasks ?: return
        _uiState.value = _uiState.value.copy(draftCareTasks = current.filterIndexed { itemIndex, _ -> itemIndex != index })
    }

    fun confirmCarePlan() {
        val tasks = _uiState.value.draftCareTasks ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) { api.confirmCarePlan(CarePlanRequest(tasks)) }
                if (!result.success) error("No se pudo guardar el plan.")
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    careTasks = result.careTasks,
                    draftCareTasks = null,
                    notice = "Plan de cuidados guardado en tu cuenta."
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = error.userMessage())
            }
        }
    }

    fun reschedule(task: CarePlanTaskDto) {
        val next = nextAvailable(task) ?: run {
            _uiState.value = _uiState.value.copy(error = "No encontramos otro horario semanal sin cruce.")
            return
        }
        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.rescheduleCareTask(task.id, CareTaskTimeRequest(next.first, next.second))
                }
                val moved = response.careTask ?: error("El servicio no devolvió el cuidado reprogramado.")
                _uiState.value = _uiState.value.copy(
                    careTasks = _uiState.value.careTasks.map { if (it.id == task.id) moved else it },
                    error = null,
                    notice = "Cuidado reprogramado."
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(error = error.userMessage())
            }
        }
    }

    fun deleteCareTask(taskId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.deleteCareTask(taskId) }
                _uiState.value = _uiState.value.copy(careTasks = _uiState.value.careTasks.filterNot { it.id == taskId })
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(error = error.userMessage())
            }
        }
    }

    private fun nextAvailable(task: CarePlanTaskDto): Pair<Int, String>? {
        val classes = _uiState.value.classes
        val otherTasks = _uiState.value.careTasks.filterNot { it.id == task.id }
        for (dayOffset in 0..6) {
            val day = ((task.dayOfWeek - 1 + dayOffset) % 7) + 1
            val earliest = if (dayOffset == 0) {
                runCatching { LocalTime.parse(task.startTime).plusMinutes(15) }.getOrDefault(LocalTime.of(7, 0))
            } else LocalTime.of(7, 0)
            var minutes = maxOf(7 * 60, (earliest.hour * 60 + earliest.minute + 14) / 15 * 15)
            while (minutes + task.durationMinutes <= 21 * 60) {
                val start = LocalTime.of(minutes / 60, minutes % 60)
                val endMinute = minutes + task.durationMinutes
                val classConflict = classes.any { block ->
                    if (block.dayOfWeek != day) false else runCatching {
                        val a = LocalTime.parse(block.startTime).toSecondOfDay() / 60
                        val b = LocalTime.parse(block.endTime).toSecondOfDay() / 60
                        minutes < b && endMinute > a
                    }.getOrDefault(false)
                }
                val careConflict = otherTasks.any { other ->
                    if (other.dayOfWeek != day) false else runCatching {
                        val a = LocalTime.parse(other.startTime).toSecondOfDay() / 60
                        minutes < a + other.durationMinutes && endMinute > a
                    }.getOrDefault(false)
                }
                if (!classConflict && !careConflict) return day to start.toString()
                minutes += 15
            }
        }
        return null
    }

    private fun applySchedule(response: WeeklyScheduleResponse, notice: String? = null) {
        val pending = response.pendingImport
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isSaving = false,
            classes = response.classes,
            careTasks = response.careTasks,
            pendingImportId = pending?.id,
            editingClasses = pending?.classes,
            draftCareTasks = null,
            error = null,
            notice = notice
        )
    }

    private fun Exception.userMessage(): String =
        message?.takeIf { it.length < 200 } ?: "No pudimos completar esta acción. Revisa tu conexión e inténtalo otra vez."
}
