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
data class ChatMessage(
    val id: String,
    val role: String,
    val text: String,
    val photoNames: List<String> = emptyList(),
    val analysis: PlantAnalysis? = null
)
data class PlantPhotoState(
    val photos: List<PlantPhoto> = emptyList(),
    val busy: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val message: String? = null,
    val uploaded: Boolean = false,
    val batchId: String? = null,
    val analysis: PlantAnalysis? = null,
    val photosPending: Boolean = false,
    val conversation: List<ChatMessage> = emptyList()
)
class PlantPhotoViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(PlantPhotoState())
    val state: StateFlow<PlantPhotoState> = mutableState.asStateFlow()
    private var cameraFile: File? = null
    private var conversationLoaded = false

    fun restoreConversation(context: Context) {
        if (conversationLoaded) return
        conversationLoaded = true
        val raw = context.getSharedPreferences(CHAT_PREFERENCES, Context.MODE_PRIVATE).getString(CHAT_KEY, null) ?: return
        val messages = runCatching {
            val array = org.json.JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) array.optJSONObject(index)?.let { add(it.toChatMessage()) }
            }
        }.getOrDefault(emptyList())
        mutableState.value = mutableState.value.copy(
            conversation = messages.takeLast(MAX_HISTORY_MESSAGES),
            analysis = messages.lastOrNull { it.analysis != null }?.analysis
        )
    }

    fun startNewConversation(context: Context) {
        mutableState.value.photos.forEach { it.file.delete() }
        conversationLoaded = true
        mutableState.value = PlantPhotoState()
        persistConversation(context, emptyList())
    }

    fun sendMessage(context: Context, rawQuestion: String) {
        val current = mutableState.value
        if (current.busy) return
        val photos = current.photos.takeIf { current.photosPending && current.analysis?.identification == null }.orEmpty()
        val question = rawQuestion.trim().ifBlank {
            if (photos.isNotEmpty()) "Identifica esta planta y dame una guía breve de cuidados." else ""
        }
        if (question.isBlank()) {
            mutableState.value = current.copy(error = "Escribe una pregunta o adjunta una foto para continuar.")
            return
        }
        if (photos.size > MAX_PHOTOS) {
            mutableState.value = current.copy(error = "El máximo es de cinco imágenes por análisis.")
            return
        }
        val userMessage = ChatMessage(UUID.randomUUID().toString(), ROLE_USER, question, photos.map { it.name })
        val conversation = (current.conversation + userMessage).takeLast(MAX_HISTORY_MESSAGES)
        mutableState.value = current.copy(conversation = conversation, busy = true, progress = if (photos.isEmpty()) "Enviando pregunta…" else "Preparando identificación…", error = null)
        persistConversation(context, conversation)
        viewModelScope.launch { runAssistantTurn(context, userMessage, photos) }
    }

    fun retryLastMessage(context: Context) {
        val current = mutableState.value
        if (current.busy) return
        val latestUserMessage = current.conversation.lastOrNull { it.role == ROLE_USER } ?: return
        mutableState.value = current.copy(busy = true, error = null, progress = "Reintentando…")
        val photos = current.photos.takeIf { current.photosPending && current.analysis?.identification == null }.orEmpty()
        viewModelScope.launch { runAssistantTurn(context, latestUserMessage, photos) }
    }

    private suspend fun runAssistantTurn(context: Context, userMessage: ChatMessage, photos: List<PlantPhoto>) {
        try {
            var identification = mutableState.value.analysis?.identification
            if (photos.isNotEmpty() && identification == null) {
                identification = withContext(Dispatchers.IO) {
                    PlantAnalysisApi.identify(photos.map { it.file }) { stage ->
                        mutableState.value = mutableState.value.copy(progress = stage)
                    }
                }
                mutableState.value = mutableState.value.copy(analysis = PlantAnalysis(identification))
            }
            val history = mutableState.value.conversation
                .dropLastWhile { it.id != userMessage.id }
                .dropLast(1)
                .takeLast(12)
                .map { SpaceBunnyTurn(it.role, it.text) }
            val skillInstructions = withContext(Dispatchers.IO) { SpaceBunnySkillRepository.load(context) }
            val reply = withContext(Dispatchers.IO) {
                PlantAnalysisApi.generateReply(skillInstructions, identification, history, userMessage.text) { stage ->
                    mutableState.value = mutableState.value.copy(progress = stage)
                }
            }
            val analysis = identification?.let { PlantAnalysis(it, reply) }
            val assistantMessage = ChatMessage(UUID.randomUUID().toString(), ROLE_ASSISTANT, reply, analysis = analysis)
            val conversation = (mutableState.value.conversation + assistantMessage).takeLast(MAX_HISTORY_MESSAGES)
            mutableState.value = mutableState.value.copy(
                conversation = conversation,
                analysis = analysis ?: mutableState.value.analysis,
                photos = mutableState.value.photos,
                photosPending = false,
                uploaded = false,
                batchId = null,
                busy = false,
                progress = null,
                error = null
            )
            persistConversation(context, conversation)
        } catch (error: Exception) {
            val message = when (error) {
                is UnknownHostException, is ConnectException -> "No se pudo conectar con Pl@ntNet u OpenCode. Comprueba Internet y que OpenCode esté activo."
                is SocketTimeoutException -> "El servicio tardó demasiado en responder. Puedes reintentar."
                else -> error.message ?: "No se pudo completar la respuesta."
            }
            mutableState.value = mutableState.value.copy(busy = false, progress = null, error = message)
        }
    }

    fun addUris(context: Context, uris: List<Uri>) {
        if (mutableState.value.busy) return
        if (uris.isEmpty()) return
        val existingPhotos = mutableState.value.photos.takeIf { mutableState.value.photosPending }.orEmpty()
        if (!mutableState.value.photosPending) mutableState.value.photos.forEach { it.file.delete() }
        val remaining = MAX_PHOTOS - existingPhotos.size
        if (remaining <= 0) {
            mutableState.value = mutableState.value.copy(error = "El máximo es de cinco imágenes.")
            return
        }
        val acceptedUris = uris.take(remaining)
        val excess = uris.size - acceptedUris.size
        mutableState.value = mutableState.value.copy(photos = existingPhotos, photosPending = true, busy = true, progress = "Validando imágenes…", error = if (excess > 0) "Solo se admiten cinco imágenes. Se omitieron $excess." else null)
        viewModelScope.launch {
            try {
                for (uri in acceptedUris) {
                    try {
                        val photo = withContext(Dispatchers.IO) { validateAndStore(context, uri) }
                        mutableState.value = mutableState.value.copy(photos = mutableState.value.photos + photo, photosPending = true, uploaded = false, batchId = null, analysis = null)
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
        val current = mutableState.value
        val remaining = current.photos.filterNot { it.id == id }
        mutableState.value = current.copy(photos = remaining, photosPending = current.photosPending && remaining.isNotEmpty(), uploaded = false, batchId = null, analysis = if (current.photosPending) null else current.analysis, error = null)
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
            mutableState.value = mutableState.value.copy(busy = true, progress = "Enviando ${photos.size} ${if (photos.size == 1) "imagen" else "imágenes"}…", error = null, uploaded = false, batchId = null)
            try {
                val result = withContext(Dispatchers.IO) { PlantUploadApi.upload(photos.map { it.file }) }
                check(result.count == photos.size) { "El servidor no confirmó todas las imágenes; no se marcó el lote como guardado." }
                mutableState.value = mutableState.value.copy(busy = false, progress = null, uploaded = true, batchId = result.batchId, photos = photos.map { it.copy(status = "Guardada en servidor y MySQL") }, message = "${result.count} ${if (result.count == 1) "imagen guardada" else "imágenes guardadas"} correctamente en el servidor y registradas en MySQL.")
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

    fun clearMessage() { mutableState.value = mutableState.value.copy(message = null) }

    fun reportError(message: String) { mutableState.value = mutableState.value.copy(error = message) }

    private fun persistConversation(context: Context, messages: List<ChatMessage>) {
        val json = org.json.JSONArray().apply {
            messages.takeLast(MAX_HISTORY_MESSAGES).forEach { message ->
                put(JSONObject().put("id", message.id).put("role", message.role).put("text", message.text)
                    .put("photos", org.json.JSONArray(message.photoNames))
                    .put("analysis", message.analysis?.toJson()))
            }
        }
        context.getSharedPreferences(CHAT_PREFERENCES, Context.MODE_PRIVATE).edit().putString(CHAT_KEY, json.toString()).apply()
    }

    private fun JSONObject.toChatMessage(): ChatMessage {
        val photos = optJSONArray("photos") ?: org.json.JSONArray()
        val analysis = optJSONObject("analysis")?.toPlantAnalysis()
        return ChatMessage(optString("id", UUID.randomUUID().toString()), optString("role"), optString("text"),
            (0 until photos.length()).map { photos.optString(it) }, analysis)
    }

    private fun PlantAnalysis.toJson(): JSONObject = JSONObject()
        .put("scientificName", identification.scientificName)
        .put("family", identification.family)
        .put("commonName", identification.commonName)
        .put("score", identification.score)
        .put("alternatives", org.json.JSONArray(identification.alternatives))
        .put("uncertain", identification.uncertain)
        .put("care", care)

    private fun JSONObject.toPlantAnalysis(): PlantAnalysis {
        val alternatives = optJSONArray("alternatives") ?: org.json.JSONArray()
        return PlantAnalysis(
            PlantIdentification(optString("scientificName"), optString("family").takeIf { it != "null" },
                optString("commonName").takeIf { it != "null" }, optDouble("score"),
                (0 until alternatives.length()).map { alternatives.optString(it) }, optBoolean("uncertain")),
            optString("care").takeIf { it.isNotBlank() && it != "null" }
        )
    }

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
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val MAX_PHOTOS = 5
        private const val CHAT_PREFERENCES = "plant_chat_history"
        private const val CHAT_KEY = "messages"
        private const val MAX_HISTORY_MESSAGES = 60
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
