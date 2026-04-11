// app/src/main/java/com/d4vram/pillmate2/ui/ai/AiChatScreen.kt
package com.d4vram.pillmate2.ui.ai

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.ai.AssistantChatMessage
import com.d4vram.pillmate2.ai.AssistantMessageRole
import com.d4vram.pillmate2.ai.toTreatmentStatsSnapshot
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.ui.stats.viewmodel.StatisticsViewModel
import kotlinx.coroutines.launch
import java.io.File

private val suggestions = listOf(
    "¿Qué patrón tengo esta semana?",
    "¿Franja horaria dominante?",
    "¿Hay tendencia?",
    "Comparar con semana anterior"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    repository: MedicationRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statsViewModel: StatisticsViewModel = viewModel(
        factory = StatisticsViewModel.provideFactory(repository)
    )
    val viewModel: AiHubViewModel = viewModel(
        factory = AiHubViewModel.provideFactory(context.applicationContext as Application)
    )
    val statsUiState by statsViewModel.uiState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }

    LaunchedEffect(statsUiState) {
        viewModel.updateStatisticsContext(statsUiState.toTreatmentStatsSnapshot())
    }

    // Auto-scroll al último mensaje
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    val activeModelName = uiState.models.firstOrNull { it.isActive }?.displayName

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.sessions.firstOrNull {
                                it.id == uiState.activeSessionId
                            }?.title ?: "PillMate AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (activeModelName != null) Color(0xFF22C55E)
                                        else MaterialTheme.colorScheme.outline,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Text(
                                text = if (activeModelName != null) "$activeModelName · Local"
                                else "Sin modelo · Modo análisis",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Renombrar conversación") },
                                onClick = {
                                    showMenu = false
                                    renameDraft = uiState.sessions.firstOrNull {
                                        it.id == uiState.activeSessionId
                                    }?.title.orEmpty()
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Nueva conversación") },
                                onClick = {
                                    showMenu = false
                                    viewModel.createSession("Nueva conversación")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar chat") },
                                enabled = uiState.activeSessionId != null,
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        val exportUri = viewModel.exportCurrentSession() ?: return@launch
                                        val file = File(requireNotNull(exportUri.path))
                                        val shareUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            putExtra(Intent.EXTRA_SUBJECT, "Chat PillMate AI")
                                        }
                                        context.startActivity(
                                            Intent.createChooser(intent, "Exportar conversación")
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Gestionar sesiones") },
                                onClick = {
                                    showMenu = false
                                    viewModel.showSessionSheet()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Lista de mensajes
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }

                // Indicador de generación
                if (uiState.isGenerating) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            val shouldShowSuggestions = !uiState.isGenerating && (
                uiState.messages.isEmpty() ||
                    uiState.messages.lastOrNull()?.role == AssistantMessageRole.ASSISTANT
                )

            if (shouldShowSuggestions) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions) { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.sendSuggestion(suggestion) },
                            label = {
                                Text(suggestion, style = MaterialTheme.typography.bodySmall)
                            }
                        )
                    }
                }
            }

            // Input bar
            InputBar(
                value = uiState.draft,
                onValueChange = viewModel::updateDraft,
                isGenerating = uiState.isGenerating,
                onSend = viewModel::sendDraft,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    if (uiState.showSessionSheet) {
        SessionBottomSheet(
            sessions = uiState.sessions,
            activeSessionId = uiState.activeSessionId,
            onDismiss = viewModel::hideSessionSheet,
            onSelectSession = viewModel::selectSession,
            onCreateSession = viewModel::createSession,
            onDeleteSession = viewModel::deleteSession
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar conversación") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("Título") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sessionId = uiState.activeSessionId
                        if (sessionId != null) {
                            viewModel.renameSession(sessionId, renameDraft)
                        }
                        showRenameDialog = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun MessageBubble(message: AssistantChatMessage) {
    val isUser = message.role == AssistantMessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp, topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 14.dp
                    )
                )
                .background(
                    if (isUser) Color(0xFF0D3050)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) Color(0xFFD7F0FF)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "···",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Escribe algo...") },
            shape = RoundedCornerShape(14.dp),
            maxLines = 4,
            enabled = !isGenerating
        )
        IconButton(
            onClick = onSend,
            enabled = value.isNotBlank() && !isGenerating,
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (value.isNotBlank() && !isGenerating)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = if (value.isNotBlank()) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
