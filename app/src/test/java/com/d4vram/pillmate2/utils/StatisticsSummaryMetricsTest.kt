package com.d4vram.pillmate2.utils

import androidx.compose.ui.graphics.Color
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsSummaryMetricsTest {
    @Test
    fun medicationSummarySeparatesTakeCountFromConsumedAmount() {
        val valium = medication("valium", "5 mg", "Pastilla")
        val noctamid = medication("noctamid", "1 mg", "Gota")
        val entries = listOf(
            entry("valium", 2),
            entry("noctamid", 9)
        )

        val rows = medicationStatisticsRows(entries, listOf(valium, noctamid))

        assertEquals(
            listOf(
                MedicationStatisticsRow(valium, takeCount = 1, amount = MedicationAmount(10f, "mg")),
                MedicationStatisticsRow(noctamid, takeCount = 1, amount = MedicationAmount(9f, "gotas"))
            ),
            rows
        )
    }

    private fun medication(id: String, dose: String, type: String) = Medication(
        id = id,
        name = id,
        dose = dose,
        type = type,
        color = Color.Green,
        emoji = "💊"
    )

    private fun entry(medicationId: String, quantity: Int) = HistoryEntry(
        id = "$medicationId-$quantity",
        medicationId = medicationId,
        medicationName = medicationId,
        time = "10:00",
        date = "2026-10-13",
        color = Color.Green,
        emoji = "💊",
        quantity = quantity.toDouble(),
        timestamp = LocalDateTime.of(2026, 10, 13, 10, 0)
    )
}
