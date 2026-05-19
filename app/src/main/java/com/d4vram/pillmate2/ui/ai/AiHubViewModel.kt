package com.d4vram.pillmate2.ui.ai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.ai.AssistantChatMessage
import com.d4vram.pillmate2.ai.AssistantMessageRole
import com.d4vram.pillmate2.ai.LocalModelStatus
import com.d4vram.pillmate2.ai.PrivateTreatmentAssistant
import com.d4vram.pillmate2.ai.TreatmentStatsSnapshot
import com.d4vram.pillmate2.data.ai.AiWorkspaceRepository
import com.d4vram.pillmate2.data.ai.StoredChatSession
import com.d4vram.pillmate2.data.ai.StoredLocalModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiHubUiState(
    val draft: String = "",
    val isGenerating: Boolean = false,
    val sourceLabel: String = "Analizador local privado",
    val activeSessionId: String? = null,
    val sessions: List<StoredChatSession> = emptyList(),
    val messages: List<AssistantChatMessage> = emptyList(),
    val models: List<StoredLocalModel> = emptyList(),
    val activeModelPath: String? = null,
    val candidatePaths: List<String> = emptyList(),
    val showModelSheet: Boolean = false,
    val showSessionSheet: Boolean = false,
    val lastFailureHint: String? = null
)

class AiHubViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = AiWorkspaceRepository(application)
    private val assistant = PrivateTreatmentAssistant(application)
    private var statsSnapshot: TreatmentStatsSnapshot = TreatmentStatsSnapshot.Empty

    private val _uiState = MutableStateFlow(AiHubUiState())
    val uiState: StateFlow<AiHubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.workspace.collect { workspace ->
                val activeSessionId = workspace.activeSessionId ?: workspace.sessions.firstOrNull()?.id
                if (activeSessionId == null) {
                    val firstSessionId = repository.createSession("Sesión inicial")
                    repository.setActiveSession(firstSessionId)
                    return@collect
                }

                val modelStatus = assistant.inspectModelStatus()
                _uiState.update {
                    it.copy(
                        activeSessionId = activeSessionId,
                        sessions = workspace.sessions,
                        messages = repository.currentMessages(activeSessionId),
                        models = workspace.models,
                        activeModelPath = modelStatus.activeModelPath,
                        candidatePaths = modelStatus.candidatePaths
                    )
                }
            }
        }
    }

    fun updateStatisticsContext(snapshot: TreatmentStatsSnapshot) {
        statsSnapshot = snapshot
    }

    fun updateDraft(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    fun createSession(title: String = "Nueva conversación") {
        viewModelScope.launch {
            val id = repository.createSession(title)
            repository.setActiveSession(id)
        }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            repository.setActiveSession(sessionId)
        }
    }

    fun sendDraft() {
        val question = _uiState.value.draft.trim()
        if (question.isBlank() || _uiState.value.isGenerating) return
        sendQuestion(question, clearDraft = true)
    }

    fun sendSuggestion(question: String) {
        if (_uiState.value.isGenerating) return
        sendQuestion(question.trim(), clearDraft = false)
    }

    suspend fun importModel(uri: Uri, displayName: String) {
        repository.importModel(uri, displayName)
        refreshModelStatus()
    }

    suspend fun downloadModel(displayName: String, sourceUrl: String) {
        repository.downloadModel(displayName, sourceUrl)
        refreshModelStatus()
    }

    fun activateModel(modelId: String) {
        viewModelScope.launch {
            repository.activateModel(modelId)
            refreshModelStatus()
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            repository.deleteModel(modelId)
            refreshModelStatus()
        }
    }

    suspend fun importSession(uri: Uri) {
        repository.importSession(uri)
    }

    suspend fun exportCurrentSession(): Uri? {
        val sessionId = _uiState.value.activeSessionId ?: return null
        return repository.exportSession(sessionId)
    }

    fun showModelSheet() {
        _uiState.update { it.copy(showModelSheet = true) }
    }

    fun hideModelSheet() {
        _uiState.update { it.copy(showModelSheet = false) }
    }

    fun showSessionSheet() {
        _uiState.update { it.copy(showSessionSheet = true) }
    }

    fun hideSessionSheet() {
        _uiState.update { it.copy(showSessionSheet = false) }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch {
            repository.renameSession(sessionId, title)
        }
    }

    override fun onCleared() {
        assistant.close()
        super.onCleared()
    }

    private fun sendQuestion(question: String, clearDraft: Boolean) {
        _uiState.update { it.copy(isGenerating = true, draft = if (clearDraft) "" else it.draft) }
        viewModelScope.launch {
            val sessionId = _uiState.value.activeSessionId ?: repository.createSession("Nueva conversación")
            repository.setActiveSession(sessionId)
            repository.appendMessage(sessionId, AssistantMessageRole.USER, question)
            val conversation = repository.currentMessages(sessionId)
            val reply = assistant.reply(question, statsSnapshot, conversation)
            repository.appendMessage(sessionId, AssistantMessageRole.ASSISTANT, reply.text)
            val modelStatus = reply.modelStatus
            _uiState.update {
                it.copy(
                    isGenerating = false,
                    sourceLabel = reply.sourceLabel,
                    lastFailureHint = reply.failureHint,
                    messages = repository.currentMessages(sessionId),
                    activeSessionId = sessionId,
                    activeModelPath = modelStatus.activeModelPath,
                    candidatePaths = modelStatus.candidatePaths
                )
            }
        }
    }

    private suspend fun refreshModelStatus() {
        val modelStatus: LocalModelStatus = assistant.inspectModelStatus()
        _uiState.update {
            it.copy(
                activeModelPath = modelStatus.activeModelPath,
                candidatePaths = modelStatus.candidatePaths
            )
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AiHubViewModel(application) as T
                }
            }
    }
}
