package com.correa.agronom_ia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.graphics.ImageDecoder
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

data class PlantPhoto(val id: String, val name: String, val file: File, val preview: Bitmap?, val status: String)
data class PlantPhotoState(
    val photos: List<PlantPhoto> = emptyList(),
    val busy: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val message: String? = null,
    val uploaded: Boolean = false,
    val batchId: String? = null,
    val analysis: PlantAnalysis? = null
)
class PlantPhotoViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(PlantPhotoState())
    val state: StateFlow<PlantPhotoState> = mutableState.asStateFlow()
    private var cameraFile: File? = null

    fun addUris(context: Context, uris: List<Uri>) {
        if (mutableState.value.busy) return
        if (uris.isEmpty()) return
        val remaining = MAX_PHOTOS - mutableState.value.photos.size
        if (remaining <= 0) {
            mutableState.value = mutableState.value.copy(error = "El máximo es de cinco imágenes.")
            return
        }
        val acceptedUris = uris.take(remaining)
        val excess = uris.size - acceptedUris.size
        mutableState.value = mutableState.value.copy(busy = true, progress = "Validando imágenes…", error = if (excess > 0) "Solo se admiten cinco imágenes. Se omitieron $excess." else null)
        viewModelScope.launch {
            try {
                for (uri in acceptedUris) {
                    try {
                        val photo = withContext(Dispatchers.IO) { validateAndStore(context, uri) }
                        mutableState.value = mutableState.value.copy(photos = mutableState.value.photos + photo, uploaded = false, batchId = null, analysis = null)
                    } catch (e: Exception) {
                        mutableState.value = mutableState.value.copy(error = e.message ?: "No se pudo leer una de las imágenes.")
                    }
                }
            } finally {
                mutableState.value = mutableState.value.copy(busy = false, progress = null)
            }
        }
    }

    fun prepareCamera(context: Context, launcher: ActivityResultLauncher<Uri>) {
        try {
            val directory = File(context.cacheDir, "camera").apply { mkdirs() }
            cameraFile = File(directory, "capture-${UUID.randomUUID()}.jpg")
            context.getSharedPreferences("camera_capture", Context.MODE_PRIVATE).edit()
                .putString("pending_path", cameraFile!!.absolutePath).apply()
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile!!)
            launcher.launch(uri)
        } catch (_: Exception) {
            context.getSharedPreferences("camera_capture", Context.MODE_PRIVATE).edit().remove("pending_path").apply()
            mutableState.value = mutableState.value.copy(error = "No se pudo iniciar la cámara. Revisa que haya una aplicación de cámara disponible.")
        }
    }

    fun onCameraResult(context: Context, saved: Boolean) {
        val preferences = context.getSharedPreferences("camera_capture", Context.MODE_PRIVATE)
        val file = cameraFile ?: preferences.getString("pending_path", null)?.let(::File) ?: return
        cameraFile = null
        preferences.edit().remove("pending_path").apply()
        if (saved && file.exists() && file.length() > 0) addUris(context, listOf(Uri.fromFile(file)))
        else {
            file.delete()
            mutableState.value = mutableState.value.copy(error = if (saved) "La cámara devolvió una imagen vacía." else "La captura se canceló o la cámara no concedió acceso.")
        }
    }

    fun remove(id: String) {
        val photo = mutableState.value.photos.firstOrNull { it.id == id } ?: return
        photo.file.delete()
        mutableState.value = mutableState.value.copy(photos = mutableState.value.photos.filterNot { it.id == id }, uploaded = false, batchId = null, analysis = null, error = null)
    }

    fun upload() {
        val photos = mutableState.value.photos
        if (photos.isEmpty()) {
            mutableState.value = mutableState.value.copy(error = "Selecciona al menos una imagen.")
            return
        }
        if (photos.size > MAX_PHOTOS) {
            mutableState.value = mutableState.value.copy(error = "El máximo es de cinco imágenes.")
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, progress = "Enviando ${photos.size} ${if (photos.size == 1) "imagen" else "imágenes"}…", error = null, uploaded = false, batchId = null, analysis = null)
            try {
                val result = withContext(Dispatchers.IO) { PlantUploadApi.upload(photos.map { it.file }) }
                check(result.count == photos.size) { "El servidor no confirmó todas las imágenes; no se marcó el lote como guardado." }
                mutableState.value = mutableState.value.copy(busy = false, progress = null, uploaded = true, batchId = result.batchId, analysis = null, photos = photos.map { it.copy(status = "Guardada en servidor y MySQL") }, message = "${result.count} ${if (result.count == 1) "imagen guardada" else "imágenes guardadas"} correctamente en el servidor y registradas en MySQL.")
            } catch (e: Exception) {
                val message = when (e) {
                    is UnknownHostException, is ConnectException -> "No se pudo conectar con el servidor local. Comprueba que Apache esté activo y que la dirección de la API sea correcta."
                    is SocketTimeoutException -> "El servidor tardó demasiado en responder. Comprueba la red local e inténtalo de nuevo."
                    else -> e.message ?: "No se pudieron guardar las imágenes."
                }
                mutableState.value = mutableState.value.copy(busy = false, progress = null, photos = photos.map { it.copy(status = "No guardada") }, error = message)
            }
        }
    }

    fun analyze() {
        val photos = mutableState.value.photos
        if (photos.isEmpty()) {
            mutableState.value = mutableState.value.copy(error = "Selecciona al menos una imagen para identificar la planta.")
            return
        }
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, progress = "Preparando identificación…", error = null, analysis = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    PlantAnalysisApi.analyze(photos.map { it.file }) { stage ->
                        mutableState.value = mutableState.value.copy(progress = stage)
                    }
                }
                mutableState.value = mutableState.value.copy(busy = false, progress = null, analysis = result)
            } catch (e: Exception) {
                val message = when (e) {
                    is UnknownHostException, is ConnectException -> "No hay conexión con Pl@ntNet o Space Bunny. Comprueba Internet e inténtalo de nuevo."
                    is SocketTimeoutException -> "El servicio tardó demasiado en responder. Comprueba Internet e inténtalo de nuevo."
                    else -> e.message ?: "No se pudo analizar la planta."
                }
                mutableState.value = mutableState.value.copy(busy = false, progress = null, error = message)
            }
        }
    }

    fun clearMessage() { mutableState.value = mutableState.value.copy(message = null) }

    fun reportError(message: String) { mutableState.value = mutableState.value.copy(error = message) }

    private fun validateAndStore(context: Context, uri: Uri): PlantPhoto {
        val id = UUID.randomUUID().toString()
        val incoming = File(context.cacheDir, "incoming-$id")
        try {
            val input = if (uri.scheme == "file") {
                uri.path?.let(::File)?.inputStream() ?: throw IllegalArgumentException("No se pudo acceder al archivo capturado.")
            } else {
                context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("No se pudo acceder al archivo seleccionado.")
            }
            input.use { source -> FileOutputStream(incoming).use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_INPUT_BYTES) { "La imagen supera el límite de 15 MB." }
                    output.write(buffer, 0, count)
                }
            } }
            require(incoming.length() > 0) { "El archivo está vacío." }
            val bitmap = decodeBounded(incoming)
            val output = File(context.filesDir, "images").apply { mkdirs() }
            val normalized = File(output, "$id.jpg")
            FileOutputStream(normalized).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) { "No se pudo preparar la imagen." }
            }
            val factor = minOf(1f, 480f / maxOf(bitmap.width, bitmap.height))
            val thumbnail = Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt().coerceAtLeast(1), (bitmap.height * factor).toInt().coerceAtLeast(1), true)
            if (thumbnail !== bitmap) bitmap.recycle()
            val name = "${id.take(8)}.jpg"
            return PlantPhoto(id, name, normalized, thumbnail, "Validada · JPEG")
        } finally { incoming.delete() }
    }

    private fun decodeBounded(file: File): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(file)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) { "Las dimensiones superan el límite permitido (12 000 px)." }
                val scale = minOf(1.0, MAX_DECODE_DIMENSION.toDouble() / maxOf(width, height))
                decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            require(bitmap.width >= MIN_DIMENSION && bitmap.height >= MIN_DIMENSION) { "La imagen tiene dimensiones demasiado pequeñas." }
            return bitmap
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "El archivo no contiene una imagen válida o el formato no es compatible." }
        require(bounds.outWidth <= MAX_DIMENSION && bounds.outHeight <= MAX_DIMENSION) { "Las dimensiones superan el límite permitido (12 000 px)." }
        require(bounds.outWidth >= MIN_DIMENSION && bounds.outHeight >= MIN_DIMENSION) { "La imagen tiene dimensiones demasiado pequeñas." }
        val sample = Integer.highestOneBit(maxOf(bounds.outWidth, bounds.outHeight) / MAX_DECODE_DIMENSION).coerceAtLeast(1)
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
            ?: throw IllegalArgumentException("No se pudo decodificar la imagen. El formato puede no ser compatible.")
    }

    companion object {
        const val MAX_PHOTOS = 5
        private const val MAX_INPUT_BYTES = 15L * 1024 * 1024
        private const val MAX_DIMENSION = 12_000
        private const val MAX_DECODE_DIMENSION = 2048
        private const val MIN_DIMENSION = 16
    }
}

