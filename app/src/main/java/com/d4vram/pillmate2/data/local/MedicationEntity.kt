package com.d4vram.pillmate2.data.local

import androidx.compose.ui.graphics.Color
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.d4vram.pillmate2.model.Medication
import java.time.LocalDateTime
import java.time.ZoneId

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val dose: String,
    val type: String,
    val colorArgb: Int,
    val emoji: String,
    val takenToday: Double,
    val dailyGoal: Int,
    val lastTaken: String?,
    val sortOrder: Int,
    val scheduleTime: String? = null,
    @ColumnInfo(defaultValue = "") val scheduleDays: String = "",
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0L, // Timestamp de creación en millis
    @ColumnInfo(defaultValue = "NULL") val photoPath: String? = null, // 📸 Ruta de la foto del medicamento
    @ColumnInfo(defaultValue = "0") val usePhotoInstead: Boolean = false, // 🖼️ Usar foto en lugar de emoji+color
    @ColumnInfo(defaultValue = "NULL") val parentMedicationId: String? = null, // 🔗 ID del medicamento "padre"
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false // 📦 Oculto de Inicio/widget/recordatorios; historial intacto
)

internal fun parseTimestampOrNull(timestampIso: String?): LocalDateTime? =
    timestampIso?.let { iso -> runCatching { LocalDateTime.parse(iso) }.getOrNull() }

private fun String.toScheduleDaysList(): List<String> =
    split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

// Único conversor entidad → dominio: repositorio y recordatorios usan este
internal fun MedicationEntity.toDomain(
    lastTakenTimestamp: LocalDateTime? = null
): Medication = Medication(
    id = id,
    name = name,
    dose = dose,
    type = type,
    color = Color(colorArgb),
    emoji = emoji,
    takenToday = takenToday,
    dailyGoal = dailyGoal,
    // ISO (LocalDateTime.toString()); la UI lo formatea con lastTakenLabel
    lastTaken = lastTakenTimestamp?.toString() ?: lastTaken,
    lastTakenTimestamp = lastTakenTimestamp?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        ?: lastTaken?.let { stored ->
            parseTimestampOrNull(stored)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        },
    sortOrder = sortOrder,
    scheduleTime = scheduleTime,
    scheduleDays = scheduleDays.toScheduleDaysList(),
    reminderEnabled = reminderEnabled,
    createdAt = createdAt,
    photoPath = photoPath,
    usePhotoInstead = usePhotoInstead,
    parentMedicationId = parentMedicationId,
    archived = archived
)
