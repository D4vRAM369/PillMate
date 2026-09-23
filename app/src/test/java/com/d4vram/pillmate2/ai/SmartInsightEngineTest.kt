package com.d4vram.pillmate2.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class SmartInsightEngineTest {

    @Test
    fun `fallback answer cites numbers without forcing pbl sections`() {
        val snapshot = TreatmentStatsSnapshot(
            scopeLabel = "Ibuprofeno/Paracetamol",
            filterLabel = "Últimos 7 días",
            dateRangeText = "01/04/2026 - 07/04/2026",
            displayUnit = "mg",
            totalValue = "42",
            averageValue = "6",
            totalDosesLogged = 7,
            averageDailyDoses = "1.0",
            timeSinceLastRecord = "Hoy",
            loggingConsistency = "Frecuente",
            loggingTrend = "+12%",
            mostActiveDay = "Vie",
            leastActiveDay = "Lun",
            narrativeSummary = "La actividad ha sido constante.",
            topMedications = listOf("Paracetamol (4)", "Ibuprofeno (3)"),
            hourlyPattern = mapOf("Noche" to 4, "Mañana" to 2),
            recentHistory = emptyList(),
            doseBreakdown = emptyList(),
            activityTimeline = listOf(
                TreatmentTimelinePoint("Lun", "01/04/2026", 4f),
                TreatmentTimelinePoint("Vie", "05/04/2026", 10f)
            )
        )

        val response = SmartInsightEngine().generateInsight(
            question = "¿Qué día fue el más activo?",
            snapshot = snapshot
        )

        assertTrue(response.contains("42 mg"))
        assertTrue(response.contains("+12%"))
        assertTrue(!response.contains("P:"))
    }
}
