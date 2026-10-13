package com.d4vram.pillmate2.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.ui.settings.AppLanguageOption
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppLanguageDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun selectingEnglishReturnsEnglishOption() {
        var selected = AppLanguageOption.SYSTEM
        compose.setContent {
            MaterialTheme {
                AppLanguageDialog(
                    selected = selected,
                    onSelect = { selected = it },
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.settings_language_english)).performClick()

        compose.runOnIdle { assertEquals(AppLanguageOption.ENGLISH, selected) }
    }

    @Test
    fun selectingDeviceLanguageReturnsSystemOption() {
        var selected = AppLanguageOption.ENGLISH
        compose.setContent {
            MaterialTheme {
                AppLanguageDialog(
                    selected = selected,
                    onSelect = { selected = it },
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.settings_language_device)).performClick()

        compose.runOnIdle { assertEquals(AppLanguageOption.SYSTEM, selected) }
    }

    @Test
    fun selectingSpanishReturnsSpanishOption() {
        var selected = AppLanguageOption.ENGLISH
        compose.setContent {
            MaterialTheme {
                AppLanguageDialog(
                    selected = selected,
                    onSelect = { selected = it },
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.settings_language_spanish)).performClick()

        compose.runOnIdle { assertEquals(AppLanguageOption.SPANISH, selected) }
    }
}
