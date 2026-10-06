package com.agronomia.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageUtils {

    const val MAX_SIDE_PX = 1024
    const val JPEG_QUALITY = 80

    private val ALLOWED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    )

    private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /**
     * Convierte un [Uri] de imagen a Base64.
     *
     * - Valida que sea JPG, PNG o WEBP.
     * - Reduce el lado mayor a un máximo de 1024 px manteniendo la proporción.
     * - Recomprime a JPEG con calidad 80.
     * - Devuelve los bytes JPEG codificados en Base64 (sin saltos de línea).
     *
     * Se ejecuta en [Dispatchers.IO], fuera del hilo principal.
     *
     * @return [Resource.Success] con el String Base64, o [Resource.Error] con mensaje claro en español.
     */
    suspend fun uriToBase64Jpeg(context: Context, uri: Uri): Resource<String> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Validar formato por MIME type.
                val mimeType = context.contentResolver.getType(uri)?.lowercase()
                if (mimeType != null && mimeType !in ALLOWED_MIME_TYPES) {
                    return@withContext Resource.Error(
                        "Formato no válido ($mimeType). Solo se permiten imágenes JPG, PNG o WEBP."
                    )
                }
                // Si el ContentResolver no devuelve MIME, validar por extensión como respaldo.
                if (mimeType == null) {
                    val extension = getExtension(context, uri)
                    if (extension != null && extension !in ALLOWED_EXTENSIONS) {
                        return@withContext Resource.Error(
                            "Formato no válido (.$extension). Solo se permiten imágenes JPG, PNG o WEBP."
                        )
                    }
                }

                // 2. Leer dimensiones sin cargar el bitmap en memoria.
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, boundsOptions)
                    } ?: return@withContext Resource.Error(
                        "No se pudo abrir la imagen. Verifica que el archivo exista y vuelve a intentarlo."
                    )
                } catch (e: SecurityException) {
                    return@withContext Resource.Error(
                        "Sin permiso para leer la imagen. Otorga acceso y vuelve a intentarlo.",
                        e
                    )
                }

                if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                    return@withContext Resource.Error(
                        "No se pudo decodificar la imagen. El archivo podría estar dañado o no ser una imagen válida."
                    )
                }

                // 3. Calcular inSampleSize para no cargar un bitmap gigante en memoria.
                val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                boundsOptions.inSampleSize = calculateInSampleSize(maxSide)

                // 4. Decodificar bitmap real.
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = boundsOptions.inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                var bitmap: Bitmap? = try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, decodeOptions)
                    }
                } catch (e: SecurityException) {
                    return@withContext Resource.Error(
                        "Sin permiso para leer la imagen. Otorga acceso y vuelve a intentarlo.",
                        e
                    )
                }

                if (bitmap == null) {
                    return@withContext Resource.Error(
                        "No se pudo decodificar la imagen. El archivo podría estar dañado o no ser JPG, PNG ni WEBP."
                    )
                }

                try {
                    // 5. Escalar a máximo 1024 px de lado manteniendo proporción.
                    val scaled = scaleDown(bitmap, MAX_SIDE_PX)
                    if (scaled !== bitmap && bitmap.isRecycled.not()) {
                        bitmap.recycle()
                    }
                    bitmap = scaled

                    // 6. Comprimir a JPEG calidad 80.
                    val output = ByteArrayOutputStream()
                    val compressed = bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        JPEG_QUALITY,
                        output
                    )
                    if (!compressed) {
                        return@withContext Resource.Error(
                            "Error al comprimir la imagen a JPEG. Intenta con otra imagen."
                        )
                    }
                    val bytes = output.toByteArray()
                    if (bytes.isEmpty()) {
                        return@withContext Resource.Error(
                            "Error al comprimir la imagen: se generaron 0 bytes. Intenta con otra imagen."
                        )
                    }

                    // 7. Codificar a Base64.
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    Resource.Success(base64)
                } finally {
                    // Evitar fugas de memoria nativa del Bitmap.
                    if (bitmap?.isRecycled == false) {
                        bitmap.recycle()
                    }
                }
            } catch (e: OutOfMemoryError) {
                Resource.Error(
                    "La imagen es demasiado grande para procesarla en este dispositivo. Prueba con una más pequeña.",
                    e
                )
            } catch (e: Exception) {
                Resource.Error(
                    "Error inesperado al procesar la imagen: ${e.message ?: "intenta con otra imagen"}.",
                    e
                )
            }
        }

    private fun calculateInSampleSize(maxSide: Int): Int {
        var inSampleSize = 1
        var halfMax = maxSide / 2
        // Reducir a potencia de 2 hasta que el lado mayor estimado quepa cerca de MAX_SIDE_PX.
        while (halfMax / inSampleSize >= MAX_SIDE_PX) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap

        val longest = maxOf(width, height)
        if (longest <= maxSide) return bitmap

        val ratio = maxSide / longest.toFloat()
        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun getExtension(context: Context, uri: Uri): String? {
        // Intentar por nombre de archivo / path; si no hay, null (se deja pasar al decode).
        val name = uri.lastPathSegment?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()?.trim()
        if (!name.isNullOrEmpty() && name.length <= 5) return name
        // Último recurso: extensión del display name vía ContentResolver (mejor esfuerzo).
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                        ?.substringAfterLast('.', missingDelimiterValue = "")
                        ?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
