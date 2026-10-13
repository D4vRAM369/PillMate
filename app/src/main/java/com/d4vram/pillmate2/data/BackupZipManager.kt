package com.d4vram.pillmate2.data

import android.content.Context
import com.d4vram.pillmate2.model.Medication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import java.io.File

enum class BackupImportError {
    CSV_MISSING
}

class BackupImportException(val reason: BackupImportError) : IllegalArgumentException()

/**
 * 📦 Gestor de backups en formato ZIP
 *
 * Estructura del backup:
 * backup.zip
 * ├── backup.csv          # Datos de medicamentos e historial
 * └── photos/             # Fotos de medicamentos
 *     ├── med1_abc.jpg
 *     └── med2_def.jpg
 *
 * Ventajas:
 * - Incluye imágenes sin comprometer legibilidad del CSV
 * - Compresión automática
 * - Estándar de la industria
 * - Compatible con herramientas externas
 */
object BackupZipManager {

    private const val CSV_FILENAME = "backup.csv"
    private const val PHOTOS_DIR = "photos"

    /**
     * Crea un backup completo en formato ZIP
     * @param context Contexto de la aplicación
     * @param medications Lista de medicamentos a respaldar
     * @param history Lista de historial a respaldar
     * @param outputZipFile Archivo ZIP de salida
     * @return File del ZIP creado
     */
    suspend fun createBackupZip(
        context: Context,
        medications: List<Medication>,
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        outputZipFile: File
    ): File = withContext(Dispatchers.IO) {
        // Crear directorio temporal para construir el backup
        val tempDir = File(context.cacheDir, "backup_temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // 1. Crear CSV
            val csvContent = CsvUtils.buildCsv(medications, history)
            val csvFile = File(tempDir, CSV_FILENAME)
            csvFile.writeText(csvContent)

            // 2. Copiar fotos de medicamentos
            val photosDir = File(tempDir, PHOTOS_DIR)
            photosDir.mkdirs()

            var photoCopiedCount = 0
            medications.forEach { medication ->
                medication.photoPath?.let { photoPath ->
                    val sourceFile = File(photoPath)
                    if (sourceFile.exists()) {
                        val destFile = File(photosDir, sourceFile.name)
                        sourceFile.copyTo(destFile, overwrite = true)
                        photoCopiedCount++
                    }
                }
            }

            // 2.5. Copiar audios del historial
            val audioNotesDir = File(tempDir, "audio_notes")
            audioNotesDir.mkdirs()
            val audioNoteManager = AudioNoteManager(context)
            audioNoteManager.exportEncryptionSecret(audioNotesDir)

            var audioCopiedCount = 0
            history.forEach { entry ->
                entry.audioNoteUri?.let { audioId ->
                    if (audioNoteManager.exportEncryptedAudio(audioId, audioNotesDir)) {
                        audioCopiedCount++
                    }
                }
            }

            // 3. Crear ZIP
            val zipFile = ZipFile(outputZipFile)
            val zipParams = ZipParameters()

            // Añadir CSV al ZIP
            zipFile.addFile(csvFile, zipParams)

            // Añadir carpeta de fotos si hay alguna
            if (photoCopiedCount > 0) {
                zipFile.addFolder(photosDir, zipParams)
            }

            // Añadir carpeta de audios si hay alguna
            if (audioCopiedCount > 0) {
                zipFile.addFolder(audioNotesDir, zipParams)
            }

            outputZipFile
        } finally {
            // Limpiar directorio temporal
            tempDir.deleteRecursively()
        }
    }

