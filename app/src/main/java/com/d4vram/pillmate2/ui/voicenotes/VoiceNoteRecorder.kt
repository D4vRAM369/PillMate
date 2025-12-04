package com.d4vram.pillmate2.ui.voicenotes

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class VoiceNoteRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimestamp: Long = 0L

    fun startRecording(): File {
        if (mediaRecorder != null) {
            throw IllegalStateException("Recorder already started")
        }
        val file = File.createTempFile("voice_recording_", ".m4a", context.cacheDir)
        
        // ARREGLO: Añadir la comprobación de versión de SDK para compatibilidad.
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        mediaRecorder = recorder
        outputFile = file
        startTimestamp = System.currentTimeMillis()
        return file
    }

    fun stopRecording(): RecordingResult {
        val recorder = mediaRecorder ?: throw IllegalStateException("Recorder not started")
        return try {
            recorder.stop()
            val duration = System.currentTimeMillis() - startTimestamp
            val file = outputFile ?: throw IllegalStateException("Missing output file")
            RecordingResult(file, duration)
        } finally {
            recorder.reset()
            recorder.release()
            mediaRecorder = null
            outputFile = null
            startTimestamp = 0L
        }
    }

    fun cancelRecording() {
        mediaRecorder?.let { recorder ->
            try {
                recorder.stop()
            } catch (ignored: Exception) {
                // Estado inconsistente, ignorar.
            } finally {
                recorder.reset()
                recorder.release()
            }
        }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
        startTimestamp = 0L
    }

    data class RecordingResult(
        val file: File,
        val durationMillis: Long
    )
}