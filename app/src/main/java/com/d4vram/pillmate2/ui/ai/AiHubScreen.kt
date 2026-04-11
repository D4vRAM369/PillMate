package com.d4vram.pillmate2.ui.ai

import android.app.Application
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.ai.toTreatmentStatsSnapshot
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.data.ai.StoredChatSession
import com.d4vram.pillmate2.data.ai.StoredLocalModel
import com.d4vram.pillmate2.ui.stats.TreatmentAssistantCard
import com.d4vram.pillmate2.ui.stats.viewmodel.StatisticsViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHubScreen(
    repository: MedicationRepository,
    onOpenChat: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val statsViewModel: StatisticsViewModel = viewModel(
        factory = StatisticsViewModel.provideFactory(repository)
    )
    val aiViewModel: AiHubViewModel = viewModel(
        factory = AiHubViewModel.provideFactory(context.applicationContext as Application)
    )
    val statsUiState by statsViewModel.uiState.collectAsState()
    val aiUiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showDownloadDialog by remember { mutableStateOf(false) }
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var modelName by remember { mutableStateOf("") }
    var modelUrl by remember { mutableStateOf("") }
    var sessionName by remember { mutableStateOf("") }

    LaunchedEffect(statsUiState) {
        aiViewModel.updateStatisticsContext(statsUiState.toTreatmentStatsSnapshot())
    }

    val modelImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                aiViewModel.importModel(uri, "modelo-importado")
            }
        }
    }

    val sessionImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                aiViewModel.importSession(uri)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PillMate AI",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroAiCard()
            ActionRowButton(
                icon = Icons.Default.SmartToy,
                title = "Abrir chat IA",
                description = "Entrar en el chat completo con contexto de estadísticas",
                onClick = onOpenChat
            )

            ModelSection(
                models = aiUiState.models,
                activeModelPath = aiUiState.activeModelPath,
                onImportModel = {
                    modelImporter.launch(arrayOf("*/*"))
                },
                onDownloadModel = { showDownloadDialog = true },
                onActivateModel = aiViewModel::activateModel,
                onDeleteModel = aiViewModel::deleteModel,
                onManageModels = aiViewModel::showModelSheet
            )

            SessionSection(
                sessions = aiUiState.sessions,
                activeSessionId = aiUiState.activeSessionId,
                onCreateSession = { showNewSessionDialog = true },
                onSelectSession = aiViewModel::selectSession,
                onImportSession = {
                    sessionImporter.launch(arrayOf("application/json", "*/*"))
                },
                onExportSession = {
                    scope.launch {
                        val exportUri = aiViewModel.exportCurrentSession() ?: return@launch
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
                        context.startActivity(Intent.createChooser(intent, "Exportar conversación"))
                    }
                },
                onManageSessions = aiViewModel::showSessionSheet
            )

            TreatmentAssistantCard(
                uiState = aiUiState.toAssistantCardState(),
                onDraftChange = aiViewModel::updateDraft,
                onSend = aiViewModel::sendDraft,
                onSuggestionSelected = aiViewModel::sendSuggestion
            )
        }
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Descargar modelo local") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Nombre del modelo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelUrl,
                        onValueChange = { modelUrl = it },
                        label = { Text("URL directa al .task") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            aiViewModel.downloadModel(modelName, modelUrl)
                            showDownloadDialog = false
                            modelName = ""
                            modelUrl = ""
                        }
                    },
                    enabled = modelName.isNotBlank() && modelUrl.startsWith("http")
                ) {
                    Text("Descargar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDownloadDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text("Nueva conversación") },
            text = {
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        aiViewModel.createSession(sessionName.ifBlank { "Nueva conversación" })
                        sessionName = ""
                        showNewSessionDialog = false
                    }
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewSessionDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (aiUiState.showModelSheet) {
        ModelBottomSheet(
            models = aiUiState.models,
            activeModelPath = aiUiState.activeModelPath,
            candidatePaths = aiUiState.candidatePaths,
            onDismiss = aiViewModel::hideModelSheet,
            onImportModel = { modelImporter.launch(arrayOf("*/*")) },
            onDownloadModel = { showDownloadDialog = true },
            onActivateModel = aiViewModel::activateModel,
            onDeleteModel = aiViewModel::deleteModel
        )
    }

    if (aiUiState.showSessionSheet) {
        SessionBottomSheet(
            sessions = aiUiState.sessions,
            activeSessionId = aiUiState.activeSessionId,
            onDismiss = aiViewModel::hideSessionSheet,
            onSelectSession = aiViewModel::selectSession,
            onCreateSession = aiViewModel::createSession,
            onDeleteSession = aiViewModel::deleteSession
        )
    }
}

@Composable
private fun HeroAiCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF071325), Color(0xFF0F3B66), Color(0xFF0EA5E9))
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(Color.White.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Text(
                    text = "Chat local con tus estadísticas",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "Gestiona modelos `.task`, sesiones guardadas, importación, exportación y respuestas privadas dentro de una screen propia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD7F0FF)
                )
            }
        }
    }
}

