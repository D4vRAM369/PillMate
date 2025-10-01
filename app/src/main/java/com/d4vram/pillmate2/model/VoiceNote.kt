package com.d4vram.pillmate2.model

/**
 * 🎙️ Modelo de datos para una nota de voz cifrada.
 */
data class VoiceNote(
    val id: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val durationMillis: Long,
    val encryptedFileName: String,
    val noteSaltBase64: String,
    val noteIvBase64: String,
    val fileSizeBytes: Long
)
