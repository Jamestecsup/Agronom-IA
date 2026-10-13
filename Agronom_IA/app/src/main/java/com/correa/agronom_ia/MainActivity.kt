package com.correa.agronom_ia

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.correa.agronom_ia.ui.theme.Agronom_IATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Agronom_IATheme { PlantPhotoScreen() } }
    }
}

@Composable
private fun PlantPhotoScreen(model: PlantPhotoViewModel = viewModel()) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingNetworkAction by remember { mutableStateOf<String?>(null) }
    val localNetworkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingNetworkAction
        pendingNetworkAction = null
        if (granted && action != null) {
            if (action == "analyze") model.analyze() else model.upload()
        } else {
            if (action != null) model.reportError("Se necesita permiso de red local para conectar con los servicios locales de desarrollo.")
        }
    }
    fun runWithLocalNetworkPermission(action: String) {
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNetworkAction = action
            localNetworkPermission.launch(permission)
        } else {
            if (action == "analyze") model.analyze() else model.upload()
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> -> model.addUris(context, uris) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved -> model.onCameraResult(context, saved) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); model.clearMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("HERBARIO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Identifica tus plantas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("Selecciona fotos claras de una misma planta. Puedes agregar hasta cinco imágenes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Elegir fotos") }
                    Button(onClick = { model.prepareCamera(context, camera) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Tomar foto") }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Imágenes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${state.photos.size} de ${PlantPhotoViewModel.MAX_PHOTOS}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.photos.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Aún no hay imágenes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text("Selecciona al menos una imagen para continuar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        items(state.photos, key = { it.id }) { photo ->
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.width(156.dp)) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (photo.preview != null) Image(photo.preview.asImageBitmap(), contentDescription = "Vista previa de ${photo.name}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(11.dp)))
                                    else Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp) }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(photo.name, maxLines = 1, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { model.remove(photo.id) }, enabled = !state.busy) { Text("Quitar") }
                                    }
                                    Text(photo.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (state.error != null) item { Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (state.busy) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(state.progress ?: "Validando imágenes…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                OutlinedButton(onClick = { runWithLocalNetworkPermission("analyze") }, enabled = state.photos.isNotEmpty() && !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (state.analysis?.care == null && state.analysis != null) "Reintentar guía de cuidados" else "Identificar y consultar cuidados")
                }
            }
            item {
                Button(onClick = { runWithLocalNetworkPermission("upload") }, enabled = state.photos.isNotEmpty() && !state.busy && !state.uploaded, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (state.uploaded) "Guardadas correctamente" else "Guardar imágenes")
                }
            }
            state.analysis?.let { analysis -> item { AnalysisCard(analysis) } }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@Composable
private fun AnalysisCard(analysis: PlantAnalysis) {
    val identification = analysis.identification
    val care = analysis.care
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Identificación · Pl@ntNet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(identification.scientificName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            identification.commonName?.let { AnalysisDetail("Nombre común informado", it) }
            identification.family?.let { AnalysisDetail("Familia", it) }
            AnalysisDetail("Coincidencia del candidato", identification.scorePercent)
            if (identification.uncertain) {
                Text("La identificación es incierta. El porcentaje ordena candidatos y no garantiza que la especie sea correcta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text("El porcentaje es una puntuación de coincidencia, no una garantía de identificación.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (identification.alternatives.isNotEmpty()) AnalysisDetail("Otros candidatos", identification.alternatives.joinToString())
            Text("Cuidados · Space Bunny", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (care == null) {
                Text("La identificación está disponible. La guía de cuidados está pendiente; puedes reintentar la consulta.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AnalysisDetail("Riego", care.watering)
                AnalysisDetail("Iluminación", care.lighting)
                AnalysisDetail("Toxicidad para perros y gatos", care.petToxicity)
                if (care.warnings.isNotBlank()) AnalysisDetail("Advertencias", care.warnings)
                Text("Confirma la toxicidad con una fuente veterinaria fiable antes de permitir que una mascota consuma la planta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnalysisDetail(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
