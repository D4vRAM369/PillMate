package com.d4vram.pillmate2.data

import android.content.Context
import com.d4vram.pillmate2.data.voice.VoiceNoteEncryption
import java.io.File
import java.util.Base64
import java.util.UUID
import java.security.SecureRandom

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
        private const val PREFS_NAME = "audio_note_encryption"
        private const val INSTALL_SECRET_KEY = "install_secret"
        private val LEGACY_ENCRYPTION_PASSWORD = "PillMate_AudioNotes_2024".toCharArray()
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

    private val encryptionPassword: CharArray by lazy {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(INSTALL_SECRET_KEY, null)
        val encoded = existing ?: run {
            val bytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
            Base64.getEncoder().encodeToString(bytes).also {
                prefs.edit().putString(INSTALL_SECRET_KEY, it).apply()
            }
        }
        encoded.toCharArray()
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
        val key = VoiceNoteEncryption.deriveKey(encryptionPassword, salt)

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
            val tempDecryptedFile = File(tempDecryptDir, "$audioId.m4a")
            val key = VoiceNoteEncryption.deriveKey(encryptionPassword, salt)
            runCatching {
                VoiceNoteEncryption.decryptFile(key, iv, encryptedFile, tempDecryptedFile)
            }.getOrElse {
                val legacyKey = VoiceNoteEncryption.deriveKey(LEGACY_ENCRYPTION_PASSWORD, salt)
                VoiceNoteEncryption.decryptFile(legacyKey, iv, encryptedFile, tempDecryptedFile)
            }

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

    fun exportEncryptedAudio(audioId: String, destinationDir: File): Boolean {
        val encryptedFile = File(audioNotesDir, "$audioId.enc")
        val saltFile = File(audioNotesDir, "$audioId.salt")
        val ivFile = File(audioNotesDir, "$audioId.iv")
        if (!encryptedFile.exists() || !saltFile.exists() || !ivFile.exists()) return false

        destinationDir.mkdirs()
        encryptedFile.copyTo(File(destinationDir, encryptedFile.name), overwrite = true)
        saltFile.copyTo(File(destinationDir, saltFile.name), overwrite = true)
        ivFile.copyTo(File(destinationDir, ivFile.name), overwrite = true)
        return true
    }

    fun exportEncryptionSecret(destinationDir: File) {
        destinationDir.mkdirs()
        File(destinationDir, INSTALL_SECRET_KEY).writeText(String(encryptionPassword))
    }

    fun importEncryptionSecret(sourceDir: File) {
        val secretFile = File(sourceDir, INSTALL_SECRET_KEY)
        if (!secretFile.exists()) return
        val secret = secretFile.readText().takeIf { it.isNotBlank() } ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(INSTALL_SECRET_KEY, secret)
            .apply()
    }

    fun importEncryptedAudio(audioId: String, sourceDir: File): Boolean {
        val encryptedFile = File(sourceDir, "$audioId.enc")
        val saltFile = File(sourceDir, "$audioId.salt")
        val ivFile = File(sourceDir, "$audioId.iv")
        if (!encryptedFile.exists() || !saltFile.exists() || !ivFile.exists()) return false

        encryptedFile.copyTo(File(audioNotesDir, encryptedFile.name), overwrite = true)
        saltFile.copyTo(File(audioNotesDir, saltFile.name), overwrite = true)
        ivFile.copyTo(File(audioNotesDir, ivFile.name), overwrite = true)
        return true
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
