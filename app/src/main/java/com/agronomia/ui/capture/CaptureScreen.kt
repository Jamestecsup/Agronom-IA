package com.agronomia.ui.capture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.agronomia.ui.components.AgronomIaTopBar
import com.agronomia.ui.components.FullscreenImageDialog
import com.agronomia.ui.theme.LocalAccessibilityState
import com.agronomia.util.Constants
import com.agronomia.util.ImageUtils
import java.io.File

private fun createCameraImageUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File.createTempFile("capture_", ".jpg", imagesDir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onNavigateToResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageUris by viewModel.imageUris.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val organs by viewModel.organs.collectAsState()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraRationale by remember { mutableStateOf(false) }
    var selectedNavIndex by remember { mutableIntStateOf(0) }

    /** Agrega fotos avisando si se topó el máximo. */
    fun addUrisWithFeedback(picked: List<Uri>) {
        if (picked.isEmpty()) return
        val added = viewModel.onImagesAdded(picked)
        if (added < picked.size) {
            val message = if (added == 0) {
                "Límite de ${ImageUtils.MAX_IMAGES} fotos alcanzado. Quita alguna para agregar más."
            } else {
                "Se agregaron $added de ${picked.size} fotos (máximo ${ImageUtils.MAX_IMAGES})."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Galería múltiple: hasta MAX_IMAGES por vez (se acumulan con la cámara).
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(ImageUtils.MAX_IMAGES)
    ) { uris -> addUrisWithFeedback(uris) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            if (ImageUtils.uriHasContent(context, uri)) {
                if (viewModel.onCameraPhotoTaken(uri) == 0) {
                    Toast.makeText(
                        context,
                        "Límite de ${ImageUtils.MAX_IMAGES} fotos alcanzado. Quita alguna para agregar más.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // La cámara (virtual) del emulador dejó el archivo en 0 bytes:
                // abrimos la galería para elegir imágenes reales.
                Toast.makeText(
                    context,
                    "La foto salió vacía en este emulador. Elige de la galería.",
                    Toast.LENGTH_LONG
                ).show()
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createCameraImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            Toast.makeText(
                context,
                "Sin permiso de cámara no se pueden tomar fotos. Puedes usar la Galería.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun launchCamera() {
        if (imageUris.size >= ImageUtils.MAX_IMAGES) {
            Toast.makeText(
                context,
                "Límite de ${ImageUtils.MAX_IMAGES} fotos alcanzado. Quita alguna para agregar más.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val uri = createCameraImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            // Si ya lo negó antes, explicar para qué se usa antes de reintentar.
            val activity = context as? Activity
            val needsRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            if (needsRationale) {
                showCameraRationale = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Explicación del permiso de cámara (solo si el usuario ya lo negó antes).
    if (showCameraRationale) {
        AlertDialog(
            onDismissRequest = { showCameraRationale = false },
            title = { Text("Permiso de cámara") },
            text = {
                Text(
                    "Agronom-IA usa la cámara para fotografiar la planta a identificar " +
                        "(ideal: flor, hoja y tallo). Sin este permiso solo podrás " +
                        "elegir imágenes de la galería."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCameraRationale = false
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Permitir") }
            },
            dismissButton = {
                TextButton(onClick = { showCameraRationale = false }) { Text("Ahora no") }
            }
        )
    }

    Scaffold(
        topBar = {
            AgronomIaTopBar()
        },
        // La barra superior ya aplica el inset del sistema y el footer global
        // reserva el inferior: no volver a restarlos aquí.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Acción principal: barra de acción (no es el footer de navegación).
            // El Scaffold reserva su espacio, así no tapa las tarjetas.
            if (imageUris.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            viewModel.identifyCurrentImage()
                            onNavigateToResult()
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = {
                            if (uiState is IdentificationUiState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Outlined.Search, contentDescription = null)
                            }
                        },
                        text = {
                            Text(
                                when {
                                    uiState is IdentificationUiState.Loading -> "Identificando…"
                                    imageUris.size > 1 -> "Identificar (${imageUris.size} fotos)"
                                    else -> "Identificar"
                                }
                            )
                        }
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Foto ampliada a pantalla completa al tocarla.
            var expandedImage by remember { mutableStateOf<Uri?>(null) }

            // Vista previa con Coil: la primera foto en grande + tarjetas por foto.
            val firstUri = imageUris.firstOrNull()
            if (firstUri != null) {
                AsyncImage(
                    model = firstUri,
                    contentDescription = "Foto seleccionada de la planta (toca para ampliar)",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { expandedImage = firstUri }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Foto 1 de ${imageUris.size} (máx. ${ImageUtils.MAX_IMAGES}). " +
                        "Ideal: flor, hoja y tallo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "¿Qué es cada foto? Elige su órgano para identificar mejor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                imageUris.forEachIndexed { index, uri ->
                    PhotoOrganCard(
                        index = index,
                        uri = uri,
                        organ = organs[uri.toString()] ?: "auto",
                        onOrganChange = { viewModel.setOrgan(uri, it) },
                        onRemove = { viewModel.removeImage(uri) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                val highContrast = LocalAccessibilityState.current.highContrast
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (highContrast) {
                                Modifier
                                    .background(Color(0xFFE4ECF7))
                                    .border(2.dp, Color(0xFF000000), RoundedCornerShape(16.dp))
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = if (highContrast) Color(0xFF002855) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sin fotos. Usa Galería o Cámara " +
                                "(hasta ${ImageUtils.MAX_IMAGES}: flor, hoja y tallo).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val highContrast = LocalAccessibilityState.current.highContrast
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    border = if (highContrast) BorderStroke(2.dp, Color(0xFF000000)) else ButtonDefaults.outlinedButtonBorder(true),
                    colors = if (highContrast) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF002855),
                            contentColor = Color(0xFFFFFFFF)
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(
                        text = "Galería",
                        fontWeight = if (highContrast) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }
                OutlinedButton(
                    onClick = ::launchCamera,
                    modifier = Modifier.weight(1f),
                    border = if (highContrast) BorderStroke(2.dp, Color(0xFF000000)) else ButtonDefaults.outlinedButtonBorder(true),
                    colors = if (highContrast) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF002855),
                            contentColor = Color(0xFFFFFFFF)
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(
                        text = "Cámara",
                        fontWeight = if (highContrast) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }
            }

            if (imageUris.isNotEmpty()) {
                TextButton(onClick = viewModel::clearImages) {
                    Text(
                        text = "Quitar todas",
                        fontWeight = if (highContrast) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (highContrast) Color(0xFF9B0000) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            expandedImage?.let { uri ->
                FullscreenImageDialog(
                    image = uri,
                    contentDescription = "Foto ampliada",
                    onDismiss = { expandedImage = null }
                )
            }
        }
    }
}

/**
 * Tarjeta de UNA foto seleccionada: miniatura, botón para quitarla, chips del
 * órgano fotografiado y descripción breve de la opción elegida.
 */
@Composable
private fun PhotoOrganCard(
    index: Int,
    uri: Uri,
    organ: String,
    onOrganChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    val highContrast = LocalAccessibilityState.current.highContrast
    var expandedImage by remember { mutableStateOf(false) }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = if (highContrast) BorderStroke(2.dp, Color(0xFF000000)) else androidx.compose.material3.CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Foto ${index + 1} (toca para ampliar)",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expandedImage = true }
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Foto ${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRemove) {
                        Text(
                            text = "Quitar",
                            fontWeight = if (highContrast) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (highContrast) Color(0xFF9B0000) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        Constants.ORGAN_OPTIONS,
                        key = { it.first }
                    ) { (value, label) ->
                        val isSelected = organ == value
                        FilterChip(
                            selected = isSelected,
                            onClick = { onOrganChange(value) },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (highContrast || isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = if (highContrast) {
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF002855),
                                    selectedLabelColor = Color(0xFFFFFFFF),
                                    containerColor = Color(0xFFFFFFFF),
                                    labelColor = Color(0xFF000000)
                                )
                            } else {
                                FilterChipDefaults.filterChipColors()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Constants.ORGAN_DESCRIPTIONS[organ]
                        ?: Constants.ORGAN_DESCRIPTIONS["auto"].orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (expandedImage) {
        FullscreenImageDialog(
            image = uri,
            contentDescription = "Foto ${index + 1} ampliada",
            onDismiss = { expandedImage = false }
        )
    }
}