@Composable
private fun ModelSection(
    models: List<StoredLocalModel>,
    activeModelPath: String?,
    onImportModel: () -> Unit,
    onDownloadModel: () -> Unit,
    onActivateModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onManageModels: () -> Unit
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(
                title = "Modelos locales",
                subtitle = activeModelPath?.substringAfterLast('/') ?: "Sin modelo activo"
            )

            ActionRowButton(
                icon = Icons.Default.FileOpen,
                title = "Importar modelo",
                description = "Traer un `.task` desde almacenamiento local",
                onClick = onImportModel
            )
            ActionRowButton(
                icon = Icons.Default.Download,
                title = "Descargar por URL",
                description = "Bajar un modelo `.task` directamente a la app",
                onClick = onDownloadModel
            )
            OutlinedButton(onClick = onManageModels, modifier = Modifier.fillMaxWidth()) {
                Text("Gestionar modelos en hoja")
            }

            if (models.isEmpty()) {
                EmptyRow("Todavía no hay modelos registrados.")
            } else {
                models.forEach { model ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (model.isActive) "Activo" else "Listo para activar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onActivateModel(model.id) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                            }
                            OutlinedButton(onClick = { onDeleteModel(model.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SessionSection(
    sessions: List<StoredChatSession>,
    activeSessionId: String?,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onImportSession: () -> Unit,
    onExportSession: () -> Unit,
    onManageSessions: () -> Unit
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(
                title = "Sesiones",
                subtitle = sessions.firstOrNull { it.id == activeSessionId }?.title ?: "Sin sesión activa"
            )

            ActionRowButton(
                icon = Icons.Default.UploadFile,
                title = "Importar chat",
                description = "Recuperar una conversación exportada en JSON",
                onClick = onImportSession
            )
            ActionRowButton(
                icon = Icons.Default.FolderZip,
                title = "Exportar chat activo",
                description = "Compartir la sesión actual como archivo JSON",
                onClick = onExportSession
            )
            ActionRowButton(
                icon = Icons.Default.Memory,
                title = "Nueva conversación",
                description = "Crear una sesión nueva con otro hilo",
                onClick = onCreateSession
            )
            OutlinedButton(onClick = onManageSessions, modifier = Modifier.fillMaxWidth()) {
                Text("Gestionar sesiones en hoja")
            }

            if (sessions.isEmpty()) {
                EmptyRow("Todavía no hay conversaciones.")
            } else {
                sessions.take(6).forEach { session ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelectSession(session.id) }
                            .background(
                                if (session.id == activeSessionId) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                }
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${session.messages.size} msgs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionRowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun AiHubUiState.toAssistantCardState() = com.d4vram.pillmate2.ai.TreatmentAssistantUiState(
    messages = messages,
    draft = draft,
    isGenerating = isGenerating,
    sourceLabel = sourceLabel,
    activeModelPath = activeModelPath,
    candidatePaths = candidatePaths
)
