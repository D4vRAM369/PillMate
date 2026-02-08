package com.d4vram.pillmate2.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dose: String,
    val type: String,
    val colorArgb: Int,
    val emoji: String,
    val takenToday: Int,
    val dailyGoal: Int,
    val lastTaken: String?,
    val isPrn: Boolean,
    val sortOrder: Int,
    val scheduleTime: String? = null,
    @ColumnInfo(defaultValue = "") val scheduleDays: String = "",
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L, // Timestamp de creación en millis
    @ColumnInfo(defaultValue = "NULL") val photoPath: String? = null, // 📸 Ruta de la foto del medicamento
    @ColumnInfo(defaultValue = "0") val usePhotoInstead: Boolean = false, // 🖼️ Usar foto en lugar de emoji+color
    @ColumnInfo(defaultValue = "NULL") val parentMedicationId: String? = null // 🔗 ID del medicamento "padre"
)
