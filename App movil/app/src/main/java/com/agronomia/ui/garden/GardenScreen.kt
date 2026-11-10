package com.agronomia.ui.garden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agronomia.util.Constants
import com.agronomia.data.remote.ChatProgressImageDto
import com.agronomia.data.remote.IdentifiedPlantDto
import com.agronomia.data.remote.NetworkModule
import com.agronomia.ui.chat.ChatViewModel
import com.agronomia.ui.components.AgronomIaTopBar
import com.agronomia.ui.components.BackendImage
import com.agronomia.ui.components.FullscreenImageDialog

/** Galería de fotos de progreso, separada por el chat de cada planta. */
@Composable
fun GardenScreen(
    viewModel: ChatViewModel,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(state.plants) {
        state.plants.forEach { viewModel.loadGallery(it.id) }
    }

    Scaffold(
        topBar = { AgronomIaTopBar() },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mi jardín", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("El progreso de cada planta, reunido desde su conversación.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.loadingPlants && state.plants.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (state.plants.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Todavía no tienes plantas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Identifica una planta y su espacio de seguimiento aparecerá aquí.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(state.plants, key = { "garden_${it.id}" }) { plant ->
                    GardenPlantCard(
                        plant = plant,
                        images = state.progressImagesByPlant[plant.id].orEmpty(),
                        loading = plant.id in state.loadingGalleryPlantIds,
                        onRetry = { viewModel.loadGallery(plant.id, force = true) },
                        onOpenChat = { onOpenChat(plant.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GardenPlantCard(
    plant: IdentifiedPlantDto,
    images: List<ChatProgressImageDto>,
    loading: Boolean,
    onRetry: () -> Unit,
    onOpenChat: () -> Unit
) {
    var expandedImage by remember { mutableStateOf<String?>(null) }
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plant.commonName.ifBlank { plant.scientificName }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(plant.scientificName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onOpenChat, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Ver chat")
                }
            }
            Text("FOTOS DE PROGRESO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            when {
                loading -> Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                images.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aún no hay fotos compartidas en este chat.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, contentDescription = "Actualizar galería") }
                }
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(images, key = { it.id }) { image ->
                        Column(Modifier.width(132.dp)) {
                            BackendImage(
                                imageRef = image.imageUrl,
                                contentDescription = "Foto de progreso de ${plant.commonName}",
                                modifier = Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(14.dp))
                                    .clickable { expandedImage = image.imageUrl },
                                contentScale = ContentScale.Crop
                            )
                            Text(image.createdAt.take(10), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
                            if (image.caption.isNotBlank()) Text(image.caption, maxLines = 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    expandedImage?.let { image ->
        FullscreenImageDialog(
            Constants.backendUrl(image),
            "Foto de progreso",
            onDismiss = { expandedImage = null },
            authorizationToken = NetworkModule.currentUserToken()
        )
    }
}
