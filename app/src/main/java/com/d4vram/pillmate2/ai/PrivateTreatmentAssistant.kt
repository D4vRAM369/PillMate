package com.d4vram.pillmate2.ai

class PrivateTreatmentAssistant(
    private val insightEngine: SmartInsightEngine = SmartInsightEngine()
) {
    fun reply(question: String, snapshot: TreatmentStatsSnapshot): TreatmentAssistantReply {
        return TreatmentAssistantReply(
            text = insightEngine.generateInsight(question, snapshot),
            sourceLabel = "Analizador local privado"
        )
    }
}
