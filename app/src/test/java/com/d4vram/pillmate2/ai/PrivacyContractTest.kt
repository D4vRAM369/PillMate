package com.d4vram.pillmate2.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyContractTest {

    // Se mira el manifest fusionado, no el de src/main: las librerías añaden permisos por su cuenta
    // (WorkManager traía ACCESS_NETWORK_STATE) y esos solo aparecen tras la fusión.
    @Test
    fun `merged manifest has no network permissions - app is fully offline`() {
        val manifest = File(System.getProperty("mergedManifest") ?: "")

        assertTrue("No se encontró el manifest fusionado: ${manifest.path}", manifest.isFile)

        val content = manifest.readText()
        assertFalse(content.contains("android.permission.INTERNET"))
        assertFalse(content.contains("android.permission.ACCESS_NETWORK_STATE"))
    }
}
