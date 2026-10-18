package com.d4vram.pillmate2.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.data.local.HistoryEntryEntity
import com.d4vram.pillmate2.data.local.MedicationEntity
import com.d4vram.pillmate2.data.local.PillMateDatabase
import com.d4vram.pillmate2.utils.formatQuantity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MedicationMergeDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: PillMateDatabase
    private lateinit var repository: MedicationRepository
    private val job = SupervisorJob()

    @Before fun prepareFixture() = runBlocking {
        check(context.packageName == "dev.d4vram.pillmate.uitest")
        database = Room.inMemoryDatabaseBuilder(context, PillMateDatabase::class.java).build()
        database.medicationDao().upsertMedications(listOf(
            medication("source", "Example low", "5 mg", 0),
            medication("destination", "Example high", "10 mg", 1)
        ))
        database.historyDao().insertAll(listOf(
            entry("source-one", "source", "Example low", "5 mg"),
            entry("source-two", "source", "Example low", "5 mg"),
            entry("destination-one", "destination", "Example high", "10 mg")
        ))
        repository = MedicationRepository(database, database.medicationDao(), database.historyDao(),
            CoroutineScope(job + Dispatchers.IO))
    }

    @After fun closeFixture() = runBlocking {
        job.cancelAndJoin()
        if (::database.isInitialized) database.close()
    }

    @Test fun previewExplainsRecordConservationAndRejectsWrongFactor() {
        compose.setContent { MaterialTheme { MedicationMergeDialog(repository, onDismiss = {}) } }
        val choose = context.getString(R.string.merge_choose)
        compose.onAllNodesWithText(choose)[0].performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Example low · 5 mg").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Example low · 5 mg").performClick()
        compose.onNodeWithText(choose).performScrollTo().performClick()
        compose.onNodeWithText("Example high · 10 mg").performClick()

        val preview = context.getString(R.string.merge_preview, 2, 1, 3, 3)
        val example = context.getString(R.string.merge_example,
            "Example low", "5 mg", "Example high", "10 mg", formatQuantity(0.5))
        compose.onNodeWithText(example).assertExists()
        compose.onNodeWithText(preview).assertExists()
        compose.onNodeWithText(context.getString(R.string.merge_action)).assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("2")
        compose.onNodeWithText(context.getString(R.string.merge_factor_mismatch)).assertExists()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.merge_action)).assertIsNotEnabled()
        runBlocking {
            assertEquals(2, database.medicationDao().getAllMedications().size)
            assertEquals(setOf("source-one", "source-two", "destination-one"),
                database.historyDao().getAllHistory().map { it.id }.toSet())
        }
    }

    private fun medication(id: String, name: String, dose: String, order: Int) = MedicationEntity(
        id = id, name = name, dose = dose, type = "Pastilla", colorArgb = 0, emoji = "",
        takenToday = 0.0, dailyGoal = 1, lastTaken = null, sortOrder = order
    )

    private fun entry(id: String, medicationId: String, name: String, dose: String) = HistoryEntryEntity(
        id = id, medicationId = medicationId, medicationName = name, dose = dose,
        time = "12:34", date = "01/01/2026", colorArgb = 0, emoji = "", quantity = 1.0,
        notes = "Synthetic fixture", timestampIso = "2026-01-01T12:34:56"
    )
}
