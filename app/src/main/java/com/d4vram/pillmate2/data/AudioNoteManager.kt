package com.d4vram.pillmate2.data

import android.content.Context
import com.d4vram.pillmate2.data.voice.VoiceNoteEncryption
import java.io.File
import java.util.Base64
import java.util.UUID

/**
 * 🎙️ Gestor de notas de audio encriptadas para el historial
 *
 * Responsabilidades:
 * - Guardar archivos de audio encriptados
 * - Desencriptar para reproducción temporal
 * - Eliminar archivos de audio
 * - Exportar audios con sus metadatos
 */
class AudioNoteManager(private val context: Context) {

    companion object {
        private const val AUDIO_NOTES_DIR = "audio_notes"
        private const val TEMP_DECRYPT_DIR = "temp_audio"
        // Contraseña fija para encriptación (en producción debería venir de KeyStore)
        private val ENCRYPTION_PASSWORD = "PillMate_AudioNotes_2024".toCharArray()
    }

    private val audioNotesDir: File by lazy {
        File(context.filesDir, AUDIO_NOTES_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    private val tempDecryptDir: File by lazy {
        File(context.cacheDir, TEMP_DECRYPT_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Guarda un archivo de audio temporal encriptándolo
     * @param tempFile Archivo temporal sin encriptar
     * @return URI del archivo encriptado guardado
     */
    fun saveEncryptedAudio(tempFile: File): String {
        val audioId = UUID.randomUUID().toString()
        val encryptedFile = File(audioNotesDir, "$audioId.enc")

        // Generar salt e IV únicos
        val salt = VoiceNoteEncryption.generateSalt()
        val iv = VoiceNoteEncryption.generateIv()

        // Derivar clave de la contraseña
        val key = VoiceNoteEncryption.deriveKey(ENCRYPTION_PASSWORD, salt)

        // Encriptar archivo
        VoiceNoteEncryption.encryptFile(key, iv, tempFile, encryptedFile)

        // Guardar salt e IV en archivos separados
        File(audioNotesDir, "$audioId.salt").writeBytes(salt)
        File(audioNotesDir, "$audioId.iv").writeBytes(iv)

        // Eliminar archivo temporal
        tempFile.delete()

        return audioId
    }

    /**
     * Desencripta un audio temporalmente para reproducción
     * @param audioId ID del audio encriptado
     * @return Archivo temporal desencriptado (se debe eliminar después de usar)
     */
    fun decryptAudioForPlayback(audioId: String): File? {
        val encryptedFile = File(audioNotesDir, "$audioId.enc")
        val saltFile = File(audioNotesDir, "$audioId.salt")
        val ivFile = File(audioNotesDir, "$audioId.iv")

        if (!encryptedFile.exists() || !saltFile.exists() || !ivFile.exists()) {
            return null
        }

        try {
            val salt = saltFile.readBytes()
            val iv = ivFile.readBytes()
            val key = VoiceNoteEncryption.deriveKey(ENCRYPTION_PASSWORD, salt)

            val tempDecryptedFile = File(tempDecryptDir, "$audioId.m4a")
            VoiceNoteEncryption.decryptFile(key, iv, encryptedFile, tempDecryptedFile)

            return tempDecryptedFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Elimina un audio encriptado y sus archivos asociados
     */
    fun deleteAudio(audioId: String) {
        File(audioNotesDir, "$audioId.enc").delete()
        File(audioNotesDir, "$audioId.salt").delete()
        File(audioNotesDir, "$audioId.iv").delete()
        File(tempDecryptDir, "$audioId.m4a").delete()
    }

    /**
     * Limpia archivos temporales desencriptados
     */
    fun cleanupTempFiles() {
        tempDecryptDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Obtiene el archivo encriptado para exportación
     */
    fun getEncryptedFile(audioId: String): File? {
        val file = File(audioNotesDir, "$audioId.enc")
        return if (file.exists()) file else null
    }

    /**
     * Obtiene los metadatos de encriptación para exportación
     */
    fun getEncryptionMetadata(audioId: String): EncryptionMetadata? {
        val saltFile = File(audioNotesDir, "$audioId.salt")
        val ivFile = File(audioNotesDir, "$audioId.iv")

        if (!saltFile.exists() || !ivFile.exists()) return null

        return EncryptionMetadata(
            salt = Base64.getEncoder().encodeToString(saltFile.readBytes()),
            iv = Base64.getEncoder().encodeToString(ivFile.readBytes())
        )
    }

    /**
     * Obtiene todos los IDs de audios guardados
     */
    fun getAllAudioIds(): List<String> {
        return audioNotesDir.listFiles()
            ?.filter { it.extension == "enc" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    data class EncryptionMetadata(
        val salt: String,
        val iv: String
    )
}
