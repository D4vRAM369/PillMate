package com.d4vram.pillmate2.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.data.BackupManager
import com.d4vram.pillmate2.data.CsvUtils
import com.d4vram.pillmate2.ui.theme.AppUiStyle
import com.d4vram.pillmate2.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * ⚙️ Pantalla de configuración
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(themeManager: ThemeManager, repository: MedicationRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode by themeManager.isDarkMode.collectAsState(initial = false)
    val uiStyle by themeManager.uiStyle.collectAsState(initial = AppUiStyle.Default)
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showUiDialog by remember { mutableStateOf(false) }

    val backupManager = remember { BackupManager(context.applicationContext, repository) }
    val lastBackupMillis by backupManager.lastBackupFlow.collectAsState(initial = null)
    val lastBackupText = remember(lastBackupMillis) {
        lastBackupMillis?.let { millis ->
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
        } ?: "Sin copias"
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            snackbarMessage = null
        }
    }

    LaunchedEffect(Unit) {
        try {
            val file = backupManager.createAutomaticBackupIfNeeded()
            if (file != null) {
                snackbarMessage = "Copia automática guardada: ${file.name}"
            }
        } catch (e: Exception) {
            snackbarMessage = "Error en copia automática: ${e.message ?: "desconocido"}"
        }
    }

    // Launcher para seleccionar archivo CSV
    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val csvContent = readTextFromUri(context, it)
                    val parsed = CsvUtils.parseCsv(csvContent)
                    repository.replaceAllData(parsed.medications, parsed.history)
                    snackbarMessage = "Datos importados correctamente"
                } catch (e: Exception) {
                    snackbarMessage = "Error al importar: ${e.message ?: "desconocido"}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "⚙️ Configuración",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Sección de Apariencia
            item {
                SettingsSection(title = "🎨 Apariencia") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Modo oscuro",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isDarkMode) "Activado" else "Desactivado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { isChecked ->
                                scope.launch {
                                    themeManager.setDarkMode(isChecked)
                                }
                            }
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingsItem(
                        title = "Estilo de interfaz",
                        description = "Actual: ${uiStyle.title}",
                        icon = Icons.Default.Palette,
                        onClick = { showUiDialog = true }
                    )
                }
            }

            item {
                SettingsSection(title = "🔔 Notificaciones") {
                    SettingsItem(
                        title = "Recordatorios",
                        description = "Activar recordatorios automáticos",
                        hasSwitch = true
                    )
                    SettingsItem(
                        title = "Sonido",
                        description = "Configurar tono de notificación",
                        icon = Icons.Default.VolumeUp
                    )
                }
            }

            item {
                SettingsSection(title = "📊 Datos") {
                    SettingsItem(
                        title = "Exportar CSV",
                        description = "Crear archivo CSV con medicamentos e historial",
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            scope.launch {
                                try {
                                    val file = createExportFile(context, repository)
                                    shareFile(context, file)
                                    snackbarMessage = "CSV exportado: ${file.name}"
                                } catch (e: Exception) {
                                    snackbarMessage = "Error al exportar: ${e.message ?: "desconocido"}"
                                }
                            }
                        }
                    )
                    SettingsItem(
                        title = "Importar CSV",
                        description = "Cargar datos desde archivo CSV",
                        icon = Icons.Default.FileUpload,
                        onClick = {
                            csvImportLauncher.launch("text/*")
                        }
                    )
                    SettingsItem(
                        title = "Limpiar todos los datos",
                        description = "Eliminar todos los medicamentos y entradas",
                        icon = Icons.Default.Delete,
                        onClick = {
                            repository.clearAllData()
                            snackbarMessage = "Datos eliminados"
                        }
                    )
                    SettingsItem(
                        title = "Copia de seguridad",
                        description = "Última copia: $lastBackupText",
                        icon = Icons.Default.Backup,
                        onClick = {
                            scope.launch {
                                try {
                                    val file = backupManager.createManualBackup()
                                    snackbarMessage = "Copia guardada: ${file.name}"
                                } catch (e: Exception) {
                                    snackbarMessage = "Error en copia: ${e.message ?: "desconocido"}"
                                }
                            }
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "ℹ️ Información") {
                    SettingsItem(
                        title = "Versión",
                        description = "PillMate v2.0.0",
                        icon = Icons.Default.Info
                    )
                    SettingsItem(
                        title = "Acerca de",
                        description = "Información de la aplicación",
                        icon = Icons.Default.QuestionMark
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
    
    if (showUiDialog) {
        AlertDialog(
            onDismissRequest = { showUiDialog = false },
            title = { Text("Estilo de interfaz") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppUiStyle.entries.forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showUiDialog = false
                                    scope.launch { themeManager.setUiStyle(style) }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = style == uiStyle,
                                onClick = {
                                    showUiDialog = false
                                    scope.launch { themeManager.setUiStyle(style) }
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = style.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = style.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUiDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    hasSwitch: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit = {}
) {
    var switchState by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!hasSwitch) onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (hasSwitch) {
            Switch(
                checked = switchState,
                onCheckedChange = { switchState = it }
            )
        } else {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private suspend fun createExportFile(
    context: Context,
    repository: MedicationRepository
): File = withContext(Dispatchers.IO) {
    val medications = repository.getMedicationsSnapshot()
    val history = repository.getHistorySnapshot()
    val csv = CsvUtils.buildCsv(medications, history)
    val directory = context.getExternalFilesDir("exports") ?: context.filesDir
    if (!directory.exists()) directory.mkdirs()
    val file = File(directory, "pillmate_export_${System.currentTimeMillis()}.csv")
    file.writeText(csv)
    file
}

private suspend fun readTextFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        input.bufferedReader().use { it.readText() }
    } ?: throw IllegalArgumentException("Archivo no disponible")
}

private fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartir CSV"))
}
