package com.d4vram.pillmate2.ai

import com.d4vram.pillmate2.R
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

    /**
     * [text] escribe las frases a partir del id de un texto y sus datos: la app le pasa los recursos
     * (así salen en el idioma del móvil) y los tests un sustituto, sin necesidad de Android.
     */
    fun insights(
        entries: List<HistoryEntry>,
        locale: Locale = Locale.getDefault(),
        text: (Int, Array<out Any>) -> String
    ): List<Insight> {
        if (entries.size < 2) return emptyList()
        fun t(id: Int, vararg args: Any) = text(id, args)
        val sorted = entries.sortedBy { it.timestamp }
        val total = sorted.size
        val result = mutableListOf<Insight>()

        val (peakHour, peakCount) = sorted.groupingBy { it.timestamp.hour }.eachCount().maxBy { it.value }
        result += Insight(
            t(R.string.insight_peak_title),
            "%02d:00–%02d:00".format(peakHour, (peakHour + 1) % 24),
            t(R.string.insight_share, peakCount, total, percent(peakCount, total))
        )

        val gaps = sorted.zipWithNext { a, b -> Duration.between(a.timestamp, b.timestamp).toMinutes() }.sorted()
        result += Insight(
            t(R.string.insight_gap_title),
            formatMinutes(gaps[gaps.size / 2]),
            t(R.string.insight_gap_detail, gaps.size, formatMinutes(gaps.first()))
        )

        val byMedication = sorted.groupBy { it.medicationId }
        if (byMedication.size > 1) {
            val top = byMedication.values.maxBy { it.size }
            result += Insight(
                t(R.string.insight_top_title),
                top.first().medicationName,
                t(R.string.insight_share, top.size, total, percent(top.size, total))
            )
        }

        val byDay = sorted.groupBy { it.timestamp.toLocalDate() }
        if (byDay.size > 1) {
            val (day, dayEntries) = byDay.maxBy { it.value.size }
            result += Insight(
                t(R.string.insight_day_title),
                day.format(DateTimeFormatter.ofPattern("EEE d/M", locale)),
                t(R.string.insight_day_detail, dayEntries.size, "%.1f".format(locale, total.toFloat() / byDay.size), byDay.size)
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
