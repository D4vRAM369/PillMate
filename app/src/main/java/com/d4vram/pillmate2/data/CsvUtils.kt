package com.d4vram.pillmate2.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime

enum class CsvImportError {
    INVALID_FORMAT,
    MEDICATIONS_SECTION_MISSING,
    MEDICATION_ROW_INCOMPLETE,
    HISTORY_SECTION_MISSING,
    INVALID_QUANTITY
}

class CsvImportException(val reason: CsvImportError) : IllegalArgumentException()

/**
 * 🧾 Utilidad sencilla para transformar datos a CSV y viceversa.
 * Formato:
 * # PillMate CSV v1
 * [MEDICATIONS]
 * ... filas ...
 * [HISTORY]
 * ... filas ...
 */
object CsvUtils {
    private const val HEADER = "# PillMate CSV v1"
    private const val SECTION_MEDICATIONS = "[MEDICATIONS]"
    private const val SECTION_HISTORY = "[HISTORY]"

    fun buildCsv(medications: List<Medication>, history: List<HistoryEntry>): String {
        val builder = StringBuilder()
        builder.appendLine(HEADER)
        builder.appendLine(SECTION_MEDICATIONS)
        builder.appendLine("id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead,reminderEnabled,createdAt,parentMedicationId,archived")
        medications.forEach { medication ->
            builder.appendLine(
                listOf(
                    medication.id,
                    medication.name,
                    medication.dose,
                    medication.type,
                    medication.color.toArgb().toString(),
                    medication.emoji,
                    quantityText(medication.takenToday),
                    medication.dailyGoal.toString(),
                    medication.lastTaken ?: "",
                    medication.sortOrder.toString(),
                    medication.scheduleTime ?: "",
                    medication.scheduleDays.joinToString("|"),
                    medication.photoPath?.let { File(it).name } ?: "", // 📸 Solo el nombre del archivo
                    medication.usePhotoInstead.toString(), // 🖼️ Preferencia de visualización
                    medication.reminderEnabled.toString(),
                    medication.createdAt.toString(),
                    medication.parentMedicationId.orEmpty(),
                    medication.archived.toString()
                ).joinToCsv()
            )
        }

        builder.appendLine()
        builder.appendLine(SECTION_HISTORY)
        builder.appendLine("id,medicationId,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri")
        history.forEach { entry ->
            builder.appendLine(
                listOf(
                    entry.id,
                    entry.medicationId,
                    entry.medicationName,
                    entry.dose.orEmpty(),
                    entry.time,
                    entry.date,
                    entry.color.toArgb().toString(),
                    entry.emoji,
                    quantityText(entry.quantity),
                    entry.notes.replace("\n", "\\n"),
                    entry.timestamp.toString(),
                    entry.audioNoteUri?.let { File(it).name } ?: "" // 🎤 Solo el nombre del archivo
                ).joinToCsv()
            )
        }

        return builder.toString()
    }

    fun parseCsv(csv: String): ParsedCsv {
        val lines = csv.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty() || lines.first() != HEADER) {
            throw CsvImportException(CsvImportError.INVALID_FORMAT)
        }

        var index = 1
        if (lines.getOrNull(index) != SECTION_MEDICATIONS) {
            throw CsvImportException(CsvImportError.MEDICATIONS_SECTION_MISSING)
        }
        index += 2 // saltamos cabecera de columnas

        val medications = mutableListOf<Medication>()
        while (index < lines.size && lines[index] != SECTION_HISTORY) {
            val tokens = parseCsvLine(lines[index])
            if (tokens.size < 11) {
                throw CsvImportException(CsvImportError.MEDICATION_ROW_INCOMPLETE)
            }
            // Columnas posicionales. Las exportaciones v1 antiguas incluían una
            // columna `isPrn` entre lastTaken y sortOrder; las nuevas no. Un
            // offset desplaza los índices según detectemos el formato legado.
            val legacyOffset = if (tokens[9].toBooleanStrictOrNull() != null) 1 else 0
            val hasSchedule = tokens.size >= 12 + legacyOffset
            val hasPhotoFields = tokens.size >= 14 + legacyOffset
            medications.add(
                Medication(
                    id = tokens[0],
                    name = tokens[1],
                    dose = tokens[2],
                    type = tokens[3],
                    color = Color(tokens[4].toInt()),
                    emoji = tokens[5],
                    takenToday = parseQuantity(tokens[6], allowZero = true),
                    dailyGoal = tokens[7].toIntOrNull() ?: 1,
                    lastTaken = tokens[8].ifBlank { null },
                    sortOrder = tokens[9 + legacyOffset].toIntOrNull() ?: 0,
                    scheduleTime = if (hasSchedule) tokens[10 + legacyOffset].ifBlank { null } else null,
                    scheduleDays = if (hasSchedule) tokens[11 + legacyOffset].split('|').mapNotNull { it.trim().takeIf { value -> value.isNotEmpty() } } else emptyList(),
                    photoPath = if (hasPhotoFields) tokens[12 + legacyOffset].ifBlank { null } else null, // 📸 Será reconstruida al restaurar
                    usePhotoInstead = if (hasPhotoFields) tokens[13 + legacyOffset].toBooleanStrictOrNull() ?: false else false, // 🖼️ Preferencia
                    reminderEnabled = tokens.getOrNull(14 + legacyOffset)?.toBooleanStrictOrNull() ?: false,
                    createdAt = tokens.getOrNull(15 + legacyOffset)?.toLongOrNull() ?: 0L,
                    parentMedicationId = tokens.getOrNull(16 + legacyOffset)?.ifBlank { null },
                    archived = tokens.getOrNull(17 + legacyOffset)?.toBooleanStrictOrNull() ?: false
                )
            )
            index++
        }

