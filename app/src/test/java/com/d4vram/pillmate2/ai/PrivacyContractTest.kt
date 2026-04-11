package com.d4vram.pillmate2.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyContractTest {

    @Test
    fun `manifest allows explicit model download but still forbids telemetry state permission`() {
        val manifest = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }

        assertTrue("No se encontró AndroidManifest.xml", manifest != null)

        val content = manifest!!.readText()
        assertTrue(content.contains("android.permission.INTERNET"))
        assertFalse(content.contains("android.permission.ACCESS_NETWORK_STATE"))
    }
}
