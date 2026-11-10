package com.agronomia.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.agronomia.data.remote.IdentifiedPlantDto
import com.agronomia.ui.components.BackendImage
import com.agronomia.ui.components.MarkdownText

/** Conversaciones persistentes organizadas por planta, con fotos para documentar su progreso. */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var draft by remember { mutableStateOf("") }
    var selectedPhoto by remember { mutableStateOf<Uri?>(null) }
    val selectedPlant = state.plants.firstOrNull { it.id == state.selectedPlantId }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedPhoto = uri }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.busy, state.loadingHistory) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tu espacio botánico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = selectedPlant?.let { "Conversación sobre ${it.commonName.ifBlank { it.scientificName }}" }
                                ?: "Elige una planta para continuar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✿", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "MIS PLANTAS",
                    modifier = Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                when {
                    state.loadingPlants && state.plants.isEmpty() -> Text(
                        "Cargando tus plantas…",
                        modifier = Modifier.padding(horizontal = 18.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.plants.isEmpty() -> Text(
                        "Identifica una planta en el Escáner para iniciar su conversación.",
                        modifier = Modifier.padding(horizontal = 18.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.plants, key = { it.id }) { plant ->
                            PlantChatChoice(
                                plant = plant,
                                selected = plant.id == state.selectedPlantId,
                                onClick = {
                                    selectedPhoto = null
                                    viewModel.selectPlant(plant.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.loadingHistory) item { Text("Abriendo conversación…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else if (state.messages.isEmpty()) item { WelcomeMessage(selectedPlant) }
            items(state.messages, key = { it.id }) { message -> ChatBubble(message) }
            if (state.busy) item { TypingIndicator() }
            state.error?.let { problem ->
                item { Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                selectedPhoto?.let { uri ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        BackendImage(
                            imageRef = uri.toString(),
                            contentDescription = "Foto de progreso seleccionada",
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Text("Foto lista para compartir", modifier = Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { selectedPhoto = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar foto")
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    IconButton(
                        enabled = selectedPlant != null && !state.busy,
                        onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Adjuntar foto de progreso", tint = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (selectedPlant == null) "Elige una planta…" else "Pregunta o comparte su progreso…") },
                        enabled = selectedPlant != null && !state.loadingHistory && !state.busy,
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        enabled = selectedPlant != null && !state.busy && (draft.isNotBlank() || selectedPhoto != null),
                        onClick = {
                            viewModel.send(draft, selectedPhoto)
                            draft = ""
                            selectedPhoto = null
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlantChatChoice(plant: IdentifiedPlantDto, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(164.dp),
        shape = RoundedCornerShape(18.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Column {
            if (plant.imageUrl.isNotBlank()) {
                BackendImage(
                    imageRef = plant.imageUrl,
                    contentDescription = "Foto de ${plant.commonName.ifBlank { plant.scientificName }}",
                    modifier = Modifier.fillMaxWidth().height(96.dp)
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().height(96.dp).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text("✿", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(plant.commonName.ifBlank { plant.scientificName }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(plant.scientificName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WelcomeMessage(plant: IdentifiedPlantDto?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(if (plant == null) "Tu jardín empieza aquí" else "¿Cómo va ${plant.commonName.ifBlank { plant.scientificName }}?", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (plant == null) "Selecciona una planta identificada o usa el Escáner para agregar una nueva."
            else "Pregunta sobre sus cuidados o adjunta una foto para registrar su progreso.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessageUi) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier.widthIn(max = 320.dp).clip(
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (message.isUser) 18.dp else 4.dp, bottomEnd = if (message.isUser) 4.dp else 18.dp)
            ).background(if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            message.imageRef?.let { ref ->
                BackendImage(ref, "Foto compartida en la conversación", Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)))
            }
            if (message.text.isNotBlank()) {
                if (message.isUser) Text(message.text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium)
                else MarkdownText(message.text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text("Agronom-IA está pensando…", modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
    }
}
