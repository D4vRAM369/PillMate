package com.d4vram.pillmate2.ai

class PblFallbackEngine {

    fun respond(question: String, snapshot: TreatmentStatsSnapshot): String {
        if (snapshot.totalDosesLogged == 0 && snapshot.recentHistory.isEmpty()) {
            return "No hay un patrón de tratamiento visible en ${snapshot.dateRangeText} porque todavía no hay registros suficientes. Ahora mismo solo puedo confirmar 0 ${snapshot.displayUnit} registradas y un último estado de ${snapshot.timeSinceLastRecord}. En cuanto haya más datos podré resumir tendencia, horas más activas y regularidad sin salir del dispositivo."
        }

        val lowerQuestion = question.lowercase()
        val strongestSlot = snapshot.hourlyPattern.maxByOrNull { it.value }?.takeIf { it.value > 0 }
        val strongestDay = snapshot.activityTimeline.maxByOrNull { it.valueInUnit }
        val focusLine = when {
            lowerQuestion.contains("hora") || lowerQuestion.contains("momento") ->
                strongestSlot?.let { "La concentración más alta aparece en ${it.key.lowercase()}." }
                    ?: "No hay suficiente distribución horaria para localizar una franja dominante."

            lowerQuestion.contains("día") || lowerQuestion.contains("semana") || lowerQuestion.contains("mes") ->
                strongestDay?.let { "El punto diario más alto del periodo fue ${it.fullDate}." }
                    ?: "No hay suficiente serie temporal para destacar un día concreto."

            lowerQuestion.contains("promedio") || lowerQuestion.contains("media") ->
                "El ritmo medio del periodo se mantiene en ${snapshot.averageValue} ${snapshot.displayUnit}."

            lowerQuestion.contains("constan") || lowerQuestion.contains("regular") ->
                "La regularidad general del periodo se clasifica como ${snapshot.loggingConsistency.lowercase()}."

            else ->
                strongestSlot?.let { "El patrón dominante combina ${snapshot.loggingConsistency.lowercase()} y más actividad en ${it.key.lowercase()}." }
                    ?: "El patrón dominante del periodo es ${snapshot.loggingConsistency.lowercase()} con una tendencia ${snapshot.loggingTrend}."
        }

        val supportingFacts = buildList {
            add("Total registrado: ${snapshot.totalValue} ${snapshot.displayUnit}")
            add("Promedio del periodo: ${snapshot.averageValue} ${snapshot.displayUnit}")
            add("Promedio diario de tomas: ${snapshot.averageDailyDoses}")
            add("Tendencia: ${snapshot.loggingTrend}")
            add("Último registro: ${snapshot.timeSinceLastRecord}")
            if (snapshot.mostActiveDay != "Sin datos") {
                add("Día más activo: ${snapshot.mostActiveDay}")
            }
            strongestSlot?.let { add("Franja dominante: ${it.key} (${it.value})") }
        }

        val readingLine = buildString {
            append("Esto describe solo el patrón del periodo visible de ${snapshot.scopeLabel}. ")
            append("Si quieres afinar el análisis, puedo comparar horas, revisar la familia de dosis o resumir los registros recientes.")
        }

        val factsLine = supportingFacts
            .take(5)
            .joinToString(separator = "; ") { it }

        return "${snapshot.narrativeSummary} $focusLine $factsLine. $readingLine"
    }
}
