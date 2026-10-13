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
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.ui.theme.AppUiStyle
import com.d4vram.pillmate2.ui.theme.LocalUiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomePolishTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

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
        val add = compose.onNodeWithContentDescription(context.getString(R.string.action_add_medication))
        assertTrue("Search must not overlap the add button", search.top >= add.fetchSemanticsNode().boundsInRoot.bottom)
        add.performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.history_search_close)).performClick()
        compose.runOnIdle { assertTrue(added); assertTrue(closed) }
    }

    @Test fun noResultsOffersWorkingClearAction() {
        var clicks = 0
        compose.setContent { MaterialTheme { EmptyMedicationState(true, { clicks++ }) } }
        compose.onNodeWithText(context.getString(R.string.home_empty_no_results)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.home_clear_search)).performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun emptyCollectionOffersWorkingAddAction() {
        var clicks = 0
        compose.setContent { MaterialTheme { EmptyMedicationState(false, { clicks++ }) } }
        compose.onNodeWithText(context.getString(R.string.home_empty_first)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.home_add_medication)).performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
