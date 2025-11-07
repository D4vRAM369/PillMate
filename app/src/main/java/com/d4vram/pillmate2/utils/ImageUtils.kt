package com.d4vram.pillmate2.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * 🖼️ Utilidades para manejo de imágenes de medicamentos
 *
 * Funciones:
 * - Redimensionamiento automático a tamaño óptimo (512x512)
 * - Compresión JPEG con calidad 85%
 * - Corrección de orientación EXIF
 * - Almacenamiento seguro en directorio interno
 */
object ImageUtils {

    private const val MAX_IMAGE_SIZE = 512 // Tamaño máximo en píxeles
    private const val JPEG_QUALITY = 85 // Calidad de compresión (0-100)
    private const val IMAGES_DIR = "medication_photos" // Directorio de fotos

    /**
     * Procesa y guarda una imagen desde un URI
     * @param context Contexto de la aplicación
     * @param uri URI de la imagen seleccionada/capturada
     * @param medicationId ID del medicamento (para nombre de archivo)
     * @return Ruta del archivo guardado o null si falla
     */
    fun saveImageFromUri(context: Context, uri: Uri, medicationId: String): String? {
        return try {
            // Abrir stream de entrada
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                // Decodificar imagen
                val originalBitmap = BitmapFactory.decodeStream(stream)

                // Corregir orientación si es necesario
                val rotatedBitmap = fixOrientation(context, uri, originalBitmap)

                // Redimensionar
                val resizedBitmap = resizeImage(rotatedBitmap, MAX_IMAGE_SIZE)

                // Guardar en almacenamiento interno
                val savedPath = saveBitmapToInternalStorage(context, resizedBitmap, medicationId)

                // Liberar memoria
                if (rotatedBitmap != originalBitmap) {
                    rotatedBitmap.recycle()
                }
                if (resizedBitmap != rotatedBitmap) {
                    resizedBitmap.recycle()
                }
                originalBitmap.recycle()

                savedPath
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Redimensiona una imagen manteniendo la relación de aspecto
     * @param bitmap Bitmap original
     * @param maxSize Tamaño máximo del lado más largo
     * @return Bitmap redimensionado
     */
    private fun resizeImage(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Si ya es más pequeña que maxSize, no redimensionar
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        // Calcular nueva escala
        val scale = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Corrige la orientación de la imagen según datos EXIF
     * @param context Contexto de la aplicación
     * @param uri URI de la imagen
     * @param bitmap Bitmap original
     * @return Bitmap con orientación correcta
     */
    private fun fixOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }
            } ?: bitmap
        } catch (e: IOException) {
            e.printStackTrace()
            bitmap
        }
    }

    /**
     * Rota un bitmap
     * @param bitmap Bitmap original
     * @param degrees Grados de rotación
     * @return Bitmap rotado
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Guarda un bitmap en el almacenamiento interno
     * @param context Contexto de la aplicación
     * @param bitmap Bitmap a guardar
     * @param medicationId ID del medicamento
     * @return Ruta absoluta del archivo guardado
     */
    private fun saveBitmapToInternalStorage(
        context: Context,
        bitmap: Bitmap,
        medicationId: String
    ): String {
        // Crear directorio si no existe
        val directory = File(context.filesDir, IMAGES_DIR)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // Crear archivo con nombre único
        val filename = "${medicationId}_${UUID.randomUUID()}.jpg"
        val file = File(directory, filename)

        // Guardar como JPEG
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }

        return file.absolutePath
    }

    /**
     * Elimina una imagen del almacenamiento
     * @param photoPath Ruta de la foto a eliminar
     * @return true si se eliminó exitosamente
     */
    fun deleteImage(photoPath: String?): Boolean {
        if (photoPath.isNullOrEmpty()) return false

        return try {
            val file = File(photoPath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Verifica si una imagen existe
     * @param photoPath Ruta de la foto
     * @return true si existe
     */
    fun imageExists(photoPath: String?): Boolean {
        if (photoPath.isNullOrEmpty()) return false
        return File(photoPath).exists()
    }

    /**
     * Obtiene el tamaño de una imagen en bytes
     * @param photoPath Ruta de la foto
     * @return Tamaño en bytes o 0 si no existe
     */
    fun getImageSize(photoPath: String?): Long {
        if (photoPath.isNullOrEmpty()) return 0
        val file = File(photoPath)
        return if (file.exists()) file.length() else 0
    }
}
