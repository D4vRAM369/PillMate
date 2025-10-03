package com.d4vram.pillmate2.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import java.time.LocalDateTime

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
        builder.appendLine("id,name,dose,type,colorArgb,emoji,takenToday,dailyGoal,lastTaken,isPrn,sortOrder,scheduleTime,scheduleDays")
        medications.forEach { medication ->
            builder.appendLine(
                listOf(
                    medication.id,
                    medication.name,
                    medication.dose,
                    medication.type,
                    medication.color.toArgb().toString(),
                    medication.emoji,
                    medication.takenToday.toString(),
                    medication.dailyGoal.toString(),
                    medication.lastTaken ?: "",
                    medication.isPrn.toString(),
                    medication.sortOrder.toString(),
                    medication.scheduleTime ?: "",
                    medication.scheduleDays.joinToString("|")
                ).joinToCsv()
            )
        }

        builder.appendLine()
        builder.appendLine(SECTION_HISTORY)
        builder.appendLine("id,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso")
        history.forEach { entry ->
            builder.appendLine(
                listOf(
                    entry.id,
                    entry.medicationName,
                    entry.dose.orEmpty(),
                    entry.time,
                    entry.date,
                    entry.color.toArgb().toString(),
                    entry.emoji,
                    entry.quantity.toString(),
                    entry.notes.replace("\n", "\\n"),
                    entry.timestamp.toString()
                ).joinToCsv()
            )
        }

        return builder.toString()
    }

    fun parseCsv(csv: String): ParsedCsv {
        val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty() || lines.first() != HEADER) {
            throw IllegalArgumentException("Formato CSV no reconocido")
        }

        var index = 1
        if (lines.getOrNull(index) != SECTION_MEDICATIONS) {
            throw IllegalArgumentException("Sección de medicamentos no encontrada")
        }
        index += 2 // saltamos cabecera de columnas

        val medications = mutableListOf<Medication>()
        while (index < lines.size && lines[index] != SECTION_HISTORY) {
            val tokens = parseCsvLine(lines[index])
            if (tokens.size < 11) {
                throw IllegalArgumentException("Fila de medicamentos incompleta")
            }
            val hasSchedule = tokens.size >= 13
            medications.add(
                Medication(
                    id = tokens[0],
                    name = tokens[1],
                    dose = tokens[2],
                    type = tokens[3],
                    color = Color(tokens[4].toInt()),
                    emoji = tokens[5],
                    takenToday = tokens[6].toIntOrNull() ?: 0,
                    dailyGoal = tokens[7].toIntOrNull() ?: 1,
                    lastTaken = tokens[8].ifBlank { null },
                    isPrn = tokens[9].toBooleanStrictOrNull() ?: false,
                    sortOrder = tokens[10].toIntOrNull() ?: 0,
                    scheduleTime = if (hasSchedule) tokens[11].ifBlank { null } else null,
                    scheduleDays = if (hasSchedule) tokens[12].split('|').mapNotNull { it.trim().takeIf { value -> value.isNotEmpty() } } else emptyList()
                )
            )
            index++
        }

        if (index >= lines.size || lines[index] != SECTION_HISTORY) {
            throw IllegalArgumentException("Sección de historial no encontrada")
        }
        index += 2

        val historyHeaderLine = lines[index - 1]
        val historyHeaderHasDose = historyHeaderLine.split(',')
            .map { it.trim().lowercase() }
            .contains("dose")

        val history = mutableListOf<HistoryEntry>()
        while (index < lines.size) {
            val tokens = parseCsvLine(lines[index])
            if (tokens.size < 9) {
                throw IllegalArgumentException("Fila de historial incompleta")
            }

            val hasDoseColumn = historyHeaderHasDose && tokens.size >= 10
            val doseIndex = if (hasDoseColumn) 2 else null
            val timeIndex = if (hasDoseColumn) 3 else 2
            val dateIndex = timeIndex + 1
            val colorIndex = dateIndex + 1
            val emojiIndex = colorIndex + 1
            val quantityIndex = emojiIndex + 1
            val notesIndex = quantityIndex + 1
            val timestampIndex = notesIndex + 1

            val quantity = tokens.getOrNull(quantityIndex)?.toIntOrNull() ?: 1
            val notesValue = tokens.getOrNull(notesIndex)?.replace("\\n", "\n") ?: ""
            val timestampString = tokens.getOrNull(timestampIndex)
            val parsedTimestamp = timestampString?.let { ts ->
                runCatching { LocalDateTime.parse(ts) }.getOrNull()
            } ?: LocalDateTime.now()
            val doseValue = doseIndex?.let { idx -> tokens.getOrNull(idx)?.ifBlank { null } }
                ?: tokens.getOrNull(timestampIndex + 1)?.ifBlank { null }
            val colorValue = tokens.getOrNull(colorIndex)?.toIntOrNull() ?: 0

            history.add(
                HistoryEntry(
                    id = tokens[0],
                    medicationName = tokens[1],
                    time = tokens.getOrElse(timeIndex) { "" },
                    date = tokens.getOrElse(dateIndex) { "" },
                    color = Color(colorValue),
                    emoji = tokens.getOrElse(emojiIndex) { "" },
                    quantity = quantity,
                    notes = notesValue,
                    timestamp = parsedTimestamp,
                    dose = doseValue
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
