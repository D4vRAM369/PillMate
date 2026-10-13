package com.d4vram.pillmate2.data.voice

import com.d4vram.pillmate2.R
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.d4vram.pillmate2.model.VoiceNote
import com.d4vram.pillmate2.model.VoiceNoteCategory

@Serializable
data class ExportMetadata(
    val exportVersion: String = "1.0",
    val exportDate: String,
    val totalNotes: Int,
    val notes: List<ExportedVoiceNote>
)

@Serializable
data class ExportedVoiceNote(
    val id: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val durationMillis: Long,
    val fileName: String, // Nombre del archivo M4A en el ZIP
    val category: String,
    val tags: List<String>,
    val fileSizeBytes: Long
)

class VoiceNoteExporter(
    private val context: Context,
    private val repository: VoiceNoteRepository
) {
    
    suspend fun exportAllNotesToZip(
        password: CharArray,
        outputFile: File
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener todas las notas
            val notes = repository.getAllNotesForExport()
            if (notes.isEmpty()) {
                return@withContext ExportResult.Error(context.getString(R.string.vn_err_export_empty))
            }
            
            // 2. Crear directorio temporal
            val tempDir = File(context.cacheDir, "voice_export_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            try {
                // 3. Descifrar todas las notas a archivos M4A
                val exportedNotes = mutableListOf<ExportedVoiceNote>()
                
                notes.forEachIndexed { index, note ->
                    val tempAudioFile = repository.decryptNoteToTempFile(note, password)
                    val exportFileName = "nota_${index + 1}_${sanitizeFileName(note.title)}.m4a"
                    val destinationFile = File(tempDir, exportFileName)
                    
                    tempAudioFile.copyTo(destinationFile)
                    tempAudioFile.delete()
                    
                    exportedNotes.add(
                        ExportedVoiceNote(
                            id = note.id,
                            title = note.title,
                            createdAtEpochMillis = note.createdAtEpochMillis,
                            updatedAtEpochMillis = note.updatedAtEpochMillis,
                            durationMillis = note.durationMillis,
                            fileName = exportFileName,
                            category = note.category.name,
                            tags = note.tags,
                            fileSizeBytes = destinationFile.length()
                        )
                    )
                }
                
                // 4. Crear metadata JSON
                val metadata = ExportMetadata(
                    exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    totalNotes = notes.size,
                    notes = exportedNotes
                )
                
                val metadataFile = File(tempDir, "metadata.json")
                metadataFile.writeText(Json.encodeToString(metadata))
                
                // 5. Crear ZIP cifrado con AES-256
                val zipFile = ZipFile(outputFile)
                zipFile.setPassword(password)
                val zipParameters = ZipParameters().apply {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
                
                // 6. Añadir todos los archivos al ZIP
                tempDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        zipFile.addFile(file, zipParameters)
                    }
                }
                
                ExportResult.Success(outputFile, notes.size)
                
            } finally {
                // 7. Limpiar archivos temporales
                tempDir.deleteRecursively()
            }
            
        } catch (e: Exception) {
            ExportResult.Error(context.getString(R.string.vn_err_export_failed, e.localizedMessage.orEmpty()))
        }
    }
    
    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}

sealed class ExportResult {
    data class Success(val file: File, val noteCount: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
}