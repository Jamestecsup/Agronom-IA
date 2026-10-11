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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onNavigateToResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageUris by viewModel.imageUris.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraRationale by remember { mutableStateOf(false) }

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
            TopAppBar(
                title = { Text("Agronom-IA - Captura") }
            )
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
            // Vista previa con Coil: la primera foto en grande + miniaturas.
            val firstUri = imageUris.firstOrNull()
            if (firstUri != null) {
                AsyncImage(
                    model = firstUri,
                    contentDescription = "Foto seleccionada de la planta",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Foto 1 de ${imageUris.size} (máx. ${ImageUtils.MAX_IMAGES}). " +
                        "Ideal: flor, hoja y tallo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(imageUris, key = { it.toString() }) { uri ->
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Miniatura",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { viewModel.removeImage(uri) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(26.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.55f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Quitar foto",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Galería")
                }
                OutlinedButton(
                    onClick = ::launchCamera,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cámara")
                }
            }

            if (imageUris.isNotEmpty()) {
                TextButton(onClick = viewModel::clearImages) {
                    Text("Quitar todas")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    // Lanza el procesado + envío en el ViewModel y navega al resultado.
                    viewModel.identifyCurrentImage()
                    onNavigateToResult()
                },
                enabled = imageUris.isNotEmpty() && uiState !is IdentificationUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is IdentificationUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(if (imageUris.size > 1) "Identificar (${imageUris.size} fotos)" else "Identificar")
            }
        }
    }
}
