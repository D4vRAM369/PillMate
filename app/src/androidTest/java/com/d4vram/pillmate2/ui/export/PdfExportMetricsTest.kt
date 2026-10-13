package com.d4vram.pillmate2.ui.export

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.data.local.MedicationEntity
import com.d4vram.pillmate2.data.local.PillMateDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PdfExportMetricsTest {
    @Test
    fun reportCountsHistoryEntriesButKeepsAmountsPerMedication() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, PillMateDatabase::class.java).build()
        val scopeJob = SupervisorJob()
        try {
            val medicationDao = database.medicationDao()
            val historyDao = database.historyDao()
            medicationDao.upsertMedication(medication(
                id = "valium", name = "Valium", dose = "5 mg", type = "Pastilla"
            ))
            medicationDao.upsertMedication(medication(
                id = "noctamid", name = "Noctamid", dose = "1 mg", type = "Gota"
            ))
            val repository = MedicationRepository(
                database, medicationDao, historyDao,
                CoroutineScope(scopeJob + Dispatchers.IO)
            )
            val now = LocalDateTime.now().withSecond(0).withNano(0)
            repository.recordMedicationTaken("valium", quantity = 2, customTimestamp = now)
            repository.recordMedicationTaken("noctamid", quantity = 9, customTimestamp = now.plusMinutes(5))
            withTimeout(5_000) { repository.medications.first { it.size == 2 } }

            val data = PdfExportViewModel(repository).reportDataForTesting(
                now.toLocalDate(), now.toLocalDate()
            )
            assertEquals(2, data.totalTomas)
            assertEquals(10f, data.medicationTotals.first { it.medication.id == "valium" }.totalValue, 0.001f)
            assertEquals("mg", data.medicationTotals.first { it.medication.id == "valium" }.displayUnit)
            assertEquals(9f, data.medicationTotals.first { it.medication.id == "noctamid" }.totalValue, 0.001f)
            assertEquals("gotas", data.medicationTotals.first { it.medication.id == "noctamid" }.displayUnit)
            assertEquals(2, data.hourlyPattern.values.sum())
        } finally {
            scopeJob.cancelAndJoin()
            database.close()
        }
    }

    private fun medication(id: String, name: String, dose: String, type: String) = MedicationEntity(
        id = id, name = name, dose = dose, type = type, colorArgb = 0, emoji = "💊",
        takenToday = 0, dailyGoal = 0, lastTaken = null, sortOrder = 0
    )
}
