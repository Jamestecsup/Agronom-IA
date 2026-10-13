package com.correa.agronom_ia

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(onNewConversation: () -> Unit, onSaveImages: () -> Unit, canSaveImages: Boolean) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Agronom-IA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Asistente botánico · Space Bunny", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        actions = {
            Box {
                TextButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "Acciones de conversación" }) {
                    Text("Más")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Nueva conversación") }, onClick = { menuOpen = false; onNewConversation() })
                    DropdownMenuItem(
                        text = { Text("Guardar imágenes en XAMPP/MySQL") },
                        enabled = canSaveImages,
                        onClick = { menuOpen = false; onSaveImages() }
                    )
                }
            }
        }
    )
}

@Composable
internal fun ConversationList(
    messages: List<ChatMessage>,
    busy: Boolean,
    modifierPadding: PaddingValues,
    onSuggestion: (String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, busy) {
        val lastIndex = messages.size + if (busy) 1 else 0
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(modifierPadding),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (messages.isEmpty()) item { WelcomePanel(onSuggestion) }
        items(messages, key = { it.id }) { message -> ConversationBubble(message) }
        if (busy) item { AssistantTyping() }
    }
}

@Composable
private fun WelcomePanel(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("A", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Medium)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("¿Qué planta te gustaría cuidar?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Adjunta una foto para identificarla o pregúntame sobre sus cuidados.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onSuggestion("¿Cómo puedo saber cuándo regar una planta de interior?") }, modifier = Modifier.fillMaxWidth()) {
                Text("Cómo saber cuándo regar")
            }
            OutlinedButton(onClick = { onSuggestion("¿Qué información necesitas para ayudarme a cuidar una planta?") }, modifier = Modifier.fillMaxWidth()) {
                Text("Consejos para empezar")
            }
        }
    }
}

@Composable
private fun ConversationBubble(message: ChatMessage) {
    val isUser = message.role == PlantPhotoViewModel.ROLE_USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 360.dp),
            shape = RoundedCornerShape(if (isUser) 22.dp else 18.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            tonalElevation = if (isUser) 0.dp else 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isUser) "Tú" else "Space Bunny",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
                if (message.photoNames.isNotEmpty()) {
                    Surface(color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${message.photoNames.size} ${if (message.photoNames.size == 1) "imagen adjunta" else "imágenes adjuntas"}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                            message.photoNames.forEach { name -> Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                        }
                    }
                }
                Text(
                    text = messageAnnotatedString(message.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                message.analysis?.let { PlantNetSummary(it.identification) }
            }
        }
    }
}

@Composable
private fun messageAnnotatedString(markdown: String): AnnotatedString {
    val headingFontSize = MaterialTheme.typography.titleMedium.fontSize
    return buildAnnotatedString {
        val lines = markdown.lines()
        lines.forEachIndexed { lineIndex, rawLine ->
            val heading = rawLine.trimStart().startsWith("## ")
            val content = when {
                heading -> rawLine.trimStart().removePrefix("## ")
                rawLine.startsWith("- ") || rawLine.startsWith("* ") -> "• ${rawLine.drop(2)}"
                else -> rawLine
            }
            val start = length
            val inline = Regex("(\\*\\*[^*]+\\*\\*|\\*[^*]+\\*|`[^`]+`)")
            var cursor = 0
            inline.findAll(content).forEach { match ->
                append(content.substring(cursor, match.range.first))
                val token = match.value
                val style = when {
                    token.startsWith("**") -> SpanStyle(fontWeight = FontWeight.Bold)
                    token.startsWith("*") -> SpanStyle(fontStyle = FontStyle.Italic)
                    else -> SpanStyle(fontWeight = FontWeight.Medium)
                }
                val unwrapped = when {
                    token.startsWith("**") -> token.removePrefix("**").removeSuffix("**")
                    token.startsWith("*") || token.startsWith("`") -> token.drop(1).dropLast(1)
                    else -> token
                }
                pushStyle(style)
                append(unwrapped)
                pop()
                cursor = match.range.last + 1
            }
            append(content.substring(cursor))
            if (heading) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = headingFontSize), start, length)
            if (lineIndex < lines.lastIndex) append("\n")
        }
    }
}

@Composable
private fun PlantNetSummary(identification: PlantIdentification) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Identificación de Pl@ntNet", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(identification.commonName ?: identification.scientificName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (identification.commonName != null) Text(identification.scientificName, style = MaterialTheme.typography.bodyMedium)
            identification.family?.let { Text("Familia: $it", style = MaterialTheme.typography.bodyMedium) }
            Text("Coincidencia: ${identification.scorePercent}", style = MaterialTheme.typography.bodyMedium)
            if (identification.uncertain) {
                Text("Identificación incierta; la puntuación no garantiza la especie.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (identification.alternatives.isNotEmpty()) {
                Text("Alternativas: ${identification.alternatives.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AssistantTyping() {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Space Bunny está pensando…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ChatComposer(
    photos: List<PlantPhoto>,
    busy: Boolean,
    progress: String?,
    error: String?,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onSend: (String) -> Unit,
    onRetry: () -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (photos.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    items(photos, key = { it.id }) { photo ->
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.width(84.dp)) {
                            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                photo.preview?.let {
                                    Image(it.asImageBitmap(), contentDescription = "Vista previa de ${photo.name}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(8.dp)))
                                }
                                Text(photo.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                TextButton(onClick = { onRemovePhoto(photo.id) }, enabled = !busy, contentPadding = PaddingValues(0.dp), modifier = Modifier.height(32.dp)) { Text("Quitar") }
                            }
                        }
                    }
                }
            }
            if (busy && progress != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (error != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, enabled = !busy) { Text("Reintentar") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onGallery, enabled = !busy && photos.size < PlantPhotoViewModel.MAX_PHOTOS, modifier = Modifier.semantics { contentDescription = "Adjuntar fotos" }) { Text("Fotos") }
                TextButton(onClick = onCamera, enabled = !busy && photos.size < PlantPhotoViewModel.MAX_PHOTOS, modifier = Modifier.semantics { contentDescription = "Tomar fotografía" }) { Text("Cámara") }
                Spacer(Modifier.width(2.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MAX_DRAFT_CHARS) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Pregunta sobre plantas…", maxLines = 1) },
                    enabled = !busy,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!busy && (draft.isNotBlank() || photos.isNotEmpty())) { onSend(draft); draft = "" } })
                )
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { onSend(draft); draft = "" },
                    enabled = !busy && (draft.isNotBlank() || photos.isNotEmpty()),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.semantics { contentDescription = "Enviar mensaje" }
                ) { Text("Enviar") }
            }
        }
    }
}

private const val MAX_DRAFT_CHARS = 2_000
