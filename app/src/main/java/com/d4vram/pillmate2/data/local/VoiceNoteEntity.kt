package com.d4vram.pillmate2.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.d4vram.pillmate2.model.VoiceNoteCategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromCategory(category: VoiceNoteCategory): String {
        return category.name
    }

    @TypeConverter
    fun toCategory(categoryName: String): VoiceNoteCategory {
        return VoiceNoteCategory.valueOf(categoryName)
    }
}

@Entity(tableName = "voice_notes")
@TypeConverters(Converters::class)
data class VoiceNoteEntity(
    @PrimaryKey val id: String,
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
