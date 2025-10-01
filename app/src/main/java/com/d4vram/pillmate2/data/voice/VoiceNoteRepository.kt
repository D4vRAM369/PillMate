package com.d4vram.pillmate2.data.voice

import android.content.Context
import android.util.Base64
import com.d4vram.pillmate2.data.local.VoiceNoteDao
import com.d4vram.pillmate2.data.local.VoiceNoteEntity
import com.d4vram.pillmate2.model.VoiceNote
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VoiceNoteRepository(
    private val context: Context,
    private val voiceNoteDao: VoiceNoteDao
) {
    private val notesDir: File by lazy {
        File(context.filesDir, VOICE_NOTES_DIR).apply { if (!exists()) mkdirs() }
    }

    val voiceNotes: Flow<List<VoiceNote>> = voiceNoteDao.observeVoiceNotes()
        .map { entities -> entities.map { it.toDomain() } }

    suspend fun saveVoiceNote(
        sourceFile: File,
        password: CharArray,
        durationMillis: Long,
        title: String
    ): VoiceNote = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val salt = VoiceNoteEncryption.generateSalt()
        val iv = VoiceNoteEncryption.generateIv()
        val passwordCopy = password.copyOf()
        val key = VoiceNoteEncryption.deriveKey(passwordCopy, salt)
        VoiceNoteEncryption.wipePassword(passwordCopy)

        val encryptedFile = File(notesDir, "$id.enc")
        VoiceNoteEncryption.encryptFile(key, iv, sourceFile, encryptedFile)

        val now = Instant.now().toEpochMilli()
        val entity = VoiceNoteEntity(
            id = id,
            title = title.ifBlank { DEFAULT_TITLE_PREFIX + formatTimestampLabel(now) },
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            durationMillis = durationMillis,
            encryptedFileName = encryptedFile.name,
            noteSaltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            noteIvBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            fileSizeBytes = encryptedFile.length()
        )
        voiceNoteDao.upsert(entity)
        sourceFile.delete()
        entity.toDomain()
    }

    suspend fun decryptNoteToTempFile(note: VoiceNote, password: CharArray): File = withContext(Dispatchers.IO) {
        val salt = Base64.decode(note.noteSaltBase64, Base64.NO_WRAP)
        val iv = Base64.decode(note.noteIvBase64, Base64.NO_WRAP)
        val passwordCopy = password.copyOf()
        val key: SecretKey = VoiceNoteEncryption.deriveKey(passwordCopy, salt)
        VoiceNoteEncryption.wipePassword(passwordCopy)

        val encryptedFile = File(notesDir, note.encryptedFileName)
        val tempOutput = File.createTempFile("voice_note_${note.id}", TEMP_FILE_SUFFIX, context.cacheDir)
        VoiceNoteEncryption.decryptFile(key, iv, encryptedFile, tempOutput)
        tempOutput
    }

    suspend fun deleteVoiceNote(noteId: String) = withContext(Dispatchers.IO) {
        val entity = voiceNoteDao.getById(noteId) ?: return@withContext
        val encryptedFile = File(notesDir, entity.encryptedFileName)
        if (encryptedFile.exists()) {
            encryptedFile.delete()
        }
        voiceNoteDao.delete(entity)
    }

    suspend fun renameVoiceNote(noteId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val entity = voiceNoteDao.getById(noteId) ?: return@withContext
        val updated = entity.copy(
            title = newTitle.ifBlank { entity.title },
            updatedAtEpochMillis = Instant.now().toEpochMilli()
        )
        voiceNoteDao.update(updated)
    }

    suspend fun getAllNotesForExport(): List<VoiceNote> = withContext(Dispatchers.IO) {
        voiceNoteDao.getAllNotes().map { it.toDomain() }
    }

    private fun VoiceNoteEntity.toDomain(): VoiceNote = VoiceNote(
        id = id,
        title = title,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        durationMillis = durationMillis,
        encryptedFileName = encryptedFileName,
        noteSaltBase64 = noteSaltBase64,
        noteIvBase64 = noteIvBase64,
        fileSizeBytes = fileSizeBytes,
        category = category,
        tags = tags
    )

    private fun formatTimestampLabel(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        return DEFAULT_TITLE_FORMATTER.format(instant.atZone(ZoneId.systemDefault()))
    }

    companion object {
        private const val VOICE_NOTES_DIR = "voice_notes"
        private const val TEMP_FILE_SUFFIX = ".m4a"
        private const val DEFAULT_TITLE_PREFIX = "Nota "
        private val DEFAULT_TITLE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
