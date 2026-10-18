package com.d4vram.pillmate2.data

import com.d4vram.pillmate2.data.local.HistoryEntryEntity
import com.d4vram.pillmate2.data.local.MedicationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationMergeTest {
    @Test fun oldNumericStrengthMustMatchTheSelectedSource() {
        org.junit.Assert.assertTrue(mergeHistoryDoseMatchesSource("2,5", "2,5mg"))
        org.junit.Assert.assertTrue(mergeHistoryDoseMatchesSource("2.5", "2,5 mg"))
        org.junit.Assert.assertTrue(mergeHistoryDoseMatchesSource("2.5 mg", "2,5mg"))
        org.junit.Assert.assertTrue(mergeHistoryDoseMatchesSource(null, "2,5mg"))
        org.junit.Assert.assertFalse(mergeHistoryDoseMatchesSource("25", "2,5mg"))
        org.junit.Assert.assertFalse(mergeHistoryDoseMatchesSource("1,25", "2,5mg"))
        org.junit.Assert.assertFalse(mergeHistoryDoseMatchesSource("2,5 ml", "2,5mg"))
        org.junit.Assert.assertFalse(mergeHistoryDoseMatchesSource("2,5", "2,5 ml"))
    }

    @Test fun cumulativeQuantityRetainsTotalsOutsideAvailableHistory() {
        assertEquals(8.0, mergedCumulativeQuantity(3.0, 10.0, 0.5), 0.0)
        assertEquals(3.0, mergedCumulativeQuantity(3.0, 0.0, 0.5), 0.0)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            mergedCumulativeQuantity(Double.MAX_VALUE, Double.MAX_VALUE, 2.0)
        }
    }

    @Test fun quarterStrengthConversionsAcceptCommaAndPreserveAmounts() {
        assertEquals(0.25, expectedMergeMultiplier("2,5 mg", "10mg")!!, 0.0)
        assertEquals(0.5, expectedMergeMultiplier("2.5mg", "5 mg")!!, 0.0)
        assertEquals(0.25, expectedMergeMultiplier("1,25mg", "5mg")!!, 0.0)
    }

    @Test fun conversionPreservesFractions() {
        assertEquals(0.5, convertedMergeQuantity(1.0, 0.5), 0.0)
        assertEquals(0.25, convertedMergeQuantity(0.5, 0.5), 0.0)
        assertEquals(1.0, convertedMergeQuantity(2.0, 0.5), 0.0)
    }

    @Test fun rejectsInvalidMultipliersAndOverflow() {
        for (factor in listOf(0.0, -0.5, Double.NaN, Double.POSITIVE_INFINITY)) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { convertedMergeQuantity(1.0, factor) }
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { convertedMergeQuantity(Double.MAX_VALUE, 2.0) }
    }

    @Test fun rejectsFactorThatChangesRecordedMilligrams() {
        val entry = HistoryEntryEntity("entry", "source", "Example low", "12:34", "2026-01-01", 1, "a", 1.0, "note", "2026-01-01T12:34:56", "5 mg", null)
        val destination = MedicationEntity("destination", "Example high", "10 mg", "Pastilla", 2, "b", 0.0, 1, null, 0)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { mergedHistoryEntry(entry, destination, 2.0) }
        assertEquals(0.5, mergedHistoryEntry(entry, destination, 0.5).quantity, 0.0)
    }

    @Test fun movesEntryWithoutLosingIdentityOrAttachments() {
        val entry = HistoryEntryEntity("entry", "source", "Example low", "12:34", "2026-01-01", 1, "a", 1.0, "note", "2026-01-01T12:34:56", "5 mg", "audio.enc")
        val destination = MedicationEntity("destination", "Example high", "10 mg", "Pastilla", 2, "b", 0.0, 1, null, 0)
        val result = mergedHistoryEntry(entry, destination, 0.5)
        assertEquals(entry.copy(medicationId = "destination", medicationName = "Example high", dose = "10 mg", colorArgb = 2, emoji = "b", quantity = 0.5), result)
    }
}
