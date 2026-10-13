package com.d4vram.pillmate2.ui.voicenotes

import androidx.annotation.StringRes
import com.d4vram.pillmate2.R
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

    // Aquí no hay pantalla: los textos se piden con el contexto de la app (idioma del móvil en ese momento).
    private fun t(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

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
            _uiState.update { it.copy(errorMessage = t(R.string.vn_err_password_empty)) }
            return
        }
        if (password != confirm) {
            _uiState.update { it.copy(errorMessage = t(R.string.vn_err_password_mismatch)) }
            return
        }
        viewModelScope.launch {
            try {
                securityManager.setPassword(password, hint)
                replaceSessionPassword(password.toCharArray())
                _uiState.update {
                    it.copy(
                        unlocked = true,
                        infoMessage = t(R.string.vn_info_password_set),
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_password_save)) }
            }
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch {
            val valid = try {
                securityManager.verifyPassword(password)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_password_verify)) }
                return@launch
            }
            if (valid) {
                replaceSessionPassword(password.toCharArray())
                _uiState.update { it.copy(unlocked = true, errorMessage = null, infoMessage = t(R.string.vn_info_unlocked)) }
            } else {
                _uiState.update { it.copy(errorMessage = t(R.string.vn_err_wrong_password)) }
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
            _uiState.update { it.copy(errorMessage = t(R.string.vn_err_unlock_first)) }
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
            _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_start_recording)) }
            recorder.cancelRecording()
            currentTempRecording = null
            recordingTimerJob?.cancel()
        }
    }

    fun stopRecording(title: String?) {
        if (!_uiState.value.isRecording) return
        val password = sessionPassword
        if (password == null) {
            _uiState.update { it.copy(errorMessage = t(R.string.vn_err_unlock_first)) }
            cancelRecording()
            return
        }
        val result = try {
            recorder.stopRecording()
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_stop_recording)) }
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
                _uiState.update { it.copy(infoMessage = t(R.string.vn_info_saved), errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_save)) }
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
            _uiState.update { it.copy(errorMessage = t(R.string.vn_err_unlock_first)) }
            return
        }
        if (_uiState.value.playingNoteId == note.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        val password = sessionPassword
        if (password == null) {
            _uiState.update { it.copy(errorMessage = t(R.string.vn_err_unlock_first)) }
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
                    stopPlayback(withError = t(R.string.vn_err_playback))
                    true
                }
                player.prepare()
                player.start()

                playbackTempFile?.delete()
                playbackTempFile = tempFile
                mediaPlayer = player
                _uiState.update { it.copy(playingNoteId = note.id, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_play)) }
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
                _uiState.update { it.copy(infoMessage = t(R.string.vn_info_deleted)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_delete)) }
            }
        }
    }

    fun renameNote(note: VoiceNote, newTitle: String) {
        viewModelScope.launch {
            try {
                repository.renameVoiceNote(note.id, newTitle)
                _uiState.update { it.copy(infoMessage = t(R.string.vn_info_updated)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_rename)) }
            }
        }
    }

    fun updateNoteCategory(note: VoiceNote, category: VoiceNoteCategory) {
        viewModelScope.launch {
            try {
                repository.updateVoiceNoteCategory(note.id, category)
                _uiState.update { it.copy(infoMessage = t(R.string.vn_info_category_updated)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_category)) }
            }
        }
    }

    fun updateNoteTags(note: VoiceNote, tags: List<String>) {
        viewModelScope.launch {
            try {
                repository.updateVoiceNoteTags(note.id, tags)
                _uiState.update { it.copy(infoMessage = t(R.string.vn_info_tags_updated)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_tags)) }
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
            _uiState.update { it.copy(errorMessage = t(R.string.vn_err_unlock_first)) }
            return
        }
        val password = sessionPassword
        if (password == null) {
            _uiState.update { it.copy(errorMessage = t(R.string.vn_err_unlock_first)) }
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
                    putExtra(Intent.EXTRA_TEXT, t(R.string.vn_share_text, note.title))
                    type = "audio/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooserIntent = Intent.createChooser(shareIntent, t(R.string.voice_note_share))
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                getApplication<Application>().startActivity(chooserIntent)
                
                _uiState.update { it.copy(infoMessage = t(R.string.vn_info_sharing)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: t(R.string.vn_err_share)) }
            }
        }
    }
    
    fun exportAllNotes() {
        if (!_uiState.value.unlocked) {
            _uiState.update { it.copy(errorMessage = t(R.string.vn_err_unlock_first)) }
            return
        }
        val password = sessionPassword
        if (password == null) {
            _uiState.update { it.copy(errorMessage = t(R.string.vn_err_unlock_first)) }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, infoMessage = t(R.string.vn_info_exporting)) }
            
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
                            putExtra(Intent.EXTRA_SUBJECT, t(R.string.vn_export_subject))
                            putExtra(Intent.EXTRA_TEXT, getApplication<Application>().resources.getQuantityString(R.plurals.vn_export_text, result.noteCount, result.noteCount))
                            type = "application/zip"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        val chooserIntent = Intent.createChooser(shareIntent, t(R.string.vn_export_chooser))
                        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        getApplication<Application>().startActivity(chooserIntent)
                        
                        _uiState.update { 
                            it.copy(
                                isExporting = false, 
                                infoMessage = getApplication<Application>().resources.getQuantityString(R.plurals.vn_info_exported, result.noteCount, result.noteCount)
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
                        errorMessage = e.localizedMessage ?: t(R.string.vn_err_export)
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
