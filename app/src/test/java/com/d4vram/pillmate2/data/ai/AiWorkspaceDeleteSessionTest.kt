package com.d4vram.pillmate2.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiWorkspaceDeleteSessionTest {

    private fun nextActiveSessionId(
        sessions: List<StoredChatSession>,
        deletedId: String,
        currentActiveId: String?
    ): String? {
        val remaining = sessions.filterNot { it.id == deletedId }
        return if (currentActiveId == deletedId) remaining.firstOrNull()?.id
        else currentActiveId
    }

    private fun fakeSession(id: String) = StoredChatSession(
        id = id, title = "Session $id",
        createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L
    )

    @Test
    fun `deleting active session activates the next one`() {
        val sessions = listOf(fakeSession("a"), fakeSession("b"), fakeSession("c"))
        val result = nextActiveSessionId(sessions, deletedId = "a", currentActiveId = "a")
        assertEquals("b", result)
    }

    @Test
    fun `deleting non-active session keeps current active`() {
        val sessions = listOf(fakeSession("a"), fakeSession("b"))
        val result = nextActiveSessionId(sessions, deletedId = "b", currentActiveId = "a")
        assertEquals("a", result)
    }

    @Test
    fun `deleting only session returns null`() {
        val sessions = listOf(fakeSession("a"))
        val result = nextActiveSessionId(sessions, deletedId = "a", currentActiveId = "a")
        assertNull(result)
    }
}
