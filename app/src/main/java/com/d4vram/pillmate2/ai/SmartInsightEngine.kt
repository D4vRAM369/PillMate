package com.d4vram.pillmate2.ai

import com.d4vram.pillmate2.model.HistoryEntry
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Un dato calculado sobre las tomas del periodo: qué mide, cuánto vale y de dónde sale. */
data class Insight(val title: String, val value: String, val detail: String)

/**
 * Describe el patrón de las tomas registradas con aritmética, sin modelo de IA.
 * Solo cuenta lo que hay: no estima adherencia ni opina, porque la app es de registro a demanda.
 */
object SmartInsightEngine {

    fun insights(entries: List<HistoryEntry>): List<Insight> {
        if (entries.size < 2) return emptyList()
        val sorted = entries.sortedBy { it.timestamp }
        val total = sorted.size
        val result = mutableListOf<Insight>()

        val (peakHour, peakCount) = sorted.groupingBy { it.timestamp.hour }.eachCount().maxBy { it.value }
        result += Insight(
            "Hora más activa",
            "%02d:00–%02d:00".format(peakHour, (peakHour + 1) % 24),
            "$peakCount de $total tomas (${percent(peakCount, total)} %)"
        )

        val gaps = sorted.zipWithNext { a, b -> Duration.between(a.timestamp, b.timestamp).toMinutes() }.sorted()
        result += Insight(
            "Intervalo habitual entre tomas",
            formatMinutes(gaps[gaps.size / 2]),
            "Mediana de ${gaps.size} intervalos · el más corto: ${formatMinutes(gaps.first())}"
        )

        val byMedication = sorted.groupBy { it.medicationId }
        if (byMedication.size > 1) {
            val top = byMedication.values.maxBy { it.size }
            result += Insight(
                "Más registrado",
                top.first().medicationName,
                "${top.size} de $total tomas (${percent(top.size, total)} %)"
            )
        }

        val byDay = sorted.groupBy { it.timestamp.toLocalDate() }
        if (byDay.size > 1) {
            val (day, dayEntries) = byDay.maxBy { it.value.size }
            result += Insight(
                "Día con más tomas",
                day.format(DateTimeFormatter.ofPattern("EEE d/M", Locale("es"))),
                "${dayEntries.size} tomas · media de %.1f en los ${byDay.size} días con registro"
                    .format(Locale("es"), total.toFloat() / byDay.size)
            )
        }
        return result
    }

    private fun percent(part: Int, total: Int): Int = (part * 100f / total).roundToInt()

    internal fun formatMinutes(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes < 24 * 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "${minutes / 1440} d ${minutes % 1440 / 60} h"
    }
}
