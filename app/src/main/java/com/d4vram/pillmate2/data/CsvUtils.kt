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
        builder.appendLine("id,name,dose,type,colorArgb,emoji,takenToday,dailyGoal,lastTaken,isPrn,sortOrder")
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
                    medication.sortOrder.toString()
                ).joinToCsv()
            )
        }

        builder.appendLine()
        builder.appendLine(SECTION_HISTORY)
        builder.appendLine("id,medicationName,time,date,colorArgb,emoji,notes,timestampIso")
        history.forEach { entry ->
            builder.appendLine(
                listOf(
                    entry.id,
                    entry.medicationName,
                    entry.time,
                    entry.date,
                    entry.color.toArgb().toString(),
                    entry.emoji,
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
                    sortOrder = tokens[10].toIntOrNull() ?: 0
                )
            )
            index++
        }

        if (index >= lines.size || lines[index] != SECTION_HISTORY) {
            throw IllegalArgumentException("Sección de historial no encontrada")
        }
        index += 2

        val history = mutableListOf<HistoryEntry>()
        while (index < lines.size) {
            val tokens = parseCsvLine(lines[index])
            if (tokens.size < 8) {
                throw IllegalArgumentException("Fila de historial incompleta")
            }
            history.add(
                HistoryEntry(
                    id = tokens[0],
                    medicationName = tokens[1],
                    time = tokens[2],
                    date = tokens[3],
                    color = Color(tokens[4].toInt()),
                    emoji = tokens[5],
                    notes = tokens[6].replace("\\n", "\n"),
                    timestamp = runCatching { LocalDateTime.parse(tokens[7]) }.getOrElse { LocalDateTime.now() }
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
