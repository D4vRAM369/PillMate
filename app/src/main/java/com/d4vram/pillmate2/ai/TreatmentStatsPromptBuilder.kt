package com.d4vram.pillmate2.ai

object TreatmentStatsPromptBuilder {

    private const val SYSTEM_RULES = """
Eres el asistente local y privado de PillMate.
Trabajas exclusivamente con los datos del dispositivo. No uses internet, no inventes fuentes externas y no menciones telemetría.
Tu trabajo es explicar patrones de seguimiento del tratamiento con un tono claro y prudente.
No diagnostiques, no ajustes dosis y no des instrucciones clínicas.
Responde en español natural, con 1 a 3 párrafos o una lista corta solo si aporta claridad.
Incluye cifras concretas cuando existan, explica el patrón dominante y deja claro si faltan datos.
"""

    fun buildPrompt(
        question: String,
        snapshot: TreatmentStatsSnapshot,
        conversation: List<AssistantChatMessage>
    ): String {
        val historyBlock = conversation
            .takeLast(6)
            .joinToString("\n") { message ->
                val speaker = if (message.role == AssistantMessageRole.USER) "Usuario" else "Asistente"
                "$speaker: ${message.content.trim()}"
            }
            .ifBlank { "Sin conversación previa." }

        return buildString {
            appendLine(SYSTEM_RULES.trim())
            appendLine()
            appendLine("CONTEXTO DEL PERIODO")
            appendLine(formatSnapshot(snapshot))
            appendLine()
            appendLine("CONVERSACIÓN RECIENTE")
            appendLine(historyBlock)
            appendLine()
            appendLine("PREGUNTA ACTUAL")
            appendLine(question.trim())
            appendLine()
            append("Responde ahora de forma natural y breve.")
        }
    }

    private fun formatSnapshot(snapshot: TreatmentStatsSnapshot): String = buildString {
        appendLine("- Alcance actual: ${snapshot.scopeLabel}")
        appendLine("- Filtro: ${snapshot.filterLabel}")
        appendLine("- Rango visible: ${snapshot.dateRangeText}")
        appendLine("- Total registrado: ${snapshot.totalValue} ${snapshot.displayUnit}")
        appendLine("- Promedio del periodo: ${snapshot.averageValue} ${snapshot.displayUnit}")
        appendLine("- Total de tomas: ${snapshot.totalDosesLogged}")
        appendLine("- Promedio diario de tomas: ${snapshot.averageDailyDoses}")
        appendLine("- Tiempo desde el último registro: ${snapshot.timeSinceLastRecord}")
        appendLine("- Consistencia: ${snapshot.loggingConsistency}")
        appendLine("- Tendencia: ${snapshot.loggingTrend}")
        appendLine("- Día más activo: ${snapshot.mostActiveDay}")
        appendLine("- Día menos activo: ${snapshot.leastActiveDay}")
        appendLine("- Resumen narrativo: ${snapshot.narrativeSummary}")

        if (snapshot.topMedications.isNotEmpty()) {
            appendLine("- Medicamentos destacados: ${snapshot.topMedications.joinToString()}")
        }

        if (snapshot.hourlyPattern.isNotEmpty()) {
            val hourlySummary = snapshot.hourlyPattern.entries.joinToString { "${it.key}: ${it.value}" }
            appendLine("- Distribución horaria: $hourlySummary")
        }

        if (snapshot.doseBreakdown.isNotEmpty()) {
            val breakdownSummary = snapshot.doseBreakdown.joinToString { item ->
                "${item.medicationName} ${item.dose} (${formatDecimal(item.totalValue)} ${item.displayUnit})"
            }
            appendLine("- Desglose de dosis: $breakdownSummary")
        }

        if (snapshot.recentHistory.isNotEmpty()) {
            appendLine("- Historial reciente:")
            snapshot.recentHistory.forEach { entry ->
                appendLine("  - ${entry.dateLabel}: ${entry.medicationName} ${entry.displayValue} ${entry.displayUnit}")
            }
        }

        if (snapshot.activityTimeline.isNotEmpty()) {
            val timelineSummary = snapshot.activityTimeline.joinToString { point ->
                "${point.fullDate}=${formatDecimal(point.valueInUnit)}"
            }
            appendLine("- Serie temporal: $timelineSummary")
        }
    }

    private fun formatDecimal(value: Float): String {
        return if (value == value.toInt().toFloat()) value.toInt().toString() else String.format("%.1f", value)
    }
}