private object PlantUploadApi {
    data class UploadResult(val count: Int, val batchId: String)

    fun upload(files: List<File>): UploadResult {
        val boundary = "Plant-${UUID.randomUUID()}"
        val endpoint = "${BuildConfig.PLANT_API_BASE_URL.trimEnd('/')}/api/images.php"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.buffered().use { out ->
                files.forEach { file ->
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"photos[]\"; filename=\"${file.name}\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
                    file.inputStream().buffered().use { it.copyTo(out, 32 * 1024) }
                    out.write("\r\n".toByteArray())
                }
                out.write("--$boundary--\r\n".toByteArray())
            }
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (responseCode !in 200..299 || json?.optBoolean("success") != true) {
                throw IllegalStateException(json?.optString("error")?.takeIf { it.isNotBlank() && it != "null" } ?: "El servidor no pudo guardar las imágenes (HTTP $responseCode).")
            }
            val count = json.optJSONArray("images")?.length() ?: throw IllegalStateException("La respuesta del servidor no confirmó las imágenes guardadas.")
            val batchId = json.optString("batch_id").takeIf { it.isNotBlank() && it != "null" }
                ?: throw IllegalStateException("El servidor guardó el lote, pero no devolvió su identificador.")
            return UploadResult(count, batchId)
        } finally { connection.disconnect() }
    }

}
