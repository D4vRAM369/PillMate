package com.d4vram.pillmate2.ui.components

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.d4vram.pillmate2.data.AudioNoteManager
import com.d4vram.pillmate2.data.DraftNote
import com.d4vram.pillmate2.data.DraftNoteManager
import com.d4vram.pillmate2.model.HistoryEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formatea milisegundos a formato mm:ss
 */
private fun formatDuration(millis: Int): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * 📝 Modal para añadir y editar notas de entradas del historial
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesModal(
    historyEntry: HistoryEntry,
    onDismiss: () -> Unit,
    onSaveChanges: (String, String, LocalDateTime, String?) -> Unit, // (entryId, notes, timestamp, audioNoteUri)
    onDeleteNotes: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioNoteManager = remember { AudioNoteManager(context) }
    val draftNoteManager = remember { DraftNoteManager(context) }

    val originalDateTime = remember(historyEntry.id) {
        historyEntry.timestamp.withSecond(0).withNano(0)
    }

    // Estado inicial: se cargará desde borrador si existe
    var notesText by remember(historyEntry.id) { mutableStateOf(historyEntry.notes) }
    var isEditing by remember(historyEntry.id) { mutableStateOf(historyEntry.notes.isEmpty()) }
    var selectedDateTime by remember(historyEntry.id) { mutableStateOf(originalDateTime) }
    var draftLoaded by remember(historyEntry.id) { mutableStateOf(false) }
    var hasDraftChanges by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Estado para grabación de audio
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var hasRecordPermission by remember { mutableStateOf(false) }
    var existingAudioId by remember { mutableStateOf(historyEntry.audioNoteUri) }
    var hasNewRecording by remember { mutableStateOf(false) }

    // Estado para reproducción de audio
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Cargar audio existente si lo hay
    LaunchedEffect(historyEntry.id) {
        historyEntry.audioNoteUri?.let { audioId ->
            audioNoteManager.decryptAudioForPlayback(audioId)?.let { decryptedFile ->
                audioFile = decryptedFile
            }
        }
    }

    // 📥 Cargar borrador si existe (persistencia tras cerrar app/bloqueo PIN)
    LaunchedEffect(historyEntry.id) {
        if (!draftLoaded) {
            draftNoteManager.getDraft(historyEntry.id)?.let { draft ->
                // Restaurar estado desde el borrador
                notesText = draft.notesText
                selectedDateTime = LocalDateTime.parse(draft.dateTimeIso)
                draft.audioNoteUri?.let { existingAudioId = it }
                isEditing = true  // Si hay borrador, estaba editando
                hasDraftChanges = true
            }
            draftLoaded = true
        }
    }

    // 💾 Auto-guardar borrador cuando hay cambios (con debounce de 500ms)
    LaunchedEffect(notesText, selectedDateTime, existingAudioId) {
        if (draftLoaded && isEditing) {
            delay(500)  // Debounce: esperar 500ms sin cambios antes de guardar
            val draft = DraftNote(
                entryId = historyEntry.id,
                notesText = notesText,
                dateTimeIso = selectedDateTime.toString(),
                audioNoteUri = existingAudioId
            )
            draftNoteManager.saveDraft(draft)
            hasDraftChanges = true
        }
    }

    val zoneId = remember { ZoneId.systemDefault() }
    val fullDateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val formattedDisplayDate = remember(selectedDateTime) { selectedDateTime.format(fullDateFormatter) }
    val formattedDisplayTime = remember(selectedDateTime) { selectedDateTime.format(timeFormatter) }
    val hasChanges = remember(notesText, selectedDateTime, audioFile) {
        notesText != historyEntry.notes || selectedDateTime != originalDateTime || audioFile != null
    }

    // Limpieza de recursos al desmontar
    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) { }
                release()
            }
            mediaPlayer?.release()
        }
    }

    // Timer para duración de grabación
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(100)
                recordingDuration += 100
            }
        } else {
            recordingDuration = 0
        }
    }

    // Timer para progreso de reproducción
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    playbackProgress = player.currentPosition.toFloat() / player.duration.toFloat()
                }
            }
            delay(100)
        }
    }

    // Funciones de control de reproducción
    fun stopPlayback() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        isPlaying = false
        playbackProgress = 0f
    }

    fun startPlayback() {
        audioFile?.let { file ->
            try {
                val player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        isPlaying = false
                        playbackProgress = 0f
                    }
                    start()
                }
                mediaPlayer = player
                isPlaying = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun pausePlayback() {
        mediaPlayer?.pause()
        isPlaying = false
    }

    // Funciones de control de grabación
    fun startRecording() {
        try {
            // Si hay un audio existente, eliminarlo del reproductor primero
            if (audioFile != null && !hasNewRecording) {
                stopPlayback()
            }

            val file = File.createTempFile("note_audio_", ".m4a", context.cacheDir)
            
            // ARREGLO: Comprobar la versión de Android para usar el constructor correcto.
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
            audioFile = file
            hasNewRecording = true
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteRecording() {
        // Eliminar archivo temporal
        audioFile?.delete()
        audioFile = null
        hasNewRecording = false
        existingAudioId = null
        playbackProgress = 0f
    }

    // Launcher para solicitar permisos de grabación (después de definir startRecording)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            startRecording()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header con información del medicamento
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Emoji y color del medicamento
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(historyEntry.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = historyEntry.emoji,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            val title = listOfNotNull(
                                historyEntry.medicationName,
                                historyEntry.dose?.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${historyEntry.date} • ${historyEntry.time}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (historyEntry.quantity > 1) {
                                Text(
                                    text = "Cantidad registrada: x${historyEntry.quantity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sección fecha/hora
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏰ Momento de la toma",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isEditing) {
                                TextButton(onClick = { isEditing = true }) {
                                    Text("Editar")
                                }
                            }
                        }

                        if (isEditing) {
                            OutlinedTextField(
                                value = formattedDisplayDate,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Fecha") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = "Cambiar fecha"
                                        )
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = formattedDisplayTime,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Hora") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { showTimePicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = "Cambiar hora"
                                        )
                                    }
                                }
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = "${historyEntry.date} • ${historyEntry.time}",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Sección notas
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📝 Notas adicionales",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            if (!isEditing && notesText.isNotEmpty()) {
                                Row {
                                    IconButton(onClick = { isEditing = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar notas",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = {
                                        notesText = ""
                                        onDeleteNotes(historyEntry.id)
                                        onDismiss()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar notas",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        if (isEditing) {
                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                label = { Text("Notas (opcional)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                maxLines = 5,
                                shape = RoundedCornerShape(8.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Botón de grabación de audio
                            if (!isRecording && audioFile == null) {
                                OutlinedButton(
                                    onClick = {
                                        if (hasRecordPermission) {
                                            startRecording()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Grabar nota de voz"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Grabar nota de voz")
                                }
                            }

                            // UI de grabación activa
                            if (isRecording) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Grabando...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = formatDuration(recordingDuration),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { stopRecording() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Detener grabación",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }

                            // Reproductor de audio compacto
                            if (audioFile != null && !isRecording) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "🎤 Nota de voz",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Botón de compartir
                                                IconButton(
                                                    onClick = {
                                                        audioFile?.let { file ->
                                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                                context,
                                                                "${context.packageName}.provider",
                                                                file
                                                            )
                                                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                                type = "audio/*"
                                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(
                                                                android.content.Intent.createChooser(shareIntent, "Compartir nota de voz")
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Share,
                                                        contentDescription = "Compartir audio",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                // Botón de eliminar
                                                IconButton(
                                                    onClick = {
                                                        stopPlayback()
                                                        deleteRecording()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Eliminar audio",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Barra de progreso
                                        LinearProgressIndicator(
                                            progress = { playbackProgress },
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Controles de reproducción
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (isPlaying) {
                                                        pausePlayback()
                                                    } else {
                                                        if (playbackProgress > 0f && playbackProgress < 1f) {
                                                            // Reanudar
                                                            mediaPlayer?.start()
                                                            isPlaying = true
                                                        } else {
                                                            // Iniciar desde el principio
                                                            stopPlayback()
                                                            startPlayback()
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (notesText.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = notesText,
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                 }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Sin notas adicionales",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(onClick = { isEditing = true }) {
                                        Text("Añadir notas")
                                    }
                                }
                            }
                        }
                    }

                    if (isEditing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                // Descartar cambios y eliminar borrador
                                scope.launch {
                                    draftNoteManager.deleteDraft(historyEntry.id)
                                }
                                notesText = historyEntry.notes
                                selectedDateTime = originalDateTime
                                isEditing = false
                                hasDraftChanges = false
                            }) {
                                Text("Cancelar")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    // Si hay una nueva grabación, encriptarla y guardarla
                                    val audioId = if (hasNewRecording && audioFile != null) {
                                        try {
                                            // Eliminar audio antiguo si existe
                                            existingAudioId?.let { oldId ->
                                                audioNoteManager.deleteAudio(oldId)
                                            }
                                            // Guardar nuevo audio encriptado
                                            audioNoteManager.saveEncryptedAudio(audioFile!!)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            null
                                        }
                                    } else {
                                        existingAudioId // Mantener el audio existente si no hay cambios
                                    }

                                    onSaveChanges(historyEntry.id, notesText, selectedDateTime, audioId)

                                    // 🗑️ Eliminar borrador después de guardar definitivamente
                                    scope.launch {
                                        draftNoteManager.deleteDraft(historyEntry.id)
                                    }

                                    isEditing = false
                                    hasDraftChanges = false
                                    // Modal permanece abierto después de guardar
                                },
                                enabled = hasChanges
                            ) {
                                Text("Guardar cambios")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(selectedDateTime, zoneId) {
            selectedDateTime.atZone(zoneId).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
                        selectedDateTime = selectedDateTime.withYear(newDate.year)
                            .withMonth(newDate.monthValue)
                            .withDayOfMonth(newDate.dayOfMonth)
                    }
                    showDatePicker = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedDateTime.hour,
            initialMinute = selectedDateTime.minute,
            is24Hour = true
        )

        Dialog(onDismissRequest = { showTimePicker = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Selecciona la hora",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    TimePicker(state = timePickerState)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            selectedDateTime = selectedDateTime.withHour(timePickerState.hour)
                                .withMinute(timePickerState.minute)
                                .withSecond(0)
                                .withNano(0)
                            showTimePicker = false
                        }) {
                            Text("Aceptar")
                        }
                    }
                }
            }
        }
    }
}