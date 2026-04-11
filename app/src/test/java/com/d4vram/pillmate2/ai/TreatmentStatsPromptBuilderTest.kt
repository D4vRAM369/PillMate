package com.d4vram.pillmate2.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class TreatmentStatsPromptBuilderTest {

    @Test
    fun `prompt embeds privacy contract and current stats context`() {
        val snapshot = TreatmentStatsSnapshot(
            scopeLabel = "Todos",
            filterLabel = "Últimos 30 días",
            dateRangeText = "01/03/2026 - 30/03/2026",
            displayUnit = "tomas",
            totalValue = "12",
            averageValue = "0.4",
            totalDosesLogged = 12,
            averageDailyDoses = "0.4",
            timeSinceLastRecord = "Hace 1 día",
            loggingConsistency = "Poco frecuente",
            loggingTrend = "-10%",
            mostActiveDay = "Mar",
            leastActiveDay = "Dom",
            narrativeSummary = "La actividad ha sido puntual.",
            topMedications = listOf("Ibuprofeno (5)"),
            hourlyPattern = mapOf("Mañana" to 5),
            recentHistory = listOf(
                TreatmentHistoryFact("Ibuprofeno", "29/03 08:30", "2", "tomas")
            ),
            doseBreakdown = emptyList(),
            activityTimeline = listOf(
                TreatmentTimelinePoint("Mar", "29/03/2026", 2f)
            )
        )

        val prompt = TreatmentStatsPromptBuilder.buildPrompt(
            question = "Resume este periodo",
            snapshot = snapshot,
            conversation = listOf(
                AssistantChatMessage(role = AssistantMessageRole.USER, content = "Hola")
            )
        )

        assertTrue(prompt.contains("No uses internet"))
        assertTrue(prompt.contains("Responde en español natural"))
        assertTrue(prompt.contains("01/03/2026 - 30/03/2026"))
        assertTrue(prompt.contains("Ibuprofeno"))
    }
}
