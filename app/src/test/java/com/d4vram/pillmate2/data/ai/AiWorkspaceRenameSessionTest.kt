package com.d4vram.pillmate2.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiWorkspaceRenameSessionTest {

    private fun sanitizeSessionTitle(input: String): String =
        input.trim().ifBlank { "Nueva conversación" }

    @Test
    fun `rename keeps meaningful title`() {
        assertEquals("Mi sesión IA", sanitizeSessionTitle("Mi sesión IA"))
    }

    @Test
    fun `rename trims extra spaces`() {
        assertEquals("Sesión limpia", sanitizeSessionTitle("   Sesión limpia   "))
    }

    @Test
    fun `rename falls back when blank`() {
        assertEquals("Nueva conversación", sanitizeSessionTitle("   "))
    }
}

