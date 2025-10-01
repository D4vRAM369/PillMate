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
    val fileSizeBytes: Long,
    val category: VoiceNoteCategory = VoiceNoteCategory.DEFAULT,
    val tags: List<String> = emptyList()
)

enum class VoiceNoteCategory(val colorHex: String, val displayName: String) {
    DEFAULT("#6366F1", "General"),
    WORK("#EF4444", "Trabajo"),
    PERSONAL("#10B981", "Personal"),
    MEDICAL("#F59E0B", "Médico"),
    IDEAS("#8B5CF6", "Ideas"),
    REMINDERS("#06B6D4", "Recordatorios")
}
