package com.d4vram.pillmate2.ai

import android.content.Context

class PrivateTreatmentAssistant(
    context: Context,
    private val modelLocator: LocalModelLocator = LocalModelLocator(context),
    private val fallbackEngine: PblFallbackEngine = PblFallbackEngine(),
    private val llmEngine: MediapipeLocalLlmEngine = MediapipeLocalLlmEngine(context)
) : AutoCloseable {

    fun inspectModelStatus(): LocalModelStatus = modelLocator.locate()

    suspend fun reply(
        question: String,
        snapshot: TreatmentStatsSnapshot,
        conversation: List<AssistantChatMessage>,
        preferredModelPath: String? = null,
        candidatePaths: List<String> = emptyList()
    ): TreatmentAssistantReply {
        val preferredStatus = preferredModelPath
            ?.takeIf { path -> java.io.File(path).exists() }
            ?.let { activePath ->
                LocalModelStatus(
                    activeModelPath = activePath,
                    candidatePaths = (candidatePaths + activePath).distinct()
                )
            }

        val modelStatus = preferredStatus ?: modelLocator.locate()
        if (!modelStatus.isModelAvailable) {
            return TreatmentAssistantReply(
                text = fallbackEngine.respond(question, snapshot),
                sourceLabel = "Analizador local privado",
                modelStatus = modelStatus,
                isFallback = true
            )
        }

        return try {
            val prompt = TreatmentStatsPromptBuilder.buildPrompt(
                question = question,
                snapshot = snapshot,
                conversation = conversation
            )

            val response = llmEngine.generate(
                prompt = prompt,
                modelPath = requireNotNull(modelStatus.activeModelPath)
            )

            TreatmentAssistantReply(
                text = response.ifBlank { fallbackEngine.respond(question, snapshot) },
                sourceLabel = "LLM local en dispositivo",
                modelStatus = modelStatus,
                isFallback = response.isBlank()
            )
        } catch (_: Throwable) {
            TreatmentAssistantReply(
                text = fallbackEngine.respond(question, snapshot),
                sourceLabel = "Analizador local privado",
                modelStatus = modelStatus,
                isFallback = true
            )
        }
    }

    override fun close() {
        llmEngine.close()
    }
}
