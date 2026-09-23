package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.d4vram.pillmate2.ui.theme.AppUiStyle
import com.d4vram.pillmate2.ui.theme.LocalUiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomePolishTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchStaysBelowAddButtonWithLargeText() {
        var added = false
        var closed = false
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale = 1.5f),
                LocalUiStyle provides AppUiStyle.MaterialYou
            ) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    Column(Modifier.width(320.dp)) {
                        HeaderSection(
                            onAddMedication = { added = true },
                            searchQuery = "Paracetamol",
                            searchActive = true,
                            onSearchQueryChange = {},
                            onSearchActiveChange = { closed = !it }
                        )
                    }
                }
            }
        }
        val search = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithContentDescription("Agregar medicamento")
        assertTrue("Search must not overlap the add button", search.top >= add.fetchSemanticsNode().boundsInRoot.bottom)
        add.performClick()
        compose.onNodeWithContentDescription("Cerrar búsqueda").performClick()
        compose.runOnIdle { assertTrue(added); assertTrue(closed) }
    }

    @Test fun noResultsOffersWorkingClearAction() {
        var clicks = 0
        compose.setContent { MaterialTheme { EmptyMedicationState(true, { clicks++ }) } }
        compose.onNodeWithText("Sin resultados").assertIsDisplayed()
        compose.onNodeWithText("Limpiar búsqueda").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun emptyCollectionOffersWorkingAddAction() {
        var clicks = 0
        compose.setContent { MaterialTheme { EmptyMedicationState(false, { clicks++ }) } }
        compose.onNodeWithText("Tu primer medicamento").assertIsDisplayed()
        compose.onNodeWithText("Añadir medicamento").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
