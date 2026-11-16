package com.agronomia.ui.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agronomia.data.remote.CarePlanTaskDto
import com.agronomia.data.remote.WeeklyClassDto
import com.agronomia.ui.components.AgronomIaTopBar
import java.time.LocalTime

private val weekDays = listOf("", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

@Composable
fun CalendarScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Horario semanal") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Tareas Canvas") })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (selectedTab == 0) SchedulePlannerScreen()
            else CanvasTasksScreen()
        }
    }
}

@Composable
private fun SchedulePlannerScreen(viewModel: WeeklyScheduleViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPdf)
    }
    val pdfSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching { SchedulePdfExporter.write(context, uri, state.classes, state.careTasks) }
                .onFailure { viewModel.setMessage("No pudimos crear el PDF. Elige otra ubicación e inténtalo de nuevo.") }
                .onSuccess { viewModel.setMessage("Horario exportado como PDF.") }
        }
    }

    Scaffold(
        topBar = { AgronomIaTopBar() },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mi horario semanal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Clases oficiales y cuidados recomendados para tus plantas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = "Actualizar horario")
                }
            }

            state.error?.let { MessageCard(it, isError = true) }
            state.notice?.let { MessageCard(it, isError = false) }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (state.editingClasses != null) {
                    ScheduleReviewSection(
                        classes = state.editingClasses.orEmpty(),
                        saving = state.isSaving,
                        onUpdate = viewModel::updateEditingClass,
                        onRemove = viewModel::removeEditingClass,
                        onAdd = viewModel::addEditingClass,
                        onCancel = viewModel::cancelImport,
                        onConfirm = viewModel::confirmSchedule
                    )
                } else if (state.classes.isEmpty()) {
                    OutlinedCard(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Sube el PDF oficial de tu horario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Analizaremos el texto y la grilla visual para detectar días y horas. Podrás corregir los bloques antes de guardarlos. El PDF se elimina cuando confirmes el horario.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, enabled = !state.isUploading, modifier = Modifier.fillMaxWidth()) {
                                if (state.isUploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.isUploading) "Analizando horario…" else "Seleccionar PDF")
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, enabled = !state.isUploading, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.isUploading) "Analizando…" else "Cambiar horario")
                        }
                        OutlinedButton(onClick = { pdfSaver.launch("Horario_semanal_AgronomIA.pdf") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.DateRange, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Exportar PDF")
                        }
                    }
                    WeeklyClassesSection(state.classes)
                    CarePlanSection(
                        savedTasks = state.careTasks,
                        draftTasks = state.draftCareTasks,
                        classes = state.classes,
                        isGenerating = state.isGenerating,
                        isSaving = state.isSaving,
                        onGenerate = viewModel::generateCarePlan,
                        onUpdateDraft = viewModel::updateDraftTask,
                        onRemoveDraft = viewModel::removeDraftTask,
                        onConfirm = viewModel::confirmCarePlan,
                        onReschedule = viewModel::reschedule,
                        onDelete = viewModel::deleteCareTask
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleReviewSection(
    classes: List<WeeklyClassDto>,
    saving: Boolean,
    onUpdate: (Int, (WeeklyClassDto) -> WeeklyClassDto) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Revisa los bloques detectados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Corrige los nombres, días u horas si la IA leyó mal alguna celda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        classes.forEachIndexed { index, block ->
            OutlinedCard(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bloque ${index + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(index) }) { Icon(Icons.Filled.Delete, contentDescription = "Quitar bloque") }
                    }
                    OutlinedTextField(
                        value = block.courseName,
                        onValueChange = { value -> onUpdate(index) { it.copy(courseName = value) } },
                        label = { Text("Curso") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = block.courseCode,
                            onValueChange = { value -> onUpdate(index) { it.copy(courseCode = value) } },
                            label = { Text("Código") }, singleLine = true, modifier = Modifier.weight(1f)
                        )
                        DaySelector(block.dayOfWeek, Modifier.weight(1.2f)) { value -> onUpdate(index) { it.copy(dayOfWeek = value) } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = block.startTime, onValueChange = { value -> onUpdate(index) { it.copy(startTime = value) } }, label = { Text("Inicio HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = block.endTime, onValueChange = { value -> onUpdate(index) { it.copy(endTime = value) } }, label = { Text("Fin HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    if (block.section.isNotBlank()) OutlinedTextField(value = block.section, onValueChange = { value -> onUpdate(index) { it.copy(section = value) } }, label = { Text("Sección") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Agregar bloque de clase")
        }
        Button(onClick = onConfirm, enabled = classes.isNotEmpty() && !saving, modifier = Modifier.fillMaxWidth()) {
            if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Confirmar horario y eliminar PDF")
        }
        TextButton(onClick = onCancel, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Cancelar importación") }
    }
}

@Composable
private fun WeeklyClassesSection(classes: List<WeeklyClassDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Clases oficiales", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        for (day in 1..7) {
            val blocks = classes.filter { it.dayOfWeek == day }.sortedBy { it.startTime }
            if (blocks.isEmpty()) continue
            Text(weekDays[day], style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            blocks.forEach { block ->
                OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${block.startTime}\n${block.endTime}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(block.courseName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(listOf(block.courseCode, block.section).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarePlanSection(
    savedTasks: List<CarePlanTaskDto>,
    draftTasks: List<CarePlanTaskDto>?,
    classes: List<WeeklyClassDto>,
    isGenerating: Boolean,
    isSaving: Boolean,
    onGenerate: () -> Unit,
    onUpdateDraft: (Int, (CarePlanTaskDto) -> CarePlanTaskDto) -> Unit,
    onRemoveDraft: (Int) -> Unit,
    onConfirm: () -> Unit,
    onReschedule: (CarePlanTaskDto) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Text("Plan de cuidados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("La IA usa la ficha de tus plantas y busca momentos sin clases. Las entregas de Canvas se mantienen aparte.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (draftTasks != null) {
            if (draftTasks.isEmpty()) Text("No hay acciones sugeridas para revisar.", style = MaterialTheme.typography.bodyMedium)
            draftTasks.forEachIndexed { index, task ->
                CareTaskEditor(task, classes, onUpdate = { onUpdateDraft(index, it) }, onDelete = { onRemoveDraft(index) })
            }
            Button(onClick = onConfirm, enabled = draftTasks.isNotEmpty() && draftTasks.none { hasConflict(it.dayOfWeek, it.startTime, it.durationMinutes, classes) } && !isSaving, modifier = Modifier.fillMaxWidth()) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Confirmar plan de cuidados")
            }
        } else {
            if (savedTasks.isEmpty()) Text("Aún no hay un plan confirmado.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            savedTasks.groupBy { it.dayOfWeek }.toSortedMap().forEach { (day, tasks) ->
                Text(weekDays.getOrElse(day) { "Día" }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                tasks.forEach { task ->
                    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${task.startTime} · ${task.plantName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { onDelete(task.id) }) { Text("Quitar") }
                            }
                            if (task.instructions.isNotBlank()) Text(task.instructions, style = MaterialTheme.typography.bodySmall)
                            if (hasConflict(task, classes)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text("Cruce con una clase", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { onReschedule(task) }) { Text("Reprogramar") }
                                }
                            }
                        }
                    }
                }
            }
        }
        FilledTonalButton(onClick = onGenerate, enabled = !isGenerating && !isSaving, modifier = Modifier.fillMaxWidth()) {
            if (isGenerating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (savedTasks.isEmpty()) "Generar propuesta con IA" else "Regenerar propuesta con IA")
        }
    }
}

@Composable
private fun CareTaskEditor(
    task: CarePlanTaskDto,
    classes: List<WeeklyClassDto>,
    onUpdate: ((CarePlanTaskDto) -> CarePlanTaskDto) -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.plantName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Quitar cuidado") }
            }
            OutlinedTextField(task.title, { value -> onUpdate { it.copy(title = value) } }, label = { Text("Acción") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(task.instructions, { value -> onUpdate { it.copy(instructions = value) } }, label = { Text("Indicaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DaySelector(task.dayOfWeek, Modifier.weight(1.2f)) { day -> onUpdate { it.copy(dayOfWeek = day) } }
                OutlinedTextField(task.startTime, { value -> onUpdate { it.copy(startTime = value) } }, label = { Text("Hora HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(task.durationMinutes.toString(), { value -> onUpdate { it.copy(durationMinutes = value.filter(Char::isDigit).toIntOrNull() ?: 0) } }, label = { Text("Min") }, modifier = Modifier.weight(0.7f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (hasConflict(task.dayOfWeek, task.startTime, task.durationMinutes, classes)) {
                Text("Este cuidado se cruza con una clase. Cambia el día o la hora para guardar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DaySelector(dayOfWeek: Int, modifier: Modifier = Modifier, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(weekDays.getOrElse(dayOfWeek) { "Día" })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..7).forEach { day ->
                DropdownMenuItem(text = { Text(weekDays[day]) }, onClick = { onSelected(day); expanded = false })
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        )
    ) { Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
}

private fun hasConflict(task: CarePlanTaskDto, classes: List<WeeklyClassDto>) =
    hasConflict(task.dayOfWeek, task.startTime, task.durationMinutes, classes)

private fun hasConflict(day: Int, time: String, duration: Int, classes: List<WeeklyClassDto>): Boolean {
    return try {
        val start = LocalTime.parse(time).toSecondOfDay() / 60
        val end = start + duration
        classes.any { block ->
            if (block.dayOfWeek != day) false else {
                val classStart = LocalTime.parse(block.startTime).toSecondOfDay() / 60
                val classEnd = LocalTime.parse(block.endTime).toSecondOfDay() / 60
                start < classEnd && end > classStart
            }
        }
    } catch (_: Exception) { false }
}
