package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import com.d4vram.pillmate2.ui.components.TakeMedicationDialog
import com.d4vram.pillmate2.ui.theme.AppDesign
import com.d4vram.pillmate2.ui.theme.LocalAppDesign
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.ui.quicktake.QuickTapPulseBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AstraHomeTest {
    @get:Rule val compose = createComposeRule()
    private val medication = Medication("id", "Prueba", "10 mg", "pastilla", Color.Blue, "💊")

    @Test fun quantityCannotDropBelowOneAndBusyCannotSave() {
        var saved = 0
        compose.setContent { MaterialTheme { AstraDoseSheet(medication, busy = true, onDismiss = {}, onConfirm = { _, _ -> saved++ }) } }
        compose.onNodeWithContentDescription("Reducir cantidad").assertIsNotEnabled()
        compose.onNodeWithText("Guardando…").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, saved) }
    }

    @Test fun quantityStepperSavesSelectedInteger() {
        var saved = 0
        compose.setContent { MaterialTheme { AstraDoseSheet(medication, busy = false, onDismiss = {}, onConfirm = { quantity, _ -> saved = quantity }) } }
        compose.onNodeWithContentDescription("Reducir cantidad").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Aumentar cantidad").performClick()
        compose.onNodeWithText("Registrar 2 unidades").performClick()
        compose.runOnIdle { assertEquals(2, saved) }
    }

    @Test fun catalogueDialogCannotConfirmWhileAnotherTakeIsSaving() {
        var saved = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppDesign provides AppDesign.Astra) {
                MaterialTheme {
                    TakeMedicationDialog(medication, listOf(medication), {}, { _, _, _ -> saved++ }, busy = true)
                }
            }
        }
        compose.onNodeWithText("Guardando…").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Aumentar cantidad").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, saved) }
    }

    @Test fun allFourEmptySlotsCanBeConfigured() {
        var slot = -1
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { QuickTapPulseBar(List(4) { null }, false, {}, {}, { slot = it }) } } }
        for (index in 0..3) {
            compose.onNodeWithContentDescription("Configurar casilla ${index + 1}").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(index, slot) }
        }
    }
}
