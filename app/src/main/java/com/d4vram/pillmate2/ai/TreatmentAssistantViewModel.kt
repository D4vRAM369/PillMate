package com.d4vram.pillmate2.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TreatmentAssistantUiState(
    val messages: List<AssistantChatMessage> = listOf(
        AssistantChatMessage(
            role = AssistantMessageRole.ASSISTANT,
            content = """
Estoy listo para leer el periodo visible de tus estadísticas de tratamiento. Todo ocurre en local, no hay telemetría y, si todavía no has cargado un modelo, responderé con un analizador privado sin red. Puedes preguntarme por patrón semanal, horas más activas, consistencia o diferencias entre medicamentos.
""".trim()
        )
    ),
    val draft: String = "",
    val isGenerating: Boolean = false,
    val sourceLabel: String = "Analizador local privado",
    val activeModelPath: String? = null,
    val candidatePaths: List<String> = emptyList(),
    val lastFailureHint: String? = null
)

class TreatmentAssistantViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val assistant = PrivateTreatmentAssistant(application)
    private var latestSnapshot: TreatmentStatsSnapshot = TreatmentStatsSnapshot.Empty

    private val _uiState = MutableStateFlow(
        TreatmentAssistantUiState().withModelStatus(assistant.inspectModelStatus())
    )
    val uiState: StateFlow<TreatmentAssistantUiState> = _uiState.asStateFlow()

    fun updateContext(snapshot: TreatmentStatsSnapshot) {
        latestSnapshot = snapshot
        _uiState.update { state ->
            state.withModelStatus(assistant.inspectModelStatus())
        }
    }

    fun updateDraft(newValue: String) {
        _uiState.update { it.copy(draft = newValue) }
    }

    fun sendDraft() {
        val question = _uiState.value.draft.trim()
        if (question.isEmpty() || _uiState.value.isGenerating) return
        sendQuestion(question, clearDraft = true)
    }

    fun sendSuggestion(question: String) {
        if (_uiState.value.isGenerating) return
        sendQuestion(question.trim(), clearDraft = false)
    }

    override fun onCleared() {
        assistant.close()
        super.onCleared()
    }

    private fun sendQuestion(question: String, clearDraft: Boolean) {
        val userMessage = AssistantChatMessage(
            role = AssistantMessageRole.USER,
            content = question
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                draft = if (clearDraft) "" else state.draft,
                isGenerating = true
            )
        }

        viewModelScope.launch {
            val conversation = _uiState.value.messages
            val reply = assistant.reply(
                question = question,
                snapshot = latestSnapshot,
                conversation = conversation
            )

            _uiState.update { state ->
                state.copy(
                    messages = state.messages + AssistantChatMessage(
                        role = AssistantMessageRole.ASSISTANT,
                        content = reply.text
                    ),
                    isGenerating = false,
                    sourceLabel = reply.sourceLabel,
                    lastFailureHint = reply.failureHint
                ).withModelStatus(reply.modelStatus)
            }
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TreatmentAssistantViewModel(application) as T
                }
            }
    }
}

private fun TreatmentAssistantUiState.withModelStatus(
    modelStatus: LocalModelStatus
): TreatmentAssistantUiState {
    return copy(
        activeModelPath = modelStatus.activeModelPath,
        candidatePaths = modelStatus.candidatePaths
    )
}
