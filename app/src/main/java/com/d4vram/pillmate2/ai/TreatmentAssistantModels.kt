package com.d4vram.pillmate2.ai

import com.d4vram.pillmate2.ui.stats.viewmodel.FilterRange
import com.d4vram.pillmate2.ui.stats.viewmodel.StatisticsUiState
import java.util.UUID

enum class AssistantMessageRole {
    USER,
    ASSISTANT
}

data class AssistantChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: AssistantMessageRole,
    val content: String
)

data class TreatmentTimelinePoint(
    val label: String,
    val fullDate: String,
    val valueInUnit: Float
)

data class TreatmentHistoryFact(
    val medicationName: String,
    val dateLabel: String,
    val displayValue: String,
    val displayUnit: String
)

data class TreatmentDoseFact(
    val medicationName: String,
    val dose: String,
    val totalValue: Float,
    val displayUnit: String
)

data class TreatmentStatsSnapshot(
    val scopeLabel: String,
    val filterLabel: String,
    val dateRangeText: String,
    val displayUnit: String,
    val totalValue: String,
    val averageValue: String,
    val totalDosesLogged: Int,
    val averageDailyDoses: String,
    val timeSinceLastRecord: String,
    val loggingConsistency: String,
    val loggingTrend: String,
    val mostActiveDay: String,
    val leastActiveDay: String,
    val narrativeSummary: String,
    val topMedications: List<String>,
    val hourlyPattern: Map<String, Int>,
    val recentHistory: List<TreatmentHistoryFact>,
    val doseBreakdown: List<TreatmentDoseFact>,
    val activityTimeline: List<TreatmentTimelinePoint>
) {
    companion object {
        val Empty = TreatmentStatsSnapshot(
            scopeLabel = "Tratamiento actual",
            filterLabel = "Últimos 7 días",
            dateRangeText = "Sin datos",
            displayUnit = "tomas",
            totalValue = "0",
            averageValue = "0",
            totalDosesLogged = 0,
            averageDailyDoses = "0",
            timeSinceLastRecord = "Sin registros",
            loggingConsistency = "Sin datos",
            loggingTrend = "0%",
            mostActiveDay = "Sin datos",
            leastActiveDay = "Sin datos",
            narrativeSummary = "Todavía no hay suficientes registros en el periodo visible.",
            topMedications = emptyList(),
            hourlyPattern = emptyMap(),
            recentHistory = emptyList(),
            doseBreakdown = emptyList(),
            activityTimeline = emptyList()
        )
    }
}

data class LocalModelStatus(
    val activeModelPath: String?,
    val candidatePaths: List<String>
) {
    val isModelAvailable: Boolean = !activeModelPath.isNullOrBlank()
}

data class TreatmentAssistantReply(
    val text: String,
    val sourceLabel: String,
    val modelStatus: LocalModelStatus,
    val isFallback: Boolean,
    val failureHint: String? = null
)

fun StatisticsUiState.toTreatmentStatsSnapshot(): TreatmentStatsSnapshot {
    val scopeLabel = when {
        showingFamilyStats && familyName.isNotBlank() -> familyName
        selectedMedicationName.isNotBlank() -> selectedMedicationName
        else -> "Tratamiento actual"
    }

    return TreatmentStatsSnapshot(
        scopeLabel = scopeLabel,
        filterLabel = activeFilter.toHumanLabel(),
        dateRangeText = dateRangeText.ifBlank { "Periodo visible" },
        displayUnit = displayUnit,
        totalValue = totalInUnit,
        averageValue = averageInUnit,
        totalDosesLogged = totalDosesLogged,
        averageDailyDoses = averageDailyDoses,
        timeSinceLastRecord = timeSinceLastRecord.ifBlank { "Sin registros" },
        loggingConsistency = loggingConsistency.ifBlank { "Sin datos" },
        loggingTrend = loggingTrend.ifBlank { "0%" },
        mostActiveDay = mostActiveDay.ifBlank { "Sin datos" },
        leastActiveDay = leastActiveDay.ifBlank { "Sin datos" },
        narrativeSummary = narrativeSummary.ifBlank { "Sin narrativa disponible." },
        topMedications = topMedications.map { (name, count, _) -> "$name ($count)" },
        hourlyPattern = hourlyPattern,
        recentHistory = recentHistoryWithUnits.take(6).map { history ->
            TreatmentHistoryFact(
                medicationName = history.entry.medicationName,
                dateLabel = "${history.entry.date} ${history.entry.time}",
                displayValue = history.displayValue,
                displayUnit = history.displayUnit
            )
        },
        doseBreakdown = doseBreakdown.take(6).map { breakdown ->
            TreatmentDoseFact(
                medicationName = breakdown.medicationName,
                dose = breakdown.dose,
                totalValue = breakdown.totalValue,
                displayUnit = breakdown.displayUnit
            )
        },
        activityTimeline = activityData.takeLast(14).map { point ->
            TreatmentTimelinePoint(
                label = point.dayName,
                fullDate = point.fullDate,
                valueInUnit = point.valueInUnit
            )
        }
    )
}

private fun FilterRange.toHumanLabel(): String = when (this) {
    FilterRange.WEEK -> "Últimos 7 días"
    FilterRange.DAYS_15 -> "Últimos 15 días"
    FilterRange.DAYS_30 -> "Últimos 30 días"
    FilterRange.ALL_TIME -> "Todo el tiempo"
}
