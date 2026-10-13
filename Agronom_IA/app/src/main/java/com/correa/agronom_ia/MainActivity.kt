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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
import com.correa.agronom_ia.ui.theme.Agronom_IATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Agronom_IATheme { AgronomChatRoute() } }
    }
}

@Composable
private fun AgronomChatRoute(model: PlantPhotoViewModel = viewModel()) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) { model.restoreConversation(context) }
    val localNetworkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke()
        else if (action != null) model.reportError("Se necesita permiso de red local para conectar con OpenCode en este dispositivo.")
    }
    fun withLocalNetworkPermission(action: () -> Unit) {
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            pendingAction = action
            localNetworkPermission.launch(permission)
        } else action()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> -> model.addUris(context, uris) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved -> model.onCameraResult(context, saved) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); model.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopBar(
                onNewConversation = { model.startNewConversation(context) },
                onSaveImages = { withLocalNetworkPermission(model::upload) },
                canSaveImages = state.photos.isNotEmpty() && !state.busy && !state.uploaded
            )
        },
        bottomBar = {
            ChatComposer(
                photos = if (state.photosPending) state.photos else emptyList(),
                busy = state.busy,
                progress = state.progress,
                error = state.error,
                onGallery = { gallery.launch("image/*") },
                onCamera = { model.prepareCamera(context, camera) },
                onRemovePhoto = model::remove,
                onSend = { text -> withLocalNetworkPermission { model.sendMessage(context, text) } },
                onRetry = { withLocalNetworkPermission { model.retryLastMessage(context) } }
            )
        }
    ) { padding ->
        ConversationList(
            messages = state.conversation,
            busy = state.busy,
            modifierPadding = padding,
            onSuggestion = { question -> withLocalNetworkPermission { model.sendMessage(context, question) } }
        )
    }
}
