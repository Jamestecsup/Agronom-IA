package com.agronomia.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageUtils {

    const val MAX_SIDE_PX = 1024
    const val JPEG_QUALITY = 80

    /**
     * Máximo de imágenes por identificación (límite de Pl@ntNet: hasta 5
     * fotos de la misma planta: flor, hoja, tallo...).
     */
    const val MAX_IMAGES = 5

    /**
     * Lado mayor mínimo aceptado: por debajo la foto es demasiado pequeña
     * para identificar con fiabilidad.
     */
    const val MIN_SIDE_PX = 128

    /**
     * Umbrales del chequeo de calidad previo (ver [qualityIssues]).
     * Conservadores: solo avisan en casos claros. Se calibraron con fotos
     * reales de prueba (girasol nítido ≈ 10× estos valores).
     */
    const val MIN_BRIGHTNESS = 35.0
    const val MIN_SHARPNESS = 60.0

    private val ALLOWED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp"
    )

    private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /**
     * Convierte un [Uri] de imagen a **bytes JPEG** listos para subir.
     *
     * - Valida que sea JPG, PNG o WEBP.
     * - Rechaza imágenes enanas (lado mayor menor que MIN_SIDE_PX).
     * - Corrige la orientación según la etiqueta EXIF (fotos de cámara rotadas).
     * - Reduce el lado mayor a un máximo de 1024 px manteniendo la proporción.
     * - Recomprime a JPEG con calidad 80.
     *
     * Se ejecuta en [Dispatchers.IO], fuera del hilo principal.
     *
     * @return [Resource.Success] con los bytes JPEG, o [Resource.Error] con mensaje claro en español.
     */
    suspend fun uriToJpegBytes(context: Context, uri: Uri): Resource<ByteArray> =
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

                // 2. Leer la orientación EXIF antes de decodificar.
                //    Las fotos de cámara suelen guardarse rotadas y con la orientación en EXIF.
                val orientation = readExifOrientation(context, uri)

                // 3. Leer dimensiones sin cargar el bitmap en memoria.
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                // Se usa un flag para distinguir "no se pudo abrir el flujo" de
                // "se abrió pero no se pudo decodificar" (cubren casos distintos).
                val streamOpened: Boolean = try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, boundsOptions)
                        true
                    } ?: false
                } catch (e: SecurityException) {
                    return@withContext Resource.Error(
                        "Sin permiso para leer la imagen. Otorga acceso y vuelve a intentarlo.",
                        e
                    )
                }
                if (!streamOpened) {
                    return@withContext Resource.Error(
                        "No se pudo abrir la imagen. Verifica que el archivo exista y vuelve a intentarlo."
                    )
                }

                if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                    // Si el archivo pesa 0 bytes (típico de la cámara virtual del emulador)
                    // o está dañado, avisar con claridad para que el usuario use otra vía.
                    val fileSize = try {
                        context.contentResolver.openFileDescriptor(uri, "r")
                            ?.use { fd -> fd.statSize } ?: -1L
                    } catch (_: Exception) {
                        -1L
                    }
                    return@withContext Resource.Error(
                        if (fileSize == 0L) {
                            "La imagen está vacía (0 bytes). En el emulador, la cámara virtual puede " +
                                "guardar fotos vacías; usa la Galería o toma otra foto."
                        } else {
                            "No se pudo decodificar la imagen. El archivo podría estar dañado o no ser " +
                                "una imagen válida."
                        }
                    )
                }

                // 3b. Validar tamaño mínimo: muy pequeña no sirve para identificar.
                if (maxOf(boundsOptions.outWidth, boundsOptions.outHeight) < MIN_SIDE_PX) {
                    return@withContext Resource.Error(
                        "La imagen es muy pequeña (menos de $MIN_SIDE_PX px). " +
                            "Usa una foto más grande o acércate a la planta."
                    )
                }

                // 4. Calcular inSampleSize para no cargar un bitmap gigante en memoria.
                val maxSide = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                boundsOptions.inSampleSize = calculateInSampleSize(maxSide)

                // 5. Decodificar bitmap real (de forma escalada).
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = boundsOptions.inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded: Bitmap? = try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, decodeOptions)
                    }
                } catch (e: SecurityException) {
                    return@withContext Resource.Error(
                        "Sin permiso para leer la imagen. Otorga acceso y vuelve a intentarlo.",
                        e
                    )
                }

                if (decoded == null) {
                    return@withContext Resource.Error(
                        "No se pudo decodificar la imagen. El archivo podría estar dañado o no ser JPG, PNG ni WEBP."
                    )
                }

                var bitmap: Bitmap = decoded
                try {
                    // 6. Corregir la orientación según EXIF.
                    val oriented = applyExifOrientation(bitmap, orientation)
                    if (oriented !== bitmap && !bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                    bitmap = oriented

                    // 7. Escalar a máximo 1024 px de lado manteniendo proporción.
                    val scaled = scaleDown(bitmap, MAX_SIDE_PX)
                    if (scaled !== bitmap && !bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                    bitmap = scaled

                    // 8. Comprimir a JPEG calidad 80.
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

                    // 9. Devolver los bytes JPEG (Pl@ntNet los sube en multipart).
                    Resource.Success(bytes)
                } finally {
                    // Evitar fugas de memoria nativa del Bitmap.
                    if (!bitmap.isRecycled) {
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

    /**
     * Codifica bytes JPEG a Base64 sin saltos de línea.
     * Se usa para el respaldo con visión de Gemini (imagen como data URL).
     */
    fun toBase64Jpeg(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /**
     * Indica si el archivo del [uri] tiene contenido (más de 0 bytes).
     * La cámara virtual del emulador tiende a dejar el archivo de salida en
     * 0 bytes; con esto se detecta rápido sin abrir el PDF/bitmap completo.
     */
    fun uriHasContent(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openFileDescriptor(uri, "r")
            ?.use { descriptor -> descriptor.statSize > 0 }
            ?: false
    } catch (_: Exception) {
        false
    }

    /**
     * Calidad de una foto ya preparada: brillo medio (0-255) y nitidez
     * (varianza del laplaciano; mayor = más nítida). Valores negativos
     * indican que no se pudo analizar.
     */
    data class PhotoQuality(val brightness: Double, val sharpness: Double)

    /**
     * Analiza la calidad con una versión pequeña (~192 px) para ir rápido.
     * No bloquea: si algo falla devuelve valores negativos.
     */
    fun analyzeQuality(imageBytes: ByteArray): PhotoQuality {
        if (imageBytes.isEmpty()) return PhotoQuality(-1.0, -1.0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return PhotoQuality(-1.0, -1.0)
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 192) sample *= 2
        val small = BitmapFactory.decodeByteArray(
            imageBytes, 0, imageBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return PhotoQuality(-1.0, -1.0)
        try {
            val w = small.width
            val h = small.height
            if (w < 8 || h < 8) return PhotoQuality(-1.0, -1.0)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            var sum = 0.0
            val gray = DoubleArray(pixels.size)
            for (i in pixels.indices) {
                val p = pixels[i]
                val lum = 0.299 * ((p shr 16) and 0xFF) +
                    0.587 * ((p shr 8) and 0xFF) +
                    0.114 * (p and 0xFF)
                gray[i] = lum
                sum += lum
            }
            val mean = sum / pixels.size
            // Nitidez: varianza del laplaciano con paso 2 (rápido y suficiente).
            val step = 2
            var lapSum = 0.0
            var lapSq = 0.0
            var n = 0
            for (y in step until h - step step step) {
                for (x in step until w - step step step) {
                    val c = gray[y * w + x]
                    val lap = 4 * c -
                        gray[(y - step) * w + x] - gray[(y + step) * w + x] -
                        gray[y * w + (x - step)] - gray[y * w + (x + step)]
                    lapSum += lap
                    lapSq += lap * lap
                    n++
                }
            }
            if (n == 0) return PhotoQuality(mean, -1.0)
            val meanLap = lapSum / n
            return PhotoQuality(mean, lapSq / n - meanLap * meanLap)
        } finally {
            if (!small.isRecycled) small.recycle()
        }
    }

    /**
     * Problemas de calidad legibles para el agricultor (vacío = foto apta).
     * Nunca lanza: si no se puede analizar, no bloquea la identificación.
     */
    fun qualityIssues(imageBytes: ByteArray): List<String> {
        val quality = try {
            analyzeQuality(imageBytes)
        } catch (_: Exception) {
            return emptyList()
        }
        if (quality.brightness < 0) return emptyList()
        val issues = mutableListOf<String>()
        if (quality.brightness < MIN_BRIGHTNESS) {
            issues.add("está muy oscura (tómala con más luz, de día y sin contraluz)")
        }
        // La nitidez solo se evalúa con luz suficiente (a oscuras siempre sale baja).
        if (quality.brightness >= MIN_BRIGHTNESS &&
            quality.sharpness >= 0 && quality.sharpness < MIN_SHARPNESS
        ) {
            issues.add("se ve movida o desenfocada (apoya el celular y espera que enfoque)")
        }
        return issues
    }

    /** Lee la orientación EXIF de la imagen. Devuelve [ExifInterface.ORIENTATION_NORMAL] si no se puede leer. */    private fun readExifOrientation(context: Context, uri: Uri): Int =
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            // Si el formato no soporta EXIF o falla la lectura, se asume orientación normal.
            ExifInterface.ORIENTATION_NORMAL
        }

    /**
     * Aplica la rotación/volteo indicada por la orientación EXIF.
     * Si la orientación es normal o no se puede aplicar, devuelve el mismo [bitmap].
     */
    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun calculateInSampleSize(maxSide: Int): Int {
        var inSampleSize = 1
        val halfMax = maxSide / 2
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
