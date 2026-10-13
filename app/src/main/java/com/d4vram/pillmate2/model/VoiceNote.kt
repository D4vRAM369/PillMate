package com.d4vram.pillmate2.model

import androidx.annotation.StringRes
import com.d4vram.pillmate2.R

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

enum class VoiceNoteCategory(val colorHex: String, @StringRes val labelRes: Int) {
    DEFAULT("#6366F1", R.string.vn_category_general),
    WORK("#EF4444", R.string.vn_category_work),
    PERSONAL("#10B981", R.string.vn_category_personal),
    MEDICAL("#F59E0B", R.string.vn_category_medical),
    IDEAS("#8B5CF6", R.string.vn_category_ideas),
    REMINDERS("#06B6D4", R.string.vn_category_reminders)
}