        if (index >= lines.size || lines[index] != SECTION_HISTORY) {
            throw CsvImportException(CsvImportError.HISTORY_SECTION_MISSING)
        }
        index += 2

        val historyHeaderLine = lines[index - 1]
        val historyHeaderHasDose = historyHeaderLine.split(',')
            .map { it.trim().lowercase() }
            .contains("dose")

        val history = mutableListOf<HistoryEntry>()
        while (index < lines.size) {
            val tokens = parseCsvLine(lines[index])
            // New header: id,medicationId,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
            if (tokens.size < 3) { // Must have at least id, medicationId, and name
                continue // or throw exception
            }

            val id = tokens[0]
            val medicationId = tokens[1]
            val medicationName = tokens[2]
            val offset = if (historyHeaderHasDose) 0 else -1
            val dose = if (historyHeaderHasDose) tokens.getOrNull(3)?.ifBlank { null } else null
            val time = tokens.getOrNull(4 + offset) ?: ""
            val date = tokens.getOrNull(5 + offset) ?: ""
            val colorArgb = tokens.getOrNull(6 + offset)?.toIntOrNull() ?: 0
            val emoji = tokens.getOrNull(7 + offset) ?: ""
            val quantityToken = tokens.getOrNull(8 + offset)
            val quantity = if (quantityToken.isNullOrBlank()) 1.0 else parseQuantity(quantityToken)
            val notes = tokens.getOrNull(9 + offset)?.replace("\\n", "\n") ?: ""
            val timestampStr = tokens.getOrNull(10 + offset)
            val timestamp = timestampStr?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDateTime.now()
            val audioNoteUri = tokens.getOrNull(11 + offset)?.ifBlank { null } // 🎤 ID del audio

            history.add(
                HistoryEntry(
                    id = id,
                    medicationId = medicationId,
                    medicationName = medicationName,
                    time = time,
                    date = date,
                    color = Color(colorArgb),
                    emoji = emoji,
                    quantity = quantity,
                    notes = notes,
                    timestamp = timestamp,
                    dose = dose,
                    audioNoteUri = audioNoteUri // 🎤 Ruta del audio
                )
            )
            index++
        }

        return ParsedCsv(medications, history)
    }

    data class ParsedCsv(
        val medications: List<Medication>,
        val history: List<HistoryEntry>
    )

    private fun quantityText(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun parseQuantity(raw: String, allowZero: Boolean = false): Double =
        raw.replace(',', '.').toDoubleOrNull()
            ?.takeIf { it.isFinite() && (it > 0.0 || allowZero && it == 0.0) }
            ?: throw CsvImportException(CsvImportError.INVALID_QUANTITY)

    private fun List<String>.joinToCsv(): String = buildString {
        this@joinToCsv.forEachIndexed { i, value ->
            if (i > 0) append(',')
            append(escape(value))
        }
    }

    private fun escape(raw: String): String {
        if (raw.isEmpty()) return ""
        val needsQuotes = raw.contains(',') || raw.contains('"') || raw.contains('\n')
        val value = raw.replace("\"", "\"\"")
        return if (needsQuotes) "\"$value\"" else value
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val builder = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        builder.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == ',' && !inQuotes -> {
                    values.add(builder.toString())
                    builder.clear()
                }
                else -> builder.append(char)
            }
            i++
        }
        values.add(builder.toString())
        return values
    }

    private fun String.toBooleanStrictOrNull(): Boolean? = when (lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
