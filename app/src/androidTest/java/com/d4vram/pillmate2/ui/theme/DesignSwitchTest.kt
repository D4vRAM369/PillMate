package com.d4vram.pillmate2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.ui.components.TakeMedicationDialog
import org.junit.Rule
import org.junit.Test

class DesignSwitchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun legacyRestoresOriginalDoseDialogAfterSwitchingBack() {
        val design = mutableStateOf(AppDesign.Legacy)
        val medication = Medication("test", "Ejemplo", "10 mg", "Pastilla", Color.Blue, "")
        compose.setContent {
            CompositionLocalProvider(LocalAppDesign provides design.value) {
                MaterialTheme { TakeMedicationDialog(medication, listOf(medication), {}, { _, _, _ -> }) }
            }
        }
        compose.onNodeWithText("Hora de la toma:").assertExists()
        compose.onNodeWithText("Cambiar fecha y hora").assertDoesNotExist()
        compose.runOnIdle { design.value = AppDesign.Astra }
        compose.onNodeWithText("Cambiar fecha y hora").assertExists()
        compose.onNodeWithText("Hora de la toma:").assertDoesNotExist()
        compose.runOnIdle { design.value = AppDesign.Legacy }
        compose.onNodeWithText("Hora de la toma:").assertExists()
        compose.onNodeWithText("Cambiar fecha y hora").assertDoesNotExist()
    }
}
