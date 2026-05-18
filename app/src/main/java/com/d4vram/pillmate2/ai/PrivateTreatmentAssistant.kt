package com.d4vram.pillmate2.ai

import android.content.Context
import android.util.Log

class PrivateTreatmentAssistant(
    private val modelLocator: ModelLocator,
    private val fallbackEngine: PblFallbackEngine = PblFallbackEngine(),
    private val llmEngine: LlmEngine
) : AutoCloseable {

    constructor(context: Context) : this(
        modelLocator = LocalModelLocator(context),
        fallbackEngine = PblFallbackEngine(),
        llmEngine = MediapipeLocalLlmEngine(context)
    )

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
        } catch (t: Throwable) {
            Log.e("PillMateAI", "LLM engine falló: ${t.message}", t)
            TreatmentAssistantReply(
                text = fallbackEngine.respond(question, snapshot),
                sourceLabel = "Analizador local privado",
                modelStatus = modelStatus,
                isFallback = true
                // failureHint se añade en Task 2, cuando el campo exista en TreatmentAssistantReply
            )
        }
    }

    override fun close() {
        llmEngine.close()
    }
}
