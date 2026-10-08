package com.d4vram.pillmate2.ai

import kotlin.math.roundToInt

class SmartInsightEngine {

    /**
     * Genera un análisis determinista y educativo basado en los datos reales del tratamiento.
     * Prioriza la precisión y la utilidad sobre la simulación de lenguaje natural.
     */
    fun generateInsight(question: String, snapshot: TreatmentStatsSnapshot): String {
        if (snapshot.totalDosesLogged == 0 && snapshot.recentHistory.isEmpty()) {
            return "Hola. Soy el Analizador de PillMate. No hay registros en el periodo ${snapshot.filterLabel}. " +
                   "Para darte un análisis útil, necesito que registres algunas tomas primero. " +
                   "Por ahora, solo puedo confirmar que el sistema está listo y esperando tus datos."
        }

        val lowerQuestion = question.lowercase()
        
        // 1. Análisis de Adherencia (Estimado basado en regularidad y tendencia)
        val adherenceScore = when (LoggingConsistency.fromLabel(snapshot.loggingConsistency)) {
            LoggingConsistency.VERY_FREQUENT -> 90
            LoggingConsistency.FREQUENT -> 70
            LoggingConsistency.INFREQUENT -> 40
            LoggingConsistency.SPARSE -> 20
            null -> 50
        }

        // 2. Detección de Patrón Dominante
        val strongestSlot = snapshot.hourlyPattern.maxByOrNull { it.value }?.takeIf { it.value > 0 }
        val strongestDay = snapshot.activityTimeline.maxByOrNull { it.valueInUnit }

        // 3. Construcción de la respuesta según la intención
        return when {
            lowerQuestion.contains("resumen") || lowerQuestion.contains("hola") || lowerQuestion.contains("qué tal") -> 
                buildGeneralSummary(snapshot, adherenceScore, strongestSlot)

            lowerQuestion.contains("hora") || lowerQuestion.contains("momento") || lowerQuestion.contains("cuándo") ->
                buildHourlyAnalysis(snapshot, strongestSlot)

            lowerQuestion.contains("olvido") || lowerQuestion.contains("fallo") || lowerQuestion.contains("mal") ->
                buildWarningAnalysis(snapshot)

            lowerQuestion.contains("consejo") || lowerQuestion.contains("profe") || lowerQuestion.contains("ayuda") ->
                buildEducationalTip(snapshot, adherenceScore)

            else -> buildGeneralSummary(snapshot, adherenceScore, strongestSlot)
        }
    }

    private fun buildGeneralSummary(snapshot: TreatmentStatsSnapshot, score: Int, slot: Map.Entry<String, Int>?): String {
        val trendText = when {
            snapshot.loggingTrend.startsWith("+") -> "al alza"
            snapshot.loggingTrend.startsWith("-") -> "a la baja"
            else -> "estable"
        }
        val slotText = slot?.let { " con mayor actividad en la ${it.key.lowercase()}" } ?: ""
        
        return "📊 **Resumen de ${snapshot.scopeLabel}** (${snapshot.filterLabel}):\n\n" +
               "Tu nivel de adherencia estimado es del **$score%**. " +
               "Has registrado un total de ${snapshot.totalValue} ${snapshot.displayUnit}, " +
               "manteniendo una tendencia $trendText de un ${snapshot.loggingTrend}.\n\n" +
               "El patrón actual muestra una consistencia ${snapshot.loggingConsistency.lowercase()}$slotText. " +
               "Último registro: ${snapshot.timeSinceLastRecord}."
    }

    private fun buildHourlyAnalysis(snapshot: TreatmentStatsSnapshot, slot: Map.Entry<String, Int>?): String {
        if (slot == null) return "No tengo suficientes datos horarios para identificar un patrón claro todavía."
        
        return "🕒 **Análisis Horario**:\n\n" +
               "Tu momento de mayor actividad es la **${slot.key}**, donde concentras el ${calculatePercentage(slot.value, snapshot.totalDosesLogged)}% de tus tomas. " +
               "Esto sugiere que tienes una rutina más consolidada en esa franja.\n\n" +
               "💡 *Tip del Profe:* Si tienes medicamentos críticos, intenta programarlos en tu franja más activa para reducir el riesgo de olvidos."
    }

    private fun buildWarningAnalysis(snapshot: TreatmentStatsSnapshot): String {
        val consistency = snapshot.loggingConsistency.lowercase()
        return if (LoggingConsistency.fromLabel(snapshot.loggingConsistency)?.isLow == true) {
            "⚠️ **Detección de Riesgos**:\n\n" +
            "He detectado una regularidad $consistency en los ${snapshot.filterLabel}. " +
            "Los registros muestran lagunas que podrían indicar olvidos o falta de seguimiento de la pauta.\n\n" +
            "¿Te ayudaría que revisemos las alarmas de los medicamentos más frecuentes?"
        } else {
            "✅ **Todo en orden**:\n\n" +
            "No detecto patrones de olvido significativos en este periodo. Tu regularidad es ${snapshot.loggingConsistency.lowercase()}."
        }
    }

    private fun buildEducationalTip(snapshot: TreatmentStatsSnapshot, score: Int): String {
        return "🎓 **Lección de Salud (PBL)**:\n\n" +
               "Mantener una adherencia del $score% es un gran paso. En farmacología, la 'ventana terapéutica' es el rango en el que el medicamento es eficaz sin ser tóxico. " +
               "Al registrar tus tomas con precisión, estamos asegurándonos de que te mantengas en ese rango óptimo.\n\n" +
               "Tu promedio diario es de ${snapshot.averageDailyDoses} tomas. ¡Sigue así!"
    }

    private fun calculatePercentage(value: Int, total: Int): Int {
        if (total == 0) return 0
        return ((value.toFloat() / total.toFloat()) * 100).roundToInt()
    }
}