    /**
     * Crea un backup completo en formato ZIP con cifrado AES-256
     * @param context Contexto de la aplicación
     * @param medications Lista de medicamentos a respaldar
     * @param history Lista de historial a respaldar
     * @param outputZipFile Archivo ZIP de salida
     * @param password Contraseña para cifrar el backup
     * @return File del ZIP cifrado creado
     */
    suspend fun createEncryptedBackupZip(
        context: Context,
        medications: List<Medication>,
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        outputZipFile: File,
        password: String
    ): File = withContext(Dispatchers.IO) {
        // Crear directorio temporal para construir el backup
        val tempDir = File(context.cacheDir, "backup_temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // 1. Crear CSV
            val csvContent = CsvUtils.buildCsv(medications, history)
            val csvFile = File(tempDir, CSV_FILENAME)
            csvFile.writeText(csvContent)

            // 2. Copiar fotos de medicamentos
            val photosDir = File(tempDir, PHOTOS_DIR)
            photosDir.mkdirs()

            var photoCopiedCount = 0
            medications.forEach { medication ->
                medication.photoPath?.let { photoPath ->
                    val sourceFile = File(photoPath)
                    if (sourceFile.exists()) {
                        val destFile = File(photosDir, sourceFile.name)
                        sourceFile.copyTo(destFile, overwrite = true)
                        photoCopiedCount++
                    }
                }
            }

            // 2.5. Copiar audios del historial
            val audioNotesDir = File(tempDir, "audio_notes")
            audioNotesDir.mkdirs()
            val audioNoteManager = AudioNoteManager(context)
            audioNoteManager.exportEncryptionSecret(audioNotesDir)

            var audioCopiedCount = 0
            history.forEach { entry ->
                entry.audioNoteUri?.let { audioId ->
                    if (audioNoteManager.exportEncryptedAudio(audioId, audioNotesDir)) {
                        audioCopiedCount++
                    }
                }
            }

            // 3. Crear ZIP con cifrado AES-256
            val zipFile = ZipFile(outputZipFile, password.toCharArray())
            val zipParams = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
            }

            // Añadir CSV al ZIP cifrado
            zipFile.addFile(csvFile, zipParams)

            // Añadir carpeta de fotos si hay alguna
            if (photoCopiedCount > 0) {
                zipFile.addFolder(photosDir, zipParams)
            }

            // Añadir carpeta de audios si hay alguna
            if (audioCopiedCount > 0) {
                zipFile.addFolder(audioNotesDir, zipParams)
            }

            outputZipFile
        } finally {
            // Limpiar directorio temporal
            tempDir.deleteRecursively()
        }
    }

    /**
     * Extrae un backup ZIP y retorna los datos parseados
     * @param context Contexto de la aplicación
     * @param zipFile Archivo ZIP a extraer
     * @param password Contraseña para descifrar (null si no está cifrado)
     * @return ParsedBackup con medicamentos, historial y rutas reconstruidas
     */
    suspend fun extractBackupZip(
        context: Context,
        zipFile: File,
        password: String? = null
    ): ParsedBackup = withContext(Dispatchers.IO) {
        // Crear directorio temporal para extraer
        val tempExtractDir = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}")
        tempExtractDir.mkdirs()

        try {
            // 1. Extraer ZIP (con contraseña si es necesario)
            val zip = if (password != null) {
                ZipFile(zipFile, password.toCharArray())
            } else {
                ZipFile(zipFile)
            }
            zip.extractAll(tempExtractDir.absolutePath)

            // 2. Leer CSV
            val csvFile = File(tempExtractDir, CSV_FILENAME)
            if (!csvFile.exists()) {
                throw BackupImportException(BackupImportError.CSV_MISSING)
            }

            val csvContent = csvFile.readText()
            val parsed = CsvUtils.parseCsv(csvContent)

            // 3. Restaurar fotos al directorio de la app
            val photosDir = File(tempExtractDir, PHOTOS_DIR)
            val medicationPhotosDir = File(context.filesDir, "medication_photos")
            medicationPhotosDir.mkdirs()

            val photoPathMap = mutableMapOf<String, String>() // nombre_archivo -> ruta_nueva

            if (photosDir.exists() && photosDir.isDirectory) {
                photosDir.listFiles()?.forEach { photoFile ->
                    if (photoFile.isFile && photoFile.extension.lowercase() in listOf("jpg", "jpeg", "png")) {
                        // Copiar foto al directorio de la app
                        val destFile = File(medicationPhotosDir, photoFile.name)
                        photoFile.copyTo(destFile, overwrite = true)
                        photoPathMap[photoFile.name] = destFile.absolutePath
                    }
                }
            }

            // 4. Reconstruir rutas de fotos en medicamentos
            val medicationsWithPhotos = parsed.medications.map { medication ->
                val photoFileName = medication.photoPath // Viene solo el nombre del archivo
                if (!photoFileName.isNullOrBlank() && photoPathMap.containsKey(photoFileName)) {
                    medication.copy(photoPath = photoPathMap[photoFileName])
                } else {
                    medication
                }
            }

            // 5. Restaurar audios del historial
            val audioNotesDir = File(tempExtractDir, "audio_notes")
            val audioNoteManager = AudioNoteManager(context)
            val restoredAudioIds = mutableSetOf<String>()

            if (audioNotesDir.exists() && audioNotesDir.isDirectory) {
                audioNoteManager.importEncryptionSecret(audioNotesDir)
                audioNotesDir.listFiles()
                    ?.filter { it.isFile && it.extension == "enc" }
                    ?.forEach { audioFile ->
                        val audioId = audioFile.nameWithoutExtension
                        if (audioNoteManager.importEncryptedAudio(audioId, audioNotesDir)) {
                            restoredAudioIds += audioId
                        }
                    }
            }

            // 6. Reconstruir rutas de audios en historial
            val historyWithAudios = parsed.history.map { entry ->
                val audioId = entry.audioNoteUri
                if (!audioId.isNullOrBlank() && audioId in restoredAudioIds) {
                    entry.copy(audioNoteUri = audioId)
                } else {
                    entry.copy(audioNoteUri = null)
                }
            }

            ParsedBackup(medicationsWithPhotos, historyWithAudios)
        } finally {
            // Limpiar directorio temporal
            tempExtractDir.deleteRecursively()
        }
    }

    data class ParsedBackup(
        val medications: List<Medication>,
        val history: List<com.d4vram.pillmate2.model.HistoryEntry>
    )
}
