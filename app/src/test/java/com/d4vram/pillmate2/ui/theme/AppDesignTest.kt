package com.d4vram.pillmate2.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDesignTest {
    @Test fun storedLegacyCanAlwaysBeRestored() {
        assertEquals(AppDesign.Legacy, AppDesign.fromStorage("legacy"))
        assertEquals(AppDesign.Astra, AppDesign.fromStorage("astra"))
    }
    @Test fun firstLaunchAndUnknownValuesUseAstra() {
        assertEquals(AppDesign.Astra, AppDesign.fromStorage(null))
        assertEquals(AppDesign.Astra, AppDesign.fromStorage("unknown"))
    }
}
