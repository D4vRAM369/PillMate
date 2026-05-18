package com.d4vram.pillmate2.ai

interface LlmEngine : AutoCloseable {
    suspend fun generate(prompt: String, modelPath: String): String
}
