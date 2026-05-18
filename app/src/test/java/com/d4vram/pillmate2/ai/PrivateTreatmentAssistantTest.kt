package com.d4vram.pillmate2.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateTreatmentAssistantTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun throwingEngine(): LlmEngine = object : LlmEngine {
        override suspend fun generate(prompt: String, modelPath: String): String =
            throw RuntimeException("Simulated LLM crash")
        override fun close() {}
    }

    private fun successEngine(response: String = "Respuesta del modelo"): LlmEngine = object : LlmEngine {
        override suspend fun generate(prompt: String, modelPath: String): String = response
        override fun close() {}
    }

    private fun availableLocator(path: String): ModelLocator = ModelLocator {
        LocalModelStatus(activeModelPath = path, candidatePaths = listOf(path))
    }

    private fun unavailableLocator(): ModelLocator = ModelLocator {
        LocalModelStatus(activeModelPath = null, candidatePaths = emptyList())
    }

    @Test
    fun `cuando LLM lanza excepcion, reply tiene isFallback true y failureHint no nulo`() = runBlocking {
        val modelFile = tempFolder.newFile("model.task")
        val assistant = PrivateTreatmentAssistant(
            modelLocator = availableLocator(modelFile.absolutePath),
            llmEngine = throwingEngine()
        )

        val reply = assistant.reply("test", TreatmentStatsSnapshot.Empty, emptyList())

        assertTrue("isFallback debe ser true cuando LLM falla", reply.isFallback)
        assertNotNull("failureHint no debe ser null cuando LLM falla", reply.failureHint)
    }

    @Test
    fun `cuando LLM responde bien, reply no tiene failureHint`() = runBlocking {
        val modelFile = tempFolder.newFile("model.task")
        val assistant = PrivateTreatmentAssistant(
            modelLocator = availableLocator(modelFile.absolutePath),
            llmEngine = successEngine()
        )

        val reply = assistant.reply("test", TreatmentStatsSnapshot.Empty, emptyList())

        assertFalse("isFallback debe ser false cuando LLM responde", reply.isFallback)
        assertNull("failureHint debe ser null en respuesta exitosa", reply.failureHint)
    }

    @Test
    fun `cuando no hay modelo disponible, fallback no tiene failureHint`() = runBlocking {
        val assistant = PrivateTreatmentAssistant(
            modelLocator = unavailableLocator(),
            llmEngine = successEngine()
        )

        val reply = assistant.reply("test", TreatmentStatsSnapshot.Empty, emptyList())

        assertTrue("isFallback debe ser true sin modelo", reply.isFallback)
        assertNull("failureHint debe ser null en fallback por diseño (no por error)", reply.failureHint)
    }
}
