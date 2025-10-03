package com.d4vram.pillmate2.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.model.VoiceNote
import com.d4vram.pillmate2.model.VoiceNoteCategory
import com.d4vram.pillmate2.ui.voicenotes.VoiceNotesViewModel
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.foundation.border
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.InputChip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNotesScreen(viewModel: VoiceNotesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (!message.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar(message) }
            viewModel.consumeErrorMessage()
        }
    }

    LaunchedEffect(uiState.infoMessage) {
        val message = uiState.infoMessage
        if (!message.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar(message) }
            viewModel.consumeInfoMessage()
        }
    }

    val hasAudioPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    var audioPermissionGranted by remember { mutableStateOf(hasAudioPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        audioPermissionGranted = granted
        if (granted) {
            viewModel.startRecording()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Sin permiso de micrófono") }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Notas de voz") },
                navigationIcon = {
                    Icon(Icons.Default.Mic, contentDescription = null)
                },
                actions = {
                    if (uiState.unlocked) {
                        IconButton(
                            onClick = { viewModel.exportAllNotes() },
                            enabled = !uiState.isExporting
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Exportar todas las notas"
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Surface(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            when {
                !uiState.passwordConfigured -> {
                    PasswordSetupCard(onSave = { pass, confirm, hint ->
                        viewModel.setPassword(pass, confirm, hint)
                    })
                }

                !uiState.unlocked -> {
                    UnlockCard(
                        hint = uiState.passwordHint,
                        onUnlock = { password -> viewModel.unlock(password) }
                    )
                }

                else -> {
                    VoiceNotesContent(
                        uiState = uiState,
                        onRecordToggle = {
                            if (!audioPermissionGranted) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else if (uiState.isRecording) {
                                viewModel.stopRecording(title = it)
                            } else {
                                viewModel.startRecording()
                            }
                        },
                        onCancelRecording = { viewModel.cancelRecording() },
                        onPlay = { note -> viewModel.playOrToggle(note) },
                        onDelete = { note -> viewModel.deleteNote(note) },
                        onRename = { note, title -> viewModel.renameNote(note, title) },
                        onShare = { note -> viewModel.shareNote(note) },
                        onCategoryChange = { note, category -> viewModel.updateNoteCategory(note, category) },
                        onTagsChange = { note, tags -> viewModel.updateNoteTags(note, tags) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordSetupCard(onSave: (String, String, String?) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Protege tus notas",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Crea una contraseña para cifrar tus notas de voz con AES-256",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirmar contraseña") },
            singleLine = true,
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { confirmVisible = !confirmVisible }) {
                    Icon(
                        imageVector = if (confirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = hint,
            onValueChange = { hint = it },
            label = { Text("Pista (opcional)") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onSave(password, confirm, hint.ifBlank { null }) }, enabled = password.isNotBlank() && confirm.isNotBlank()) {
            Text("Guardar contraseña")
        }
    }
}

@Composable
private fun UnlockCard(
    hint: String?,
    onUnlock: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Introduce tu contraseña",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (!hint.isNullOrBlank()) {
            Text(text = "Pista: $hint", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onUnlock(password) }, enabled = password.isNotBlank()) {
            Text("Desbloquear")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceNotesContent(
    uiState: VoiceNotesViewModel.VoiceNotesUiState,
    onRecordToggle: (String?) -> Unit,
    onCancelRecording: () -> Unit,
    onPlay: (VoiceNote) -> Unit,
    onDelete: (VoiceNote) -> Unit,
    onRename: (VoiceNote, String) -> Unit,
    onShare: (VoiceNote) -> Unit,
    onCategoryChange: (VoiceNote, VoiceNoteCategory) -> Unit,
    onTagsChange: (VoiceNote, List<String>) -> Unit
) {
    var renameTarget by remember { mutableStateOf<VoiceNote?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<VoiceNote?>(null) }
    var categoryTarget by remember { mutableStateOf<VoiceNote?>(null) }
    var tagsTarget by remember { mutableStateOf<VoiceNote?>(null) }
    var tagsText by remember { mutableStateOf("") }
    var pendingTitle by remember { mutableStateOf("") }

    val grouped = remember(uiState.voiceNotes) {
        uiState.voiceNotes
            .sortedByDescending { it.createdAtEpochMillis }
            .groupBy { dayLabel(it.createdAtEpochMillis) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (grouped.isEmpty()) {
                item {
                    EmptyState(
                        isRecording = uiState.isRecording,
                        onRecord = { onRecordToggle(null) }
                    )
                }
            } else {
                grouped.forEach { (date, notes) ->
                    stickyHeader {
                        DateHeader(date)
                    }
                    items(notes, key = { it.id }) { note ->
                        VoiceNoteRow(
                            note = note,
                            isPlaying = uiState.playingNoteId == note.id,
                            onPlay = { onPlay(note) },
                            onRename = {
                                renameTarget = note
                                renameText = note.title
                            },
                            onDelete = { deleteTarget = note },
                            onShare = { onShare(note) },
                            onCategoryChange = { category -> onCategoryChange(note, category) },
                            onTagsChange = { tags -> onTagsChange(note, tags) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        RecordControls(
            isRecording = uiState.isRecording,
            elapsedMillis = uiState.recordingElapsedMillis,
            onStop = { onRecordToggle(pendingTitle) },
            onStart = {
                pendingTitle = ""
                onRecordToggle(null)
            },
            onCancel = onCancelRecording,
            onTitleChanged = { pendingTitle = it }
        )
    }

    renameTarget?.let { note ->
        RenameDialog(
            initial = renameText,
            onDismiss = {
                renameTarget = null
                renameText = ""
            },
            onConfirm = { title ->
                onRename(note, title)
                renameTarget = null
                renameText = ""
            }
        )
    }

    deleteTarget?.let { note ->
        ConfirmDeleteDialog(
            title = note.title,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDelete(note)
                deleteTarget = null
            }
        )
    }

    categoryTarget?.let { note ->
        CategorySelectorDialog(
            currentCategory = note.category,
            onDismiss = { categoryTarget = null },
            onConfirm = { category ->
                onCategoryChange(note, category)
                categoryTarget = null
            }
        )
    }

    tagsTarget?.let { note ->
        TagsEditorDialog(
            initialTags = tagsText,
            onDismiss = { 
                tagsTarget = null
                tagsText = ""
            },
            onConfirm = { tags ->
                val tagsList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                onTagsChange(note, tagsList)
                tagsTarget = null
                tagsText = ""
            }
        )
    }
}

@Composable
private fun DateHeader(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun VoiceNoteRow(
    note: VoiceNote,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onCategoryChange: (VoiceNoteCategory) -> Unit,
    onTagsChange: (List<String>) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = ComposeColor(android.graphics.Color.parseColor(note.category.colorHex)),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        shape = CircleShape
                    )
                    .clickable { onPlay() }
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = note.title, 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = { /* Mostrar selector de categoría */ },
                        label = { Text(note.category.displayName) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        ComposeColor(android.graphics.Color.parseColor(note.category.colorHex)),
                                        CircleShape
                                    )
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Duración ${formatDuration(note.durationMillis)} • ${formatTime(note.createdAtEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (note.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(note.tags) { tag ->
                            SuggestionChip(
                                onClick = { /* Editar etiquetas */ },
                                label = { 
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall
                                    ) 
                                }
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Detener" else "Reproducir"
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Cambiar categoría") },
                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            showCategoryDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Editar etiquetas") },
                        leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            showTagsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }

    if (showCategoryDialog) {
        CategorySelectorDialog(
            currentCategory = note.category,
            onDismiss = { showCategoryDialog = false },
            onConfirm = { category ->
                onCategoryChange(category)
                showCategoryDialog = false
            }
        )
    }

    if (showTagsDialog) {
        TagsEditorDialog(
            initialTags = note.tags.joinToString(", "),
            onDismiss = { showTagsDialog = false },
            onConfirm = { tags ->
                val tagsList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                onTagsChange(tagsList)
                showTagsDialog = false
            }
        )
    }
}

@Composable
private fun RecordControls(
    isRecording: Boolean,
    elapsedMillis: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onTitleChanged: (String) -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (isRecording) {
                Text(
                    text = "Grabando... ${formatDuration(elapsedMillis)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = formatRecordingTitle(elapsedMillis),
                    onValueChange = { onTitleChanged(it) },
                    label = { Text("Título automático") },
                    enabled = false
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onStop, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar")
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancelar")
                    }
                }
            } else {
                Text(text = "Listo para grabar", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grabar nota de voz")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isRecording: Boolean, onRecord: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MicNone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Sin notas de voz", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Graba tus recordatorios hablados y guárdalos cifrados.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (!isRecording) {
            Button(onClick = onRecord) {
                Text("Empezar a grabar")
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar nota") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Título") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text("Eliminar nota") },
        text = { Text("¿Eliminar \"$title\"? Esta acción es permanente.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Eliminar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun formatTime(epochMillis: Long): String {
    val instant = Instant.ofEpochMilli(epochMillis)
    val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}

private fun dayLabel(epochMillis: Long): String {
    val instant = Instant.ofEpochMilli(epochMillis)
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())
    return formatter.format(instant.atZone(ZoneId.systemDefault())).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

private fun formatRecordingTitle(elapsed: Long): String {
    val instant = Instant.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    return "Nota ${formatter.format(instant.atZone(ZoneId.systemDefault()))}"
}

@Composable
private fun CategorySelectorDialog(
    currentCategory: VoiceNoteCategory,
    onDismiss: () -> Unit,
    onConfirm: (VoiceNoteCategory) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar categoría") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceNoteCategory.values().forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onConfirm(category) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = category == currentCategory,
                            onClick = { onConfirm(category) }
                        )
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    ComposeColor(android.graphics.Color.parseColor(category.colorHex)),
                                    CircleShape
                                )
                        )
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun TagsEditorDialog(
    initialTags: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var tagsText by remember { mutableStateOf(initialTags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar etiquetas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Separa las etiquetas con comas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Etiquetas") },
                    placeholder = { Text("urgente, importante, trabajo") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(tagsText) }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
