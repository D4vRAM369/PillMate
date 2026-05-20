package com.d4vram.pillmate2.data.ai

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.d4vram.pillmate2.ai.AssistantChatMessage
import com.d4vram.pillmate2.ai.AssistantMessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class AiWorkspaceRepository(
    context: Context
) {

    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val workspaceDir = File(appContext.filesDir, "ai").apply { mkdirs() }
    private val modelsDir = File(appContext.filesDir, "llm").apply { mkdirs() }
    private val activeModelPointer = File(modelsDir, "active-model-path.txt")
    private val workspaceFile = File(workspaceDir, "workspace.json")

    private val _workspace = MutableStateFlow(loadWorkspace())
    val workspace: StateFlow<AiWorkspaceState> = _workspace.asStateFlow()

    suspend fun createSession(title: String = "Nueva conversación"): String = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        _workspace.update { state ->
            state.copy(
                activeSessionId = sessionId,
                sessions = listOf(
                    StoredChatSession(
                        id = sessionId,
                        title = title,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now
                    )
                ) + state.sessions
            )
        }
        persist()
        sessionId
    }

    suspend fun setActiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        _workspace.update { it.copy(activeSessionId = sessionId) }
        persist()
    }

    suspend fun appendMessage(
        sessionId: String,
        role: AssistantMessageRole,
        content: String
    ) = withContext(Dispatchers.IO) {
        val message = StoredChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = role.name,
            content = content,
            createdAtEpochMillis = System.currentTimeMillis()
        )
        _workspace.update { state ->
            state.copy(
                sessions = state.sessions.map { session ->
                    if (session.id == sessionId) {
                        session.copy(
                            updatedAtEpochMillis = message.createdAtEpochMillis,
                            messages = session.messages + message
                        )
                    } else session
                }
            )
        }
        persist()
    }

    suspend fun importModel(
        sourceUri: Uri,
        displayName: String
    ): StoredLocalModel = withContext(Dispatchers.IO) {
        val safeName = displayName.trim().ifBlank { "modelo-local" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "-")
        val target = File(modelsDir, "$safeName-${System.currentTimeMillis()}.task")
        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se pudo abrir el archivo del modelo.")

        val model = StoredLocalModel(
            id = UUID.randomUUID().toString(),
            displayName = displayName.ifBlank { target.nameWithoutExtension },
            localPath = target.absolutePath,
            sourceUrl = null,
            sizeBytes = target.length(),
            downloadedAtEpochMillis = System.currentTimeMillis(),
            status = ModelStatus.READY
        )
        addModel(model, activate = workspace.value.models.none { it.isActive })
        model
    }

    suspend fun downloadModel(
        displayName: String,
        sourceUrl: String,
        onProgress: (Float) -> Unit = {}
    ): StoredLocalModel = withContext(Dispatchers.IO) {
        val safeName = displayName.trim().ifBlank { "modelo-remoto" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "-")
        val target = File(modelsDir, "$safeName-${System.currentTimeMillis()}.task")

        val connection = URL(sourceUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        connection.requestMethod = "GET"
        connection.connect()
        if (connection.responseCode !in 200..299) {
            error("Descarga fallida (HTTP ${connection.responseCode}). La URL requiere autenticación o no es válida.")
        }

        val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
        var bytesRead = 0L
        val buffer = ByteArray(8192)
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    totalBytes?.let { onProgress(bytesRead.toFloat() / it) }
                }
            }
        }
        connection.disconnect()

        val model = StoredLocalModel(
            id = UUID.randomUUID().toString(),
            displayName = displayName.ifBlank { target.nameWithoutExtension },
            localPath = target.absolutePath,
            sourceUrl = sourceUrl,
            sizeBytes = target.length(),
            downloadedAtEpochMillis = System.currentTimeMillis(),
            status = ModelStatus.READY
        )
        addModel(model, activate = workspace.value.models.none { it.isActive })
        model
    }

    suspend fun activateModel(modelId: String) = withContext(Dispatchers.IO) {
        val active = workspace.value.models.firstOrNull { it.id == modelId } ?: return@withContext
        activeModelPointer.writeText(active.localPath)
        _workspace.update { state ->
            state.copy(
                models = state.models.map { model ->
                    model.copy(isActive = model.id == modelId)
                }
            )
        }
        persist()
    }

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = workspace.value.models.firstOrNull { it.id == modelId } ?: return@withContext
        File(model.localPath).delete()
        _workspace.update { state ->
            state.copy(models = state.models.filterNot { it.id == modelId })
        }
        if (activeModelPointer.exists() && activeModelPointer.readText() == model.localPath) {
            activeModelPointer.delete()
        }
        persist()
    }

    suspend fun exportSession(sessionId: String): Uri = withContext(Dispatchers.IO) {
        val session = workspace.value.sessions.firstOrNull { it.id == sessionId }
            ?: error("No existe la conversación.")
        val file = File(workspaceDir, "${session.title.sanitizeFileName()}-${session.id.take(6)}.json")
        file.writeText(json.encodeToString(session))
        file.toUri()
    }

    suspend fun importSession(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val content = appContext.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
            ?: error("No se pudo leer la conversación.")
        val imported = json.decodeFromString<StoredChatSession>(content)
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val rebuilt = imported.copy(
            id = newId,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            messages = imported.messages.map { msg ->
                msg.copy(id = UUID.randomUUID().toString(), sessionId = newId)
            }
        )
        _workspace.update { state ->
            state.copy(
                activeSessionId = newId,
                sessions = listOf(rebuilt) + state.sessions
            )
        }
        persist()
        newId
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        _workspace.update { state ->
            val remaining = state.sessions.filterNot { it.id == sessionId }
            val newActiveId = if (state.activeSessionId == sessionId) {
                remaining.firstOrNull()?.id
            } else {
                state.activeSessionId
            }
            state.copy(sessions = remaining, activeSessionId = newActiveId)
        }
        persist()
    }

    suspend fun renameSession(sessionId: String, title: String) = withContext(Dispatchers.IO) {
        val newTitle = title.trim().ifBlank { "Nueva conversación" }
        _workspace.update { state ->
            state.copy(
                sessions = state.sessions.map { session ->
                    if (session.id == sessionId) {
                        session.copy(
                            title = newTitle,
                            updatedAtEpochMillis = System.currentTimeMillis()
                        )
                    } else session
                }
            )
        }
        persist()
    }

    fun currentMessages(sessionId: String?): List<AssistantChatMessage> {
        val session = workspace.value.sessions.firstOrNull { it.id == sessionId } ?: return emptyList()
        return session.messages.map { stored ->
            AssistantChatMessage(
                id = stored.id,
                role = AssistantMessageRole.valueOf(stored.role),
                content = stored.content
            )
        }
    }

    private suspend fun addModel(model: StoredLocalModel, activate: Boolean) {
        _workspace.update { state ->
            val existing = if (activate) {
                state.models.map { it.copy(isActive = false) }
            } else {
                state.models
            }
            state.copy(models = listOf(model.copy(isActive = activate)) + existing)
        }
        if (activate) {
            activeModelPointer.writeText(model.localPath)
            _workspace.update { state ->
                state.copy(models = state.models.map { it.copy(isActive = it.id == model.id) })
            }
        }
        persist()
    }

    private fun loadWorkspace(): AiWorkspaceState {
        if (!workspaceFile.exists()) {
            return AiWorkspaceState()
        }
        return runCatching {
            json.decodeFromString<AiWorkspaceState>(workspaceFile.readText())
        }.getOrDefault(AiWorkspaceState())
    }

    private suspend fun persist() = withContext(Dispatchers.IO) {
        workspaceFile.writeText(json.encodeToString(_workspace.value))
    }
}

@Serializable
data class AiWorkspaceState(
    val activeSessionId: String? = null,
    val sessions: List<StoredChatSession> = emptyList(),
    val models: List<StoredLocalModel> = emptyList()
)

@Serializable
data class StoredChatSession(
    val id: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val messages: List<StoredChatMessage> = emptyList()
)

@Serializable
data class StoredChatMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val createdAtEpochMillis: Long
)

@Serializable
data class StoredLocalModel(
    val id: String,
    val displayName: String,
    val localPath: String,
    val sourceUrl: String? = null,
    val sizeBytes: Long = 0L,
    val downloadedAtEpochMillis: Long? = null,
    val status: ModelStatus = ModelStatus.READY,
    val isActive: Boolean = false
)

@Serializable
enum class ModelStatus {
    @SerialName("ready")
    READY
}

private fun String.sanitizeFileName(): String =
    trim()
        .ifBlank { "chat-pillmate-ai" }
        .replace(Regex("[^a-zA-Z0-9._-]"), "-")
