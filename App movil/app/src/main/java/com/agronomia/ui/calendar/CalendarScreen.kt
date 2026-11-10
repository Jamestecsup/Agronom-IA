package com.agronomia.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agronomia.data.canvas.CanvasAuthState
import com.agronomia.domain.canvas.CanvasCalendarItem
import com.agronomia.domain.canvas.CanvasItemType
import com.agronomia.ui.components.AgronomIaTopBar
import com.agronomia.ui.theme.LocalAccessibilityState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val accessibilityState = LocalAccessibilityState.current
    val highContrast = accessibilityState.highContrast

    Scaffold(
        topBar = { AgronomIaTopBar() },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabecera principal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (highContrast) Color(0xFF002855)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        )
                        .then(
                            if (highContrast) Modifier.border(2.dp, Color.Black, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = if (highContrast) Color(0xFFFFEA00) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Calendario de Cursos",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (uiState.authState.isConnected) {
                            "Sincronizado con Canvas LMS"
                        } else {
                            "Vincula tu cuenta de Canvas para ver tus clases y tareas"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.authState.isConnected) {
                    IconButton(
                        onClick = { viewModel.loadEvents() },
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Sincronizar",
                                tint = if (highContrast) Color(0xFF002855) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (!uiState.authState.isConnected) {
                // Formulario de vinculación con Canvas
                CanvasConnectForm(
                    isConnecting = uiState.isConnecting,
                    errorMessage = uiState.connectError,
                    highContrast = highContrast,
                    onConnect = { domain, token ->
                        viewModel.connectCanvas(domain, token)
                    }
                )
            } else {
                // Vista del calendario sincronizado
                CanvasConnectedHeader(
                    userName = uiState.authState.userName,
                    userEmail = uiState.authState.userEmail,
                    domain = uiState.authState.domain,
                    highContrast = highContrast,
                    onDisconnect = { viewModel.disconnect() }
                )

                if (uiState.loadError != null) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = uiState.loadError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Selector de días (franja horizontal centrada en hoy)
                DateStripSelector(
                    selectedDateIso = uiState.selectedDateIso,
                    datesWithEvents = uiState.datesWithEvents,
                    highContrast = highContrast,
                    onSelectDate = { dateIso -> viewModel.selectDate(dateIso) }
                )

                // Actividades del día seleccionado
                SelectedDateActivitiesSection(
                    dateIso = uiState.selectedDateIso,
                    events = uiState.eventsForSelectedDate,
                    highContrast = highContrast
                )

                // Próximas entregas destacadas
                if (uiState.upcomingEvents.isNotEmpty()) {
                    UpcomingEventsSection(
                        events = uiState.upcomingEvents,
                        highContrast = highContrast
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasConnectForm(
    isConnecting: Boolean,
    errorMessage: String?,
    highContrast: Boolean,
    onConnect: (String, String) -> Unit
) {
    var domain by remember { mutableStateOf(CanvasAuthState.DEFAULT_CANVAS_DOMAIN) }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (highContrast) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (highContrast) 2.dp else 1.dp,
            color = if (highContrast) Color(0xFF000000) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Vincular Canvas de Tecsup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Instrucciones paso a paso
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (highContrast) Color(0xFFE4ECF7)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .border(
                        width = if (highContrast) 1.5.dp else 0.dp,
                        color = if (highContrast) Color(0xFF000000) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "¿Cómo obtener tu token en Canvas?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (highContrast) Color(0xFF002855) else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1. Entra a tu Canvas (tecsup.instructure.com) desde el navegador.\n" +
                               "2. Ve a Cuenta > Configuraciones.\n" +
                               "3. Baja hasta 'Tokens de acceso aprobados' y pulsa '+ Nuevo token de acceso'.\n" +
                               "4. Dale un nombre (ej: 'AgronomIA') y copia el código generado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Campo de Dominio Canvas
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("URL de Canvas") },
                placeholder = { Text("https://tecsup.instructure.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Campo de Token
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Token de acceso de Canvas") },
                placeholder = { Text("Pega tu token aquí (ej. 20967~...)") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showToken = !showToken }) {
                            Text(if (showToken) "Ocultar" else "Ver", fontSize = 12.sp)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Botón rápido para pegar desde portapapeles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            token = clip.trim()
                        }
                    }
                ) {
                    Text("Pegar desde portapapeles", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = { onConnect(domain, token) },
                enabled = !isConnecting && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = if (highContrast) {
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF002855),
                        contentColor = Color(0xFFFFEA00)
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verificando token...")
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vincular y Sincronizar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CanvasConnectedHeader(
    userName: String,
    userEmail: String,
    domain: String,
    highContrast: Boolean,
    onDisconnect: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (highContrast) Color(0xFFE4ECF7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            width = if (highContrast) 2.dp else 1.dp,
            color = if (highContrast) Color(0xFF000000) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32))
                    )
                    Text(
                        text = "Conectado a Canvas Tecsup",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (highContrast) Color(0xFF002855) else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = userName.ifBlank { "Estudiante Tecsup" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (userEmail.isNotBlank()) {
                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(onClick = onDisconnect) {
                Text(
                    text = "Desvincular",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DateStripSelector(
    selectedDateIso: String,
    datesWithEvents: Set<String>,
    highContrast: Boolean,
    onSelectDate: (String) -> Unit
) {
    val today = remember { LocalDate.now() }
    // Franja de 35 días (1 semana atrás y 4 semanas hacia adelante)
    val days = remember {
        (-7..28).map { offset -> today.plusDays(offset.toLong()) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Seleccionar Día",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Puntos indican entregas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(days) { date ->
                val dateIso = date.toString()
                val isSelected = dateIso == selectedDateIso
                val hasEvents = datesWithEvents.contains(dateIso)
                val isToday = date == today

                val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es", "PE"))
                    .replaceFirstChar { it.uppercase() }
                    .take(3)
                val dayOfMonth = date.dayOfMonth.toString()

                val containerBg = when {
                    isSelected && highContrast -> Color(0xFF002855)
                    isSelected && !highContrast -> MaterialTheme.colorScheme.primary
                    !isSelected && highContrast -> Color(0xFFFFFFFF)
                    else -> MaterialTheme.colorScheme.surface
                }

                val contentColor = when {
                    isSelected && highContrast -> Color(0xFFFFFFFF)
                    isSelected && !highContrast -> MaterialTheme.colorScheme.onPrimary
                    !isSelected && highContrast -> Color(0xFF000000)
                    else -> MaterialTheme.colorScheme.onSurface
                }

                val borderStroke = when {
                    highContrast -> BorderStroke(2.dp, Color(0xFF000000))
                    isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    isToday -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                    else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                }

                OutlinedCard(
                    modifier = Modifier
                        .width(58.dp)
                        .clickable { onSelectDate(dateIso) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = containerBg),
                    border = borderStroke
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = dayOfWeek,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dayOfMonth,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Indicador de actividad
                        if (hasEvents) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected && highContrast) Color(0xFFFFEA00)
                                        else if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else if (highContrast) Color(0xFF002855)
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                        } else {
                            Spacer(modifier = Modifier.size(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDateActivitiesSection(
    dateIso: String,
    events: List<CanvasCalendarItem>,
    highContrast: Boolean
) {
    val formattedHeader = remember(dateIso) {
        try {
            val parsed = LocalDate.parse(dateIso)
            val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale("es", "PE"))
            parsed.format(formatter).replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            dateIso
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formattedHeader,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${events.size} actividad(es)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (events.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (highContrast) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (highContrast) 1.5.dp else 1.dp,
                    color = if (highContrast) Color(0xFF000000) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Sin actividades programadas",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "No hay entregas de tareas ni evaluaciones en Canvas para este día.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                events.forEach { item ->
                    CanvasEventCard(item = item, highContrast = highContrast)
                }
            }
        }
    }
}

@Composable
private fun UpcomingEventsSection(
    events: List<CanvasCalendarItem>,
    highContrast: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = if (highContrast) Color(0xFF002855) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Próximas entregas más cercanas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            events.forEach { item ->
                CanvasEventCard(item = item, highContrast = highContrast)
            }
        }
    }
}

@Composable
private fun CanvasEventCard(
    item: CanvasCalendarItem,
    highContrast: Boolean
) {
    val uriHandler = LocalUriHandler.current

    val badgeColor = when (item.type) {
        CanvasItemType.ASSIGNMENT -> Color(0xFF1976D2)
        CanvasItemType.QUIZ -> Color(0xFFE65100)
        CanvasItemType.EVENT -> Color(0xFF2E7D32)
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (highContrast) Color(0xFFFFFFFF) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (highContrast) 2.dp else 1.dp,
            color = if (highContrast) Color(0xFF000000) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Etiqueta del tipo (Tarea, Examen, Evento)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (highContrast) Color(0xFF002855) else badgeColor.copy(alpha = 0.14f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.type.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (highContrast) Color(0xFFFFEA00) else badgeColor
                    )
                }

                if (item.points != null) {
                    Text(
                        text = item.points,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (highContrast) Color(0xFF000000) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Título de la tarea / evaluación
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Nombre del curso
            Text(
                text = item.courseName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                thickness = if (highContrast) 1.5.dp else 0.5.dp,
                color = if (highContrast) Color(0xFF000000) else MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Vence: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.fullFormattedDate,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isPast) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }

                if (!item.htmlUrl.isNullOrBlank()) {
                    Text(
                        text = "Abrir en Canvas ↗",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (highContrast) Color(0xFF002855) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            try {
                                uriHandler.openUri(item.htmlUrl)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }
}
