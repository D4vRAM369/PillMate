package com.d4vram.pillmate2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.ui.components.TakeMedicationDialog
import org.junit.Rule
import org.junit.Test

class DesignSwitchTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun legacyRestoresOriginalDoseDialogAfterSwitchingBack() {
        val design = mutableStateOf(AppDesign.Legacy)
        val medication = Medication("test", "Ejemplo", "10 mg", "Pastilla", Color.Blue, "")
        compose.setContent {
            CompositionLocalProvider(LocalAppDesign provides design.value) {
                MaterialTheme { TakeMedicationDialog(medication, listOf(medication), {}, { _, _, _ -> }) }
            }
        }
        compose.onNodeWithText(context.getString(R.string.dose_dialog_time_label)).assertExists()
        compose.onNodeWithText(context.getString(R.string.dose_change_datetime)).assertDoesNotExist()
        compose.runOnIdle { design.value = AppDesign.Astra }
        compose.onNodeWithText(context.getString(R.string.dose_change_datetime)).assertExists()
        compose.onNodeWithText(context.getString(R.string.dose_dialog_time_label)).assertDoesNotExist()
        compose.runOnIdle { design.value = AppDesign.Legacy }
        compose.onNodeWithText(context.getString(R.string.dose_dialog_time_label)).assertExists()
        compose.onNodeWithText(context.getString(R.string.dose_change_datetime)).assertDoesNotExist()
    }
}
