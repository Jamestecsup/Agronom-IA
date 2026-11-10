package com.agronomia.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import okhttp3.Headers
import com.agronomia.data.remote.NetworkModule
import com.agronomia.util.Constants

/** Carga fotos privadas del usuario usando el token de su sesión Django. */
@Composable
fun BackendImage(
    imageRef: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val backendUrlPrefix = remember { Constants.BACKEND_BASE_URL.trimEnd('/') + "/" }
    val imageUrl = remember(imageRef) {
        when {
            imageRef.startsWith("http://") || imageRef.startsWith("https://") ||
                imageRef.startsWith("content://") || imageRef.startsWith("file://") -> imageRef
            else -> Constants.backendUrl(imageRef)
        }
    }
    val token = NetworkModule.currentUserToken()
    val request = remember(imageUrl, token) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .apply {
                if (!token.isNullOrBlank() && imageUrl.startsWith(backendUrlPrefix)) {
                    headers(Headers.Builder().add("Authorization", "Bearer $token").build())
                }
            }
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("🌿", style = MaterialTheme.typography.headlineMedium)
            }
        }
    )
}
