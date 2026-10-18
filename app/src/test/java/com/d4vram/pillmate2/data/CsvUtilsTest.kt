package com.d4vram.pillmate2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvUtilsTest {
    @Test
    fun parseCsv_invalidFileReturnsLocalizedErrorCode() {
        val error = try {
            CsvUtils.parseCsv("not a PillMate export")
            null
        } catch (exception: CsvImportException) {
            exception.reason
        }

        assertEquals(CsvImportError.INVALID_FORMAT, error)
    }

    @Test
    fun parseCsv_supportsHistoryRowsWithoutDoseColumn() {
        val csv = """
            # PillMate CSV v1
            [MEDICATIONS]
            id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead
            med-1,Ibuprofeno,400mg,Pastilla,-1,💊,0,1,,0,,,

            [HISTORY]
            id,medicationId,medicationName,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
            hist-1,med-1,Ibuprofeno,08:00,06/06/2026,-1,💊,2,Con comida,2026-06-06T08:00:00,audio-1
        """.trimIndent()

        val parsed = CsvUtils.parseCsv(csv)

        assertEquals(1, parsed.history.size)
        val entry = parsed.history.first()
        assertNull(entry.dose)
        assertEquals("08:00", entry.time)
        assertEquals("06/06/2026", entry.date)
        assertEquals(2.0, entry.quantity, 0.0)
        assertEquals("audio-1", entry.audioNoteUri)
    }

    @Test
    fun parseCsv_supportsHistoryRowsWithDoseColumn() {
        val csv = """
            # PillMate CSV v1
            [MEDICATIONS]
            id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead

            [HISTORY]
            id,medicationId,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
            hist-1,med-1,Ibuprofeno,400mg,08:00,06/06/2026,-1,💊,2,Con comida,2026-06-06T08:00:00,audio-1
        """.trimIndent()

        val parsed = CsvUtils.parseCsv(csv)

        val entry = parsed.history.first()
        assertEquals("400mg", entry.dose)
        assertEquals("08:00", entry.time)
        assertEquals("audio-1", entry.audioNoteUri)
    }

    @Test
    fun parseCsv_newFormatParsesMedicationWithoutIsPrn() {
        val csv = """
            # PillMate CSV v1
            [MEDICATIONS]
            id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead
            med-1,Omeprazol,20mg,Cápsula,-8355712,💊,2,1,08:15,0,08:00,L|M|X|J|V,,

            [HISTORY]
            id,medicationId,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
        """.trimIndent()

        val parsed = CsvUtils.parseCsv(csv)

        val med = parsed.medications.first()
        assertEquals("med-1", med.id)
        assertEquals("Omeprazol", med.name)
        assertEquals(2.0, med.takenToday, 0.0)
        assertEquals("08:15", med.lastTaken)
        assertEquals(false, med.reminderEnabled)
        assertEquals(0L, med.createdAt)
        assertNull(med.parentMedicationId)
        assertEquals(listOf("L", "M", "X", "J", "V"), med.scheduleDays)
    }

    @Test
    fun parseCsv_legacyIsPrnColumnIsIgnored() {
        val csv = """
            # PillMate CSV v1
            [MEDICATIONS]
            id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,isPrn,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead
            med-1,Omeprazol,20mg,Cápsula,-8355712,💊,2,1,08:15,false,0,08:00,L|M|X|J|V,,

            [HISTORY]
            id,medicationId,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
        """.trimIndent()

        val parsed = CsvUtils.parseCsv(csv)

        val med = parsed.medications.first()
        assertEquals("med-1", med.id)
        assertEquals(2.0, med.takenToday, 0.0)
        assertEquals(0, med.sortOrder)
        assertEquals(listOf("L", "M", "X", "J", "V"), med.scheduleDays)
    }
    @Test
    fun fractionalBackupRoundTripPreservesConvertedReferencesAndMetadata() {
        val medication = com.d4vram.pillmate2.model.Medication(
            id = "target", name = "Example tablet", dose = "20 mg", type = "Pastilla",
            color = androidx.compose.ui.graphics.Color.Green, emoji = "T", takenToday = 1.75,
            reminderEnabled = true, createdAt = 1792012345000L, parentMedicationId = "family",
            scheduleTime = "08:00", scheduleDays = listOf("L", "V"), dailyGoal = 2,
            lastTaken = "Hoy 08:00", sortOrder = 3, usePhotoInstead = true
        )
        val entry = com.d4vram.pillmate2.model.HistoryEntry(
            id = "original-entry", medicationId = medication.id, medicationName = medication.name,
            dose = medication.dose, time = "08:00", date = "2026-10-15",
            color = medication.color, emoji = medication.emoji, quantity = 0.5,
            notes = "Retained note", timestamp = java.time.LocalDateTime.of(2026, 10, 15, 8, 0),
            audioNoteUri = "voice.enc"
        )
        val parsed = CsvUtils.parseCsv(CsvUtils.buildCsv(listOf(medication), listOf(entry)))
        assertEquals(medication, parsed.medications.single())
        assertEquals(entry, parsed.history.single())
    }

    @Test
    fun fractionalBackupRejectsInvalidHistoryQuantities() {
        val csv = """
            # PillMate CSV v1
            [MEDICATIONS]
            id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead
            [HISTORY]
            id,medicationId,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
            entry,target,Example,20mg,08:00,2026-10-15,-1,T,QUANTITY,,2026-10-15T08:00,
        """.trimIndent()
        for (quantity in listOf("NaN", "Infinity", "-0.5", "0", "bad")) {
            val error = runCatching { CsvUtils.parseCsv(csv.replace("QUANTITY", quantity)) }.exceptionOrNull()
            assertEquals(CsvImportError.INVALID_QUANTITY, (error as CsvImportException).reason)
        }
    }

    @Test
    fun backupRejectsNonfiniteMedicationCounter() {
        val csv = """
            # PillMate CSV v1
            [MEDICATIONS]
            id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead
            target,Example,20mg,Pastilla,-1,T,Infinity,1,,0,,,,false
            [HISTORY]
            id,medicationId,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
        """.trimIndent()
        val error = runCatching { CsvUtils.parseCsv(csv) }.exceptionOrNull()
        assertEquals(CsvImportError.INVALID_QUANTITY, (error as CsvImportException).reason)
    }

    @Test
    fun exportedQuantitiesKeepLegacyIntegersAndLocaleIndependentFractions() {
        val previousLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-ES"))
            val medication = com.d4vram.pillmate2.model.Medication(
                id = "target", name = "Example", dose = "20 mg", type = "Pastilla",
                color = androidx.compose.ui.graphics.Color.Green, emoji = "T", takenToday = 2.0
            )
            val entry = com.d4vram.pillmate2.model.HistoryEntry(
                id = "entry", medicationId = "target", medicationName = "Example",
                time = "08:00", date = "2026-10-15", color = medication.color,
                emoji = "T", quantity = 2.0
            )
            val whole = CsvUtils.buildCsv(listOf(medication), listOf(entry)).lines()
            assertEquals("2", whole[3].split(',')[6])
            assertEquals("2", whole[7].split(',')[8])
            val fractional = CsvUtils.buildCsv(listOf(medication.copy(takenToday = 0.5)), listOf(entry.copy(quantity = 0.5))).lines()
            assertEquals("0.5", fractional[3].split(',')[6])
            assertEquals("0.5", fractional[7].split(',')[8])
        } finally {
            java.util.Locale.setDefault(previousLocale)
        }
    }

}
