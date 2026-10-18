package com.d4vram.pillmate2.ui.stats

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.data.local.MedicationEntity
import com.d4vram.pillmate2.data.local.PillMateDatabase
import com.d4vram.pillmate2.ui.stats.viewmodel.FilterRange
import com.d4vram.pillmate2.ui.stats.viewmodel.StatisticsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class StatisticsEventCountTest {
    @Test
    fun oneMultiUnitEntryCountsAsOneTakeButKeepsItsAmount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, PillMateDatabase::class.java).build()
        val scopeJob = SupervisorJob()
        val scope = CoroutineScope(scopeJob + Dispatchers.IO)
        try {
            val medicationDao = database.medicationDao()
            val historyDao = database.historyDao()
            medicationDao.upsertMedication(
                MedicationEntity(
                    id = "valium",
                    name = "Valium",
                    dose = "5 mg",
                    type = "Pastilla",
                    colorArgb = 0,
                    emoji = "💊",
                    takenToday = 0.0,
                    dailyGoal = 0,
                    lastTaken = null,
                    sortOrder = 0
                )
            )
            medicationDao.upsertMedication(
                MedicationEntity(
                    id = "noctamid",
                    name = "Noctamid",
                    dose = "1 mg",
                    type = "Gota",
                    colorArgb = 0,
                    emoji = "💧",
                    takenToday = 0.0,
                    dailyGoal = 0,
                    lastTaken = null,
                    sortOrder = 1
                )
            )
            val repository = MedicationRepository(database, medicationDao, historyDao, scope)
            val now = LocalDateTime.now().withSecond(0).withNano(0)
            repository.recordMedicationTaken("valium", quantity = 2.0, customTimestamp = now)
            repository.recordMedicationTaken("noctamid", quantity = 9.0, customTimestamp = now.plusMinutes(5))
            withTimeout(5_000) {
                repository.medications.first { it.size == 2 }
            }

            val viewModel = StatisticsViewModel(repository)
            viewModel.setFilter(FilterRange.ALL_TIME)
            withTimeout(5_000) {
                viewModel.uiState.first { it.periodEntries.size == 2 }
            }
            assertEquals(2, viewModel.uiState.value.totalDosesLogged)

            viewModel.selectMedication("valium")
            withTimeout(5_000) {
                viewModel.uiState.first { it.selectedMedicationId == "valium" && it.periodEntries.size == 1 }
            }
            val valiumState = viewModel.uiState.value
            assertEquals(1, valiumState.totalDosesLogged)
            assertEquals("10", valiumState.totalInUnit)
            val valiumPoint = valiumState.activityData.first { it.date == now.toLocalDate() }
            assertEquals(1f, valiumPoint.totalValue, 0.001f)
            assertEquals(10f, valiumPoint.valueInUnit, 0.001f)

            viewModel.selectMedication("noctamid")
            withTimeout(5_000) {
                viewModel.uiState.first { it.selectedMedicationId == "noctamid" && it.periodEntries.size == 1 }
            }
            val dropsState = viewModel.uiState.value
            assertEquals(1, dropsState.totalDosesLogged)
            assertEquals("9", dropsState.totalInUnit)
            val dropsPoint = dropsState.activityData.first { it.date == now.toLocalDate() }
            assertEquals(1f, dropsPoint.totalValue, 0.001f)
            assertEquals(9f, dropsPoint.valueInUnit, 0.001f)
        } finally {
            scopeJob.cancelAndJoin()
            database.close()
        }
    }
}
