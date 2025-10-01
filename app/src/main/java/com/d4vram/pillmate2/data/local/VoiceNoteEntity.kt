package com.d4vram.pillmate2.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val durationMillis: Long,
    val encryptedFileName: String,
    val noteSaltBase64: String,
    val noteIvBase64: String,
    val fileSizeBytes: Long
)
