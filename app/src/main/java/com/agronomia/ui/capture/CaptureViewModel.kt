package com.agronomia.ui.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CaptureViewModel : ViewModel() {

    private val _imageUri = MutableStateFlow<Uri?>(null)
    val imageUri: StateFlow<Uri?> = _imageUri.asStateFlow()

    fun onImageSelected(uri: Uri?) {
        if (uri != null) {
            _imageUri.value = uri
        }
    }

    fun clearImage() {
        _imageUri.value = null
    }
}
