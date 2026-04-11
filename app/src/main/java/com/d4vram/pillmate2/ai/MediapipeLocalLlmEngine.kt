package com.d4vram.pillmate2.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediapipeLocalLlmEngine(context: Context) : AutoCloseable {

    private val appContext = context.applicationContext
    private val lock = Any()
    private var currentModelPath: String? = null
    private var currentEngine: LlmInference? = null

    suspend fun generate(prompt: String, modelPath: String): String = withContext(Dispatchers.IO) {
        val engine = synchronized(lock) {
            if (currentEngine == null || currentModelPath != modelPath) {
                currentEngine?.close()
                currentEngine = createEngine(modelPath)
                currentModelPath = modelPath
            }
            currentEngine
        } ?: error("No se pudo inicializar el motor local.")

        engine.generateResponse(prompt).trim()
    }

    override fun close() {
        synchronized(lock) {
            currentEngine?.close()
            currentEngine = null
            currentModelPath = null
        }
    }

    private fun createEngine(modelPath: String): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setMaxTopK(32)
            .build()

        return LlmInference.createFromOptions(appContext, options)
    }
}
