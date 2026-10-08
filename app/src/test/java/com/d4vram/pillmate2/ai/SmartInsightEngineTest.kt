package com.d4vram.pillmate2.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartInsightEngineTest {

    @Test
    fun `fallback answer cites numbers without forcing pbl sections`() {
        val response = SmartInsightEngine().generateInsight(
            question = "¿Qué día fue el más activo?",
            snapshot = snapshot
        )

        assertTrue(response.contains("42 mg"))
        assertTrue(response.contains("+12%"))
        assertTrue(!response.contains("P:"))
    }

    @Test
    fun `adherence estimate follows the real consistency labels`() {
        val engine = SmartInsightEngine()
        val expected = mapOf(
            LoggingConsistency.VERY_FREQUENT to "90%",
            LoggingConsistency.FREQUENT to "70%",
            LoggingConsistency.INFREQUENT to "40%",
            LoggingConsistency.SPARSE to "20%"
        )
        expected.forEach { (consistency, score) ->
            val response = engine.generateInsight("resumen", snapshot.copy(loggingConsistency = consistency.label))
            assertTrue("$consistency -> $response", response.contains(score))
        }
    }

    @Test
    fun `forgetfulness question warns only when logging is infrequent`() {
        val engine = SmartInsightEngine()
        LoggingConsistency.entries.forEach { consistency ->
            val response = engine.generateInsight("¿tengo algún olvido?", snapshot.copy(loggingConsistency = consistency.label))
            assertEquals("$consistency -> $response", consistency.isLow, response.contains("Detección de Riesgos"))
        }
    }

    @Test
    fun `day ratio thresholds map to consistency levels`() {
        assertEquals(LoggingConsistency.VERY_FREQUENT, LoggingConsistency.fromDayRatio(0.8f))
        assertEquals(LoggingConsistency.FREQUENT, LoggingConsistency.fromDayRatio(0.5f))
        assertEquals(LoggingConsistency.INFREQUENT, LoggingConsistency.fromDayRatio(0.2f))
        assertEquals(LoggingConsistency.SPARSE, LoggingConsistency.fromDayRatio(0f))
    }

    private val snapshot = TreatmentStatsSnapshot(
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
}
