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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.agronomia.domain.model.PlantResult
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

    InfoSection("Descripción", plant.description)
    InfoSection("Luz", plant.light)
    InfoSection("Riego", plant.watering)
    InfoSection("Floración", plant.flowering)
    InfoSection("Usos", plant.uses)
    InfoSection("Cuidados", plant.care)

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onTakeAnotherPhoto,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Tomar otra foto")
    }
    Spacer(modifier = Modifier.height(8.dp))
}

/** Bloque de información (título + cuerpo). No se muestra si el cuerpo está vacío. */
@Composable
private fun InfoSection(title: String, body: String) {
    if (body.isBlank()) return

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth()
    )
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
