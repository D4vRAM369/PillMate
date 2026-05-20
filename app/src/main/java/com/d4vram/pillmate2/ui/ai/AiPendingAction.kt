package com.d4vram.pillmate2.ui.ai

object AiPendingAction {
    @Volatile var action: String? = null
    fun consume(): String? = action.also { action = null }
}
