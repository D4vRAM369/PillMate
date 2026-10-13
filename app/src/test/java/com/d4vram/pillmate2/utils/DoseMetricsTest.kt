package com.d4vram.pillmate2.utils

import androidx.compose.ui.graphics.Color
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import java.time.LocalDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class DoseMetricsTest {
    @Test
    fun oneEntryWithTwoTabletsIsOneTakeAndTenMilligrams() {
        val valium = medication(id = "valium", dose = "5 mg", type = "Pastilla")
        val entries = listOf(entry(medicationId = "valium", quantity = 2))

        assertEquals(1, countTakeEvents(entries))
        assertEquals(MedicationAmount(10f, "mg"), amountForMedication(valium, entries))
    }

    @Test
    fun oneEntryWithNineDropsIsOneTakeAndNineDrops() {
        val noctamid = medication(id = "noctamid", dose = "1 mg", type = "Gota")
        val entries = listOf(entry(medicationId = "noctamid", quantity = 9))

        assertEquals(1, countTakeEvents(entries))
        assertEquals(MedicationAmount(9f, "gotas"), amountForMedication(noctamid, entries))
    }

    @Test
    fun customMedicationQuantityRemainsUnitsSeparateFromTakeCount() {
        val custom = medication(id = "custom", dose = "", type = "Otra")
        val entries = listOf(entry(medicationId = "custom", quantity = 2))

        assertEquals(1, countTakeEvents(entries))
        assertEquals(MedicationAmount(2f, "units"), amountForMedication(custom, entries))
    }

    @Test
    fun twoSeparateEntriesAreTwoTakesEvenWhenBothHaveQuantityTwo() {
        val entries = listOf(
            entry(medicationId = "valium", quantity = 2),
            entry(medicationId = "valium", quantity = 2)
        )

        assertEquals(2, countTakeEvents(entries))
    }

    @Test
    fun mixedStrengthFamilyUsesEachEntryMedicationStrength() {
        val fiveMg = medication(id = "valium-5", dose = "5 mg", type = "Pastilla")
        val tenMg = medication(id = "valium-10", dose = "10 mg", type = "Pastilla")
        val entries = listOf(
            entry(medicationId = "valium-5", quantity = 2),
            entry(medicationId = "valium-10", quantity = 1)
        )

        assertEquals(2, countTakeEvents(entries))
        val amounts = amountsByMedication(entries, listOf(fiveMg, tenMg))
        assertEquals(10f, amounts.getValue("valium-5").value, 0.001f)
        assertEquals(10f, amounts.getValue("valium-10").value, 0.001f)
    }

    @Test
    fun dailyChartMetricsSeparateTakeCountFromConsumedAmount() {
        val valium = medication(id = "valium", dose = "5 mg", type = "Pastilla")
        val entries = listOf(entry(medicationId = "valium", quantity = 2))

        val metrics = dailyDoseMetrics(entries, mapOf("valium" to valium), "mg")

        assertEquals(1, metrics.takeCount)
        assertEquals(10f, metrics.amount, 0.001f)
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
        id = UUID.randomUUID().toString(),
        medicationId = medicationId,
        medicationName = medicationId,
        time = "10:00",
        date = "2026-10-13",
        color = Color.Green,
        emoji = "💊",
        quantity = quantity,
        timestamp = LocalDateTime.of(2026, 10, 13, 10, 0)
    )
}
