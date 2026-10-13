package com.d4vram.pillmate2.utils

import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsExporterTest {
    @Test
    fun summaryKeepsTableColumnsAndSeparatesEventsFromAmounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val valium = medication("Valium", "5 mg", "Pastilla")
        val noctamid = medication("Noctamid", "1 mg", "Gota")
        val amountLabel = context.getString(
            R.string.csv_medication_amount,
            valium.name,
            "mg"
        )
        val dropsLabel = context.getString(
            R.string.csv_medication_amount,
            noctamid.name,
            context.getString(R.string.unit_drops)
        )
        val metricHeader = context.getString(R.string.csv_metric)
        val medicationHeader = context.getString(R.string.stats_medication_fallback)
        val file = StatsExporter(context).exportSummaryToCSV(
            totalDoses = 2,
            averageDaily = "2",
            medications = listOf(valium, noctamid),
            history = listOf(entry(valium, 2), entry(noctamid, 9)),
            fileName = "summary-metrics-test.csv"
        ) ?: error("No se pudo generar el CSV")

        val csv = file.readText()
        assertTrue(csv.contains("$amountLabel,10"))
        assertTrue(csv.contains("$dropsLabel,9"))
        assertTrue(csv.contains("Valium,1,"))
        assertTrue(csv.contains("Noctamid,1,"))
        assertTrue(csv.lines().any { it.split(',').size == 2 && it.startsWith("$metricHeader,") })
        assertTrue(csv.lines().any { it.split(',').size == 3 && it.startsWith("$medicationHeader,") })
        file.delete()
    }

    private fun medication(name: String, dose: String, type: String) = Medication(
        id = name.lowercase(), name = name, dose = dose, type = type,
        color = Color.Green, emoji = "💊"
    )

    private fun entry(medication: Medication, quantity: Int) = HistoryEntry(
        id = medication.id, medicationId = medication.id, medicationName = medication.name,
        time = "10:00", date = "2026-10-13", color = Color.Green, emoji = "💊",
        quantity = quantity, timestamp = LocalDateTime.of(2026, 10, 13, 10, 0)
    )
}
