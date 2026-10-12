package com.d4vram.pillmate2.ui.theme

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDesignPersistenceTest {
    @Test fun switchingDesignSurvivesNewManagerAndPreservesPalette() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = ThemeManager(context)
        val original = manager.appDesign.first()
        val palette = manager.uiStyle.first()
        try {
            manager.setAppDesign(AppDesign.Legacy)
            assertEquals(AppDesign.Legacy, ThemeManager(context).appDesign.first())
            manager.setAppDesign(AppDesign.Astra)
            assertEquals(AppDesign.Astra, ThemeManager(context).appDesign.first())
            assertEquals(palette, manager.uiStyle.first())
        } finally {
            manager.setAppDesign(original)
        }
    }
}
