package com.d4vram.pillmate2.ui.voicenotes

import android.app.Application
import android.content.Intent
import android.media.MediaPlayer
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.data.voice.VoiceNoteEncryption
import com.d4vram.pillmate2.data.voice.VoiceNoteRepository
import com.d4vram.pillmate2.data.voice.VoiceNoteExporter
import com.d4vram.pillmate2.data.voice.ExportResult
import com.d4vram.pillmate2.model.VoiceNote
import com.d4vram.pillmate2.model.VoiceNoteCategory
import com.d4vram.pillmate2.ui.security.VoiceNoteSecurityManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceNotesViewModel(
    application: Application,
    private val repository: VoiceNoteRepository,
    private val securityManager: VoiceNoteSecurityManager
) : AndroidViewModel(application) {

    private val recorder = VoiceNoteRecorder(application.applicationContext)
    private val exporter = VoiceNoteExporter(application.applicationContext, repository)
    private var recordingTimerJob: Job? = null
    private var currentTempRecording: File? = null
    private var sessionPassword: CharArray? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playbackTempFile: File? = null

    private val _uiState = MutableStateFlow(VoiceNotesUiState())
    val uiState: StateFlow<VoiceNotesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            securityManager.settings.collectLatest { settings ->
                _uiState.update {
                    it.copy(
                        passwordConfigured = settings.isPasswordConfigured,
                        passwordHint = settings.passwordHint,
                        unlocked = it.unlocked && settings.isPasswordConfigured
                    )
                }
                if (!settings.isPasswordConfigured) {
                    lockSession()
                }
            }
        }

        viewModelScope.launch {
            repository.voiceNotes.collectLatest { notes ->
                _uiState.update { it.copy(voiceNotes = notes) }
            }
        }
    }

    fun setPassword(password: String, confirm: String, hint: String?) {
        if (password.isBlank() || confirm.isBlank()) {
            _uiState.update { it.copy(errorMessage = "La contraseña no puede estar vacía") }
            return
        }
        if (password != confirm) {
            _uiState.update { it.copy(errorMessage = "Las contraseñas no coinciden") }
            return
        }
        viewModelScope.launch {
            try {
                securityManager.setPassword(password, hint)
                replaceSessionPassword(password.toCharArray())
                _uiState.update {
                    it.copy(
                        unlocked = true,
                        infoMessage = "Contraseña configurada",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Error al guardar la contraseña") }
            }
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch {
            val valid = try {
                securityManager.verifyPassword(password)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "Error al verificar la contraseña") }
                return@launch
            }
            if (valid) {
                replaceSessionPassword(password.toCharArray())
                _uiState.update { it.copy(unlocked = true, errorMessage = null, infoMessage = "Notas desbloqueadas") }
            } else {
                _uiState.update { it.copy(errorMessage = "Contraseña incorrecta") }
            }
        }
    }

    fun lockSession() {
        VoiceNoteEncryption.wipePassword(sessionPassword)
        sessionPassword = null
        stopPlayback()
        _uiState.update { it.copy(unlocked = false) }
    }
    
    fun lockFromAppSecurity() {
        lockSession()
    }

    fun startRecording() {
        if (!_uiState.value.unlocked) {
            _uiState.update { it.copy(errorMessage = "Desbloquea las notas primero") }
            return
        }
        if (_uiState.value.isRecording) return

        try {
            currentTempRecording = recorder.startRecording()
            _uiState.update { it.copy(isRecording = true, recordingElapsedMillis = 0L, errorMessage = null) }
            recordingTimerJob?.cancel()
            recordingTimerJob = viewModelScope.launch {
                val start = System.currentTimeMillis()
                while (true) {
                    delay(200L)
                    val elapsed = System.currentTimeMillis() - start
                    _uiState.update { state -> state.copy(recordingElapsedMillis = elapsed) }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "No se pudo iniciar la grabación") }
            recorder.cancelRecording()
            currentTempRecording = null
            recordingTimerJob?.cancel()
        }
    }

    fun stopRecording(title: String?) {
        if (!_uiState.value.isRecording) return
        val password = sessionPassword
        if (password == null) {
            _uiState.update { it.copy(errorMessage = "Desbloquea las notas primero") }
            cancelRecording()
            return
        }
        val result = try {
            recorder.stopRecording()
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "No se pudo detener la grabación") }
            recorder.cancelRecording()
            currentTempRecording?.delete()
            currentTempRecording = null
            recordingTimerJob?.cancel()
            _uiState.update { it.copy(isRecording = false, recordingElapsedMillis = 0L) }
            return
        }

        recordingTimerJob?.cancel()
        _uiState.update { it.copy(isRecording = false, recordingElapsedMillis = 0L) }

        viewModelScope.launch {
            try {
                repository.saveVoiceNote(
                    sourceFile = result.file,
                    password = password,
                    durationMillis = result.durationMillis,
                    title = title.orEmpty()
                )
                _uiState.update { it.copy(infoMessage = "Nota de voz guardada", errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "No se pudo guardar la nota") }
                result.file.delete()
            } finally {
                currentTempRecording = null
            }
        }
    }

    fun cancelRecording() {
        recordingTimerJob?.cancel()
        recorder.cancelRecording()
        currentTempRecording?.delete()
        currentTempRecording = null
        _uiState.update { it.copy(isRecording = false, recordingElapsedMillis = 0L) }
    }

    fun playOrToggle(note: VoiceNote) {
        if (!_uiState.value.unlocked) {
            _uiState.update { it.copy(errorMessage = "Desbloquea las notas primero") }
            return
        }
        if (_uiState.value.playingNoteId == note.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        val password = sessionPassword
        if (password == null) {
            _uiState.update { it.copy(errorMessage = "Desbloquea las notas primero") }
            return
        }

        viewModelScope.launch {
            try {
                val tempFile = repository.decryptNoteToTempFile(note, password)
                val player = MediaPlayer()
                player.setDataSource(tempFile.absolutePath)
                player.setOnCompletionListener {
                    stopPlayback()
                }
                player.setOnErrorListener { _, _, _ ->
                    stopPlayback(withError = "Error al reproducir la nota")
                    true
                }
                player.prepare()
                player.start()

                playbackTempFile?.delete()
                playbackTempFile = tempFile
                mediaPlayer = player
                _uiState.update { it.copy(playingNoteId = note.id, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "No se pudo reproducir la nota") }
            }
        }
    }

    fun stopPlayback(withError: String? = null) {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (ignored: Exception) {
            } finally {
                player.reset()
                player.release()
            }
        }
        mediaPlayer = null
        playbackTempFile?.delete()
        playbackTempFile = null
        _uiState.update { it.copy(playingNoteId = null, errorMessage = withError ?: it.errorMessage) }
    }

    fun deleteNote(note: VoiceNote) {
        viewModelScope.launch {
            try {
                if (_uiState.value.playingNoteId == note.id) {
                    stopPlayback()
                }
                repository.deleteVoiceNote(note.id)
                _uiState.update { it.copy(infoMessage = "Nota eliminada") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "No se pudo eliminar la nota") }
            }
        }
    }

    fun renameNote(note: VoiceNote, newTitle: String) {
        viewModelScope.launch {
            try {
                repository.renameVoiceNote(note.id, newTitle)
                _uiState.update { it.copy(infoMessage = "Nota actualizada") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "No se pudo renombrar la nota") }
            }
        }
    }

    fun updateNoteCategory(note: VoiceNote, category: VoiceNoteCategory) {
        viewModelScope.launch {
            try {
                repository.updateVoiceNoteCategory(note.id, category)
                _uiState.update { it.copy(infoMessage = "Categoría actualizada") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "No se pudo actualizar la categoría") }
            }
        }
    }

    fun updateNoteTags(note: VoiceNote, tags: List<String>) {
        viewModelScope.launch {
            try {
                repository.updateVoiceNoteTags(note.id, tags)
                _uiState.update { it.copy(infoMessage = "Etiquetas actualizadas") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "No se pudieron actualizar las etiquetas") }
            }
        }
    }

    fun consumeInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    fun shareNote(note: VoiceNote) {
        if (!_uiState.value.unlocked) {
            _uiState.update { it.copy(errorMessage = "Desbloquea las notas primero") }
            return
        }
        val password = sessionPassword
        if (password == null) {
            _uiState.update { it.copy(errorMessage = "Desbloquea las notas primero") }
            return
        }
        
        viewModelScope.launch {
            try {
                val tempFile = repository.decryptNoteToTempFile(note, password)
                val shareFile = File(getApplication<Application>().cacheDir, "shared_${note.title.replace("[^a-zA-Z0-9]".toRegex(), "_")}.m4a")
                tempFile.copyTo(shareFile, overwrite = true)
                tempFile.delete()
                
                val uri = FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.provider",
                    shareFile
                )
                
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, note.title)
                    putExtra(Intent.EXTRA_TEXT, "Nota de voz: ${note.title}")
                    type = "audio/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooserIntent = Intent.createChooser(shareIntent, "Compartir nota de voz")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(chooserIntent)
                
                _uiState.update { it.copy(infoMessage = "Preparando para compartir...") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "No se pudo compartir la nota") }
            }
        }
    }
    
    fun exportAllNotes() {
        if (!_uiState.value.unlocked) {
            _uiState.update { it.copy(errorMessage = "Desbloquea las notas primero") }
            return
        }
        val password = sessionPassword
        if (password == null) {
            _uiState.update { it.copy(errorMessage = "Desbloquea las notas primero") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, infoMessage = "Exportando notas...") }
            
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val exportFile = File(getApplication<Application>().getExternalFilesDir(null), "notas_voz_backup_$timestamp.zip")
                
                when (val result = exporter.exportAllNotesToZip(password, exportFile)) {
                    is ExportResult.Success -> {
                        // Compartir el archivo ZIP
                        val uri = FileProvider.getUriForFile(
                            getApplication(),
                            "${getApplication<Application>().packageName}.provider",
                            result.file
                        )
                        
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "Backup de Notas de Voz")
                            putExtra(Intent.EXTRA_TEXT, "Archivo de respaldo con ${result.noteCount} notas de voz cifradas")
                            type = "application/zip"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        val chooserIntent = Intent.createChooser(shareIntent, "Exportar backup de notas")
                        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        getApplication<Application>().startActivity(chooserIntent)
                        
                        _uiState.update { 
                            it.copy(
                                isExporting = false, 
                                infoMessage = "Backup exportado: ${result.noteCount} notas"
                            ) 
                        }
                    }
                    is ExportResult.Error -> {
                        _uiState.update { 
                            it.copy(
                                isExporting = false, 
                                errorMessage = result.message
                            ) 
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isExporting = false, 
                        errorMessage = e.localizedMessage ?: "Error al exportar"
                    ) 
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingTimerJob?.cancel()
        recorder.cancelRecording()
        stopPlayback()
        VoiceNoteEncryption.wipePassword(sessionPassword)
        sessionPassword = null
    }

    private fun replaceSessionPassword(newPassword: CharArray) {
        VoiceNoteEncryption.wipePassword(sessionPassword)
        sessionPassword = newPassword
    }

    data class VoiceNotesUiState(
        val passwordConfigured: Boolean = false,
        val unlocked: Boolean = false,
        val passwordHint: String? = null,
        val isRecording: Boolean = false,
        val recordingElapsedMillis: Long = 0L,
        val playingNoteId: String? = null,
        val voiceNotes: List<VoiceNote> = emptyList(),
        val infoMessage: String? = null,
        val errorMessage: String? = null,
        val isExporting: Boolean = false
    )

    companion object {
        fun provideFactory(
            application: Application,
            repository: VoiceNoteRepository,
            securityManager: VoiceNoteSecurityManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(VoiceNotesViewModel::class.java)) {
                    return VoiceNotesViewModel(application, repository, securityManager) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
