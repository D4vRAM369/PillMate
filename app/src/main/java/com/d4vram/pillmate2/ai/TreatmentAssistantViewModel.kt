package com.d4vram.pillmate2.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TreatmentAssistantUiState(
    val messages: List<AssistantChatMessage> = listOf(
        AssistantChatMessage(
            role = AssistantMessageRole.ASSISTANT,
            content = """
¡Hola! Soy tu Analizador de Salud local. Estoy listo para procesar tus estadísticas y darte reportes detallados sobre adherencia, patrones horarios y regularidad. Todo el análisis se realiza en tu dispositivo con total privacidad. ¿En qué puedo ayudarte hoy?
""".trim()
        )
    ),
    val draft: String = "",
    val sourceLabel: String = "Analizador local privado"
)

class TreatmentAssistantViewModel : ViewModel() {

    private val assistant = PrivateTreatmentAssistant()
    private var latestSnapshot: TreatmentStatsSnapshot = TreatmentStatsSnapshot.Empty

    private val _uiState = MutableStateFlow(TreatmentAssistantUiState())
    val uiState: StateFlow<TreatmentAssistantUiState> = _uiState.asStateFlow()

    fun updateContext(snapshot: TreatmentStatsSnapshot) {
        latestSnapshot = snapshot
    }

    fun updateDraft(newValue: String) {
        _uiState.update { it.copy(draft = newValue) }
    }

    fun sendDraft() {
        val question = _uiState.value.draft.trim()
        if (question.isEmpty()) return
        sendQuestion(question, clearDraft = true)
    }

    fun sendSuggestion(question: String) {
        sendQuestion(question.trim(), clearDraft = false)
    }

    private fun sendQuestion(question: String, clearDraft: Boolean) {
        val userMessage = AssistantChatMessage(role = AssistantMessageRole.USER, content = question)
        val reply = assistant.reply(question = question, snapshot = latestSnapshot)

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage + AssistantChatMessage(
                    role = AssistantMessageRole.ASSISTANT,
                    content = reply.text
                ),
                draft = if (clearDraft) "" else state.draft,
                sourceLabel = reply.sourceLabel
            )
        }
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TreatmentAssistantViewModel() as T
                }
            }
    }
}
