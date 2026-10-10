package com.agronomia.ui.result

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.agronomia.domain.model.PlantResult
import com.agronomia.domain.model.PlantSection
import com.agronomia.domain.model.WordMeaning
import com.agronomia.ui.capture.CaptureViewModel
import com.agronomia.ui.capture.IdentificationUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: CaptureViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val imageUri by viewModel.imageUri.collectAsState()

    // Descarta la imagen y vuelve a la pantalla de captura.
    val takeAnotherPhoto: () -> Unit = {
        viewModel.clearImage()
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agronom-IA - Resultado") }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            when (val state = uiState) {
                IdentificationUiState.Idle -> {
                    Text(
                        text = "Elige una imagen y pulsa Identificar.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onNavigateBack) { Text("Volver a captura") }
                }

                IdentificationUiState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Identificando la planta...\nPuede tardar unos segundos.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }

                is IdentificationUiState.Success ->
                    SuccessContent(
                        plant = state.plant,
                        imageUri = imageUri,
                        onTakeAnotherPhoto = takeAnotherPhoto
                    )

                is IdentificationUiState.Error -> MessageContent(
                    title = "No se pudo identificar la planta",
                    message = state.message,
                    onRetry = { viewModel.identifyCurrentImage() },
                    onTakeAnotherPhoto = takeAnotherPhoto
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(
    plant: PlantResult,
    imageUri: Uri?,
    onTakeAnotherPhoto: () -> Unit
) {
    if (imageUri != null) {
        AsyncImage(
            model = imageUri,
            contentDescription = "Foto de la planta identificada",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    Text(
        text = plant.commonName.ifBlank {
            plant.scientificName.ifBlank { "Planta identificada" }
        },
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )

    if (plant.scientificName.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = plant.scientificName,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center
        )
    }

    if (plant.family.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Familia: ${plant.family}",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Confianza: ${(plant.confidence * 100).roundToInt()}%",
        style = MaterialTheme.typography.bodyMedium
    )

    // Palabra seleccionada del glosario: se muestra su significado en contexto.
    var selectedTerm by remember { mutableStateOf<WordMeaning?>(null) }

    plant.sections.forEach { section ->
        SectionCard(
            section = section,
            onTermClick = { selectedTerm = it }
        )
    }

    selectedTerm?.let { term ->
        AlertDialog(
            onDismissRequest = { selectedTerm = null },
            title = { Text(term.word) },
            text = { Text(term.meaning) },
            confirmButton = {
                TextButton(onClick = { selectedTerm = null }) {
                    Text("Entendido")
                }
            }
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onTakeAnotherPhoto,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Tomar otra foto")
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/**
 * Bloque de una categoría en tarjeta delineada, con las palabras difíciles
 * resaltadas: al tocarlas se abre su significado en contexto.
 * No se muestra si el cuerpo está vacío.
 */
@Composable
private fun SectionCard(
    section: PlantSection,
    onTermClick: (WordMeaning) -> Unit
) {
    if (section.body.isBlank()) return

    Spacer(modifier = Modifier.height(12.dp))
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = section.title.ifBlank { section.key },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            val highlightStyle = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            )
            val annotated = remember(section.body, section.terms) {
                buildAnnotatedText(section.body, section.terms, highlightStyle)
            }
            ClickableText(
                text = annotated,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                onClick = { offset ->
                    annotated.getStringAnnotations("term", offset, offset)
                        .firstOrNull()?.let { annotation ->
                            section.terms.find { it.word == annotation.item }
                                ?.let(onTermClick)
                        }
                }
            )
            if (section.terms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Toca una palabra resaltada para ver su significado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Resalta en el texto las palabras del glosario (insensible a mayúsculas,
 * con límites de palabra y sin traslapes). Cada resaltado lleva una anotación
 * con la palabra para abrir su significado al tocarla.
 */
private fun buildAnnotatedText(
    body: String,
    terms: List<WordMeaning>,
    highlight: SpanStyle
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    builder.append(body)
    if (terms.isEmpty()) return builder.toAnnotatedString()

    val lowerBody = body.lowercase()
    val used = mutableListOf<IntRange>()
    // Términos largos primero para que no los tape un sub-término más corto.
    terms.sortedByDescending { it.word.length }.forEach { term ->
        val word = term.word.trim()
        if (word.length < 3) return@forEach
        val lowerWord = word.lowercase()
        var from = 0
        while (true) {
            val found = lowerBody.indexOf(lowerWord, from)
            if (found < 0) break
            val end = found + word.length
            val beforeOk = found == 0 || !lowerBody[found - 1].isLetterOrDigit()
            val afterOk = end >= lowerBody.length || !lowerBody[end].isLetterOrDigit()
            val overlaps = used.any { it.first < end && found < it.last }
            if (beforeOk && afterOk && !overlaps) {
                builder.addStyle(highlight, found, end)
                builder.addStringAnnotation("term", word, found, end)
                used.add(found until end)
            }
            from = end
        }
    }
    return builder.toAnnotatedString()
}

@Composable
private fun MessageContent(
    title: String,
    message: String,
    onTakeAnotherPhoto: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))

    if (onRetry != null) {
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reintentar")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    OutlinedButton(
        onClick = onTakeAnotherPhoto,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Tomar otra foto")
    }
}
