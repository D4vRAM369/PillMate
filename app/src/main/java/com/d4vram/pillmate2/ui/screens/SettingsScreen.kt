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
import com.d4vram.pillmate2.data.BackupZipManager
import com.d4vram.pillmate2.data.CsvUtils
import com.d4vram.pillmate2.ui.security.SecurityManager
import com.d4vram.pillmate2.ui.security.SecuritySettings
import com.d4vram.pillmate2.ui.security.rememberVoiceNoteSecurityManager
import com.d4vram.pillmate2.ui.theme.AppUiStyle
import com.d4vram.pillmate2.ui.theme.ThemeManager
import androidx.biometric.BiometricManager
import com.d4vram.pillmate2.model.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
fun SettingsScreen(
    themeManager: ThemeManager,
    repository: MedicationRepository,
    securityManager: SecurityManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode by themeManager.isDarkMode.collectAsState(initial = false)
    val uiStyle by themeManager.uiStyle.collectAsState(initial = AppUiStyle.Default)
    val chipCountPeriod by themeManager.chipCountPeriod.collectAsState(initial = com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Total)
    val sortMode by themeManager.sortMode.collectAsState(initial = SortMode.MANUAL)
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showUiDialog by remember { mutableStateOf(false) }
    var showChipPeriodDialog by remember { mutableStateOf(false) }
    var showSortModeDialog by remember { mutableStateOf(false) }
    val securitySettings by securityManager.settings.collectAsState(initial = SecuritySettings())
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var showTimeoutPicker by remember { mutableStateOf(false) }
    var pendingEnableLock by remember { mutableStateOf(false) }
    var showVoiceNotePasswordDialog by remember { mutableStateOf(false) }
    var showHistoryExportDialog by remember { mutableStateOf(false) }
    var showBackupExportDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var pendingEncryptedBackupFile by remember { mutableStateOf<File?>(null) }

    val biometricManager = remember { BiometricManager.from(context) }
    val canAuthenticateBiometric = remember(biometricManager) {
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(canAuthenticateBiometric, securitySettings.biometricEnabled) {
        if (!canAuthenticateBiometric && securitySettings.biometricEnabled) {
            securityManager.setBiometricEnabled(false)
        }
    }

    val backupManager = remember { BackupManager(context.applicationContext, repository) }
    val lastBackupMillis by backupManager.lastBackupFlow.collectAsState(initial = null)
    val voiceNoteSecurityManager = rememberVoiceNoteSecurityManager()
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

    // 📦 Launcher para restaurar backup ZIP (incluye fotos)
    val zipBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    // Validar que sea un archivo .zip
                    val fileName = getFileNameFromUri(context, it) ?: ""
                    if (!fileName.endsWith(".zip", ignoreCase = true)) {
                        snackbarMessage = "⚠️ Por favor selecciona un archivo .zip"
                        return@launch
                    }

                    // Copiar a archivo temporal
                    val tempFile = withContext(Dispatchers.IO) {
                        val temp = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
                        context.contentResolver.openInputStream(it)?.use { input ->
                            temp.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        temp
                    }

                    // Detectar si el ZIP está cifrado
                    val isEncrypted = withContext(Dispatchers.IO) {
                        try {
                            val zipFile = net.lingala.zip4j.ZipFile(tempFile)
                            zipFile.isEncrypted
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (isEncrypted) {
                        // Mostrar diálogo para pedir contraseña
                        pendingEncryptedBackupFile = tempFile
                        showBackupPasswordDialog = true
                    } else {
                        // Extraer directamente si no está cifrado
                        try {
                            val parsed = BackupZipManager.extractBackupZip(context, tempFile)
                            repository.replaceAllData(parsed.medications, parsed.history)
                            snackbarMessage = "✅ Backup restaurado correctamente (con fotos)"
                        } finally {
                            tempFile.delete()
                        }
                    }
                } catch (e: Exception) {
                    snackbarMessage = "❌ Error al restaurar backup: ${e.message}"
                }
            }
        }
    }

    // 📄 Launcher para importar CSV (sin fotos)
    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    // Validar que sea un archivo .csv
                    val fileName = getFileNameFromUri(context, it) ?: ""
                    if (!fileName.endsWith(".csv", ignoreCase = true)) {
                        snackbarMessage = "⚠️ Por favor selecciona un archivo .csv"
                        return@launch
                    }

                    val csvContent = readTextFromUri(context, it)
                    val parsed = CsvUtils.parseCsv(csvContent)
                    repository.replaceAllData(parsed.medications, parsed.history)
                    snackbarMessage = "✅ CSV importado correctamente"
                } catch (e: Exception) {
                    snackbarMessage = "❌ Error al importar CSV: ${e.message}"
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

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingsItem(
                        title = "Chips de recuento",
                        description = "Período: ${chipCountPeriod.title}",
                        icon = Icons.Default.Tag,
                        onClick = { showChipPeriodDialog = true }
                    )
                }
            }

            // Sección de Ordenamiento
            item {
                SettingsSection(title = "🔢 Ordenamiento") {
                    SettingsItem(
                        title = "Ordenar medicamentos por",
                        description = "Actual: ${sortMode.title}",
                        icon = Icons.Default.Sort,
                        onClick = { showSortModeDialog = true }
                    )
                }
            }

            item {
                SettingsSection(title = "🔒 Seguridad") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bloqueo de la app",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (securitySettings.lockEnabled) "Activado" else "Desactivado",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = securitySettings.lockEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    if (enabled) {
                                        if (!securitySettings.isPinConfigured) {
                                            pendingEnableLock = true
                                            showPinSetupDialog = true
                                            snackbarMessage = "Configura un PIN para activar el bloqueo"
                                        } else {
                                            securityManager.setLockEnabled(true)
                                            securityManager.recordUnlock()
                                            snackbarMessage = "Bloqueo activado"
                                        }
                                    } else {
                                        securityManager.setLockEnabled(false)
                                        securityManager.setBiometricEnabled(false)
                                        snackbarMessage = "Bloqueo desactivado"
                                    }
                                }
                            }
                        )
                    }

                    if (securitySettings.lockEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))

                        SettingsItem(
                            title = "Cambiar PIN",
                            description = "Actualizar código de acceso",
                            icon = Icons.Default.Lock,
                            onClick = { showPinChangeDialog = true }
                        )

                        if (canAuthenticateBiometric) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Desbloquear con huella",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (securitySettings.biometricEnabled) "Activado" else "Desactivado",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = securitySettings.biometricEnabled,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            securityManager.setBiometricEnabled(checked)
                                        }
                                    }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tu dispositivo no soporta autenticación biométrica fuerte.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        val timeoutLabel = lockTimeoutLabel(securitySettings.autoLockDurationMs)
                        SettingsItem(
                            title = "Tiempo antes de bloquear",
                            description = timeoutLabel,
                            icon = Icons.Default.Schedule,
                            onClick = { showTimeoutPicker = true }
                        )
                    } else if (!securitySettings.isPinConfigured) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsItem(
                            title = "Configurar PIN",
                            description = "Define un código para proteger tu información",
                            icon = Icons.Default.Security,
                            onClick = { showPinSetupDialog = true }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = "🔔 Notificaciones") {
                    Text(text = "🚧 EN CONSTRUCCIÓN", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) //Con esto ponemos el texto en negrita, con el parámetro fontWeight = FontWeight.Bold dentro de style = MaterialTheme.typography.bodyLarge.copy
                    )
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
                    // 📄 Exportar CSV (sin fotos)
                    SettingsItem(
                        title = "Exportar CSV",
                        description = "Crear archivo CSV con medicamentos e historial",
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            scope.launch {
                                try {
                                    val file = createExportFile(context, repository)
                                    shareFile(context, file)
                                    snackbarMessage = "✅ CSV exportado: ${file.name}"
                                } catch (e: Exception) {
                                    snackbarMessage = "❌ Error al exportar: ${e.message}"
                                }
                            }
                        }
                    )

                    // 🎙️ Exportar historial con audios
                    SettingsItem(
                        title = "Exportar historial con audios",
                        description = "Exportar historial completo con notas de voz encriptadas",
                        icon = Icons.Default.Mic,
                        onClick = {
                            showHistoryExportDialog = true
                        }
                    )

                    // 🔄 Restaurar Backup (importar ZIP con fotos)
                    SettingsItem(
                        title = "Restaurar Backup",
                        description = "Importar desde archivo .zip (incluye fotos)",
                        icon = Icons.Default.Restore,
                        onClick = {
                            zipBackupLauncher.launch("*/*")
                        }
                    )

                    // 📥 Importar CSV (sin fotos)
                    SettingsItem(
                        title = "Importar CSV",
                        description = "Cargar datos desde archivo .csv (sin fotos)",
                        icon = Icons.Default.FileUpload,
                        onClick = {
                            csvImportLauncher.launch("*/*")
                        }
                    )

                    // 🗑️ Limpiar todos los datos
                    SettingsItem(
                        title = "Limpiar todos los datos",
                        description = "Eliminar todos los medicamentos y entradas",
                        icon = Icons.Default.Delete,
                        onClick = {
                            repository.clearAllData()
                            snackbarMessage = "✅ Datos eliminados"
                        }
                    )

                    // 💾 AutoBackup (cada 24h)
                    SettingsItem(
                        title = "Copia de seguridad automática",
                        description = "Última copia: $lastBackupText",
                        icon = Icons.Default.Backup,
                        onClick = {
                            scope.launch {
                                try {
                                    val file = backupManager.createManualBackup()
                                    snackbarMessage = "✅ Copia guardada: ${file.name}"
                                } catch (e: Exception) {
                                    snackbarMessage = "❌ Error en copia: ${e.message}"
                                }
                            }
                        }
                    )

                    // 📦 Exportar Backup (con opción de cifrado AES-256)
                    SettingsItem(
                        title = "Exportar Backup",
                        description = "Compartir backup completo con fotos (con/sin cifrado)",
                        icon = Icons.Default.Lock,
                        onClick = {
                            showBackupExportDialog = true
                        }
                    )

                    // 📁 Ver carpeta de backups
                    SettingsItem(
                        title = "Ver carpeta de backups",
                        description = "Abrir ubicación de copias de seguridad",
                        icon = Icons.Default.Folder,
                        onClick = {
                            openBackupsFolder(context)
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "🎙️ Notas de Voz") {
                    SettingsItem(
                        title = "Cambiar contraseña de notas",
                        description = "Modificar contraseña y pista de las notas de voz",
                        icon = Icons.Default.Lock,
                        onClick = { showVoiceNotePasswordDialog = true }
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

    if (showChipPeriodDialog) {
        AlertDialog(
            onDismissRequest = { showChipPeriodDialog = false },
            title = { Text("Chips de recuento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.d4vram.pillmate2.ui.theme.ChipCountPeriod.entries.forEach { period ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showChipPeriodDialog = false
                                    scope.launch { themeManager.setChipCountPeriod(period) }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = period == chipCountPeriod,
                                onClick = {
                                    showChipPeriodDialog = false
                                    scope.launch { themeManager.setChipCountPeriod(period) }
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = period.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = period.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChipPeriodDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showSortModeDialog) {
        AlertDialog(
            onDismissRequest = { showSortModeDialog = false },
            title = { Text("Ordenar medicamentos por") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SortMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showSortModeDialog = false
                                    scope.launch { themeManager.setSortMode(mode) }
                                    snackbarMessage = "Ordenamiento cambiado a: ${mode.title}"
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == sortMode,
                                onClick = {
                                    showSortModeDialog = false
                                    scope.launch { themeManager.setSortMode(mode) }
                                    snackbarMessage = "Ordenamiento cambiado a: ${mode.title}"
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortModeDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            onDismiss = {
                showPinSetupDialog = false
                pendingEnableLock = false
            },
            onConfirm = { pin ->
                try {
                    securityManager.setPin(pin)
                    securityManager.recordUnlock()
                    if (pendingEnableLock) {
                        securityManager.setLockEnabled(true)
                        pendingEnableLock = false
                        snackbarMessage = "Bloqueo activado"
                    } else {
                        snackbarMessage = "PIN guardado"
                    }
                    null
                } catch (e: Exception) {
                    e.message ?: "No se pudo guardar el PIN"
                }
            }
        )
    }

    if (showPinChangeDialog) {
        PinChangeDialog(
            onDismiss = { showPinChangeDialog = false },
            onConfirm = { currentPin, newPin ->
                try {
                    val valid = securityManager.verifyPin(currentPin)
                    if (!valid) {
                        "PIN actual incorrecto"
                    } else {
                        securityManager.setPin(newPin)
                        snackbarMessage = "PIN actualizado"
                        null
                    }
                } catch (e: Exception) {
                    e.message ?: "Error al actualizar el PIN"
                }
            }
        )
    }

    if (showTimeoutPicker) {
        LockTimeoutDialog(
            currentDuration = securitySettings.autoLockDurationMs,
            onDismiss = { showTimeoutPicker = false },
            onTimeoutSelected = { duration ->
                securityManager.updateAutoLockDuration(duration)
                snackbarMessage = "Tiempo de bloqueo actualizado"
                showTimeoutPicker = false
            }
        )
    }

    if (showVoiceNotePasswordDialog) {
        VoiceNotePasswordChangeDialog(
            onDismiss = { showVoiceNotePasswordDialog = false },
            onConfirm = { currentPassword, newPassword, newHint ->
                scope.launch {
                    try {
                        val success = voiceNoteSecurityManager.changePassword(currentPassword, newPassword, newHint)
                        if (success) {
                            snackbarMessage = "Contraseña de notas actualizada"
                            showVoiceNotePasswordDialog = false
                        } else {
                            snackbarMessage = "Contraseña actual incorrecta"
                        }
                    } catch (e: Exception) {
                        snackbarMessage = e.localizedMessage ?: "Error al cambiar contraseña"
                    }
                }
            }
        )
    }

    if (showHistoryExportDialog) {
        HistoryExportDialog(
            onDismiss = { showHistoryExportDialog = false },
            onExport = { includeAudioNotes ->
                scope.launch {
                    try {
                        val file = exportHistoryWithAudio(context, repository, includeAudioNotes)
                        shareFile(context, file)
                        snackbarMessage = if (includeAudioNotes) {
                            "Historial exportado con ${file.length() / 1024}KB de audios"
                        } else {
                            "Historial exportado sin audios"
                        }
                        showHistoryExportDialog = false
                    } catch (e: Exception) {
                        snackbarMessage = "Error al exportar: ${e.message}"
                    }
                }
            }
        )
    }

    if (showBackupExportDialog) {
        BackupExportDialog(
            onDismiss = { showBackupExportDialog = false },
            onExport = { password ->
                scope.launch {
                    try {
                        val medications = repository.getMedicationsSnapshot()
                        val history = repository.getHistorySnapshot()
                        val directory = context.getExternalFilesDir("exports") ?: context.filesDir
                        if (!directory.exists()) directory.mkdirs()
                        val timestamp = System.currentTimeMillis()
                        val file = File(directory, "pillmate_backup_$timestamp.zip")

                        if (password != null) {
                            // Exportar con cifrado AES-256
                            BackupZipManager.createEncryptedBackupZip(context, medications, history, file, password)
                            snackbarMessage = "✅ Backup cifrado exportado: ${file.name}"
                        } else {
                            // Exportar sin cifrar
                            BackupZipManager.createBackupZip(context, medications, history, file)
                            snackbarMessage = "✅ Backup exportado: ${file.name}"
                        }

                        shareFile(context, file)
                        showBackupExportDialog = false
                    } catch (e: Exception) {
                        snackbarMessage = "❌ Error al exportar backup: ${e.message}"
                    }
                }
            }
        )
    }

    if (showBackupPasswordDialog) {
        BackupPasswordInputDialog(
            onDismiss = {
                showBackupPasswordDialog = false
                pendingEncryptedBackupFile?.delete()
                pendingEncryptedBackupFile = null
            },
            onConfirm = { password ->
                scope.launch {
                    try {
                        pendingEncryptedBackupFile?.let { tempFile ->
                            try {
                                val parsed = BackupZipManager.extractBackupZip(context, tempFile, password)
                                repository.replaceAllData(parsed.medications, parsed.history)
                                snackbarMessage = "✅ Backup cifrado restaurado correctamente (con fotos)"
                            } finally {
                                tempFile.delete()
                                pendingEncryptedBackupFile = null
                            }
                        }
                        showBackupPasswordDialog = false
                    } catch (e: Exception) {
                        snackbarMessage = "❌ Error: ${e.message ?: "Contraseña incorrecta"}"
                        pendingEncryptedBackupFile?.delete()
                        pendingEncryptedBackupFile = null
                        showBackupPasswordDialog = false
                    }
                }
            }
        )
    }
}

private val lockTimeoutOptions = listOf(
    0L to "Inmediato",
    30_000L to "30 segundos",
    60_000L to "1 minuto",
    300_000L to "5 minutos",
    600_000L to "10 minutos"
)

private fun lockTimeoutLabel(durationMs: Long): String {
    return lockTimeoutOptions.firstOrNull { it.first == durationMs }?.second
        ?: "Personalizado (${durationMs / 1000}s)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onConfirm: suspend (String) -> String?
) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Configurar PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8) pin = it.filter { ch -> ch.isDigit() } },
                    label = { Text("PIN (4-8 dígitos)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8) confirmPin = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Confirmar PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = {
                    when {
                        pin.length < 4 -> errorMessage = "El PIN debe tener al menos 4 dígitos"
                        pin != confirmPin -> errorMessage = "Los PIN no coinciden"
                        else -> {
                            errorMessage = null
                            isSubmitting = true
                            scope.launch {
                                val error = onConfirm(pin)
                                isSubmitting = false
                                if (error == null) {
                                    pin = ""
                                    confirmPin = ""
                                    onDismiss()
                                } else {
                                    errorMessage = error
                                }
                            }
                        }
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinChangeDialog(
    onDismiss: () -> Unit,
    onConfirm: suspend (String, String) -> String?
) {
    val scope = rememberCoroutineScope()
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmNewPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Cambiar PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { if (it.length <= 8) currentPin = it.filter { ch -> ch.isDigit() } },
                    label = { Text("PIN actual") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 8) newPin = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Nuevo PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = confirmNewPin,
                    onValueChange = { if (it.length <= 8) confirmNewPin = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Confirmar nuevo PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = {
                    when {
                        newPin.length < 4 -> errorMessage = "El nuevo PIN debe tener al menos 4 dígitos"
                        newPin != confirmNewPin -> errorMessage = "Los nuevos PIN no coinciden"
                        newPin == currentPin -> errorMessage = "El nuevo PIN debe ser diferente"
                        else -> {
                            errorMessage = null
                            isSubmitting = true
                            scope.launch {
                                val error = onConfirm(currentPin, newPin)
                                isSubmitting = false
                                if (error == null) {
                                    currentPin = ""
                                    newPin = ""
                                    confirmNewPin = ""
                                    onDismiss()
                                } else {
                                    errorMessage = error
                                }
                            }
                        }
                    }
                }
            ) {
                Text("Actualizar")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun LockTimeoutDialog(
    currentDuration: Long,
    onDismiss: () -> Unit,
    onTimeoutSelected: suspend (Long) -> Unit
) {
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tiempo antes de bloquear") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lockTimeoutOptions.forEach { (duration, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch {
                                    onTimeoutSelected(duration)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = duration == currentDuration,
                            onClick = {
                                scope.launch {
                                    onTimeoutSelected(duration)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label)
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

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            fileName = cursor.getString(nameIndex)
        }
    }
    return fileName
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

/**
 * Abre la carpeta de backups en el explorador de archivos
 * Ruta accesible sin root: /Android/data/com.d4vram.pillmate2/files/backups/
 */
private fun openBackupsFolder(context: Context) {
    try {
        val backupsDir = context.getExternalFilesDir("backups")

        if (backupsDir == null || !backupsDir.exists()) {
            android.widget.Toast.makeText(
                context,
                "⚠️ No hay backups todavía. Crea uno primero.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Intent para abrir la carpeta
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                backupsDir
            )
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Abrir carpeta de backups"))
        } catch (e: Exception) {
            // Fallback: Mostrar la ruta en un Toast largo
            val path = backupsDir.absolutePath
            android.widget.Toast.makeText(
                context,
                "📂 Carpeta de backups:\n$path\n\nAbre tu explorador de archivos y navega a esta ruta.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "❌ Error al abrir carpeta: ${e.message}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
private fun VoiceNotePasswordChangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (currentPassword: String, newPassword: String, newHint: String?) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    
    val isValid = currentPassword.isNotBlank() && 
                  newPassword.isNotBlank() && 
                  confirmPassword.isNotBlank() && 
                  newPassword == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contraseña de notas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Contraseña actual") },
                    singleLine = true,
                    visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                            Icon(
                                imageVector = if (currentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nueva contraseña") },
                    singleLine = true,
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmar nueva contraseña") },
                    singleLine = true,
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    isError = confirmPassword.isNotBlank() && newPassword != confirmPassword,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text("Nueva pista (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (confirmPassword.isNotBlank() && newPassword != confirmPassword) {
                    Text(
                        text = "Las contraseñas no coinciden",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentPassword, newPassword, hint.ifBlank { null }) },
                enabled = isValid
            ) {
                Text("Cambiar")
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
private fun HistoryExportDialog(
    onDismiss: () -> Unit,
    onExport: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📦 Exportar historial") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Elige cómo exportar tu historial de medicamentos:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🔒 Seguridad",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Las notas de voz siempre se exportan encriptadas con AES-256",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onExport(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Con notas de voz")
                }
                OutlinedButton(
                    onClick = { onExport(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Solo texto (CSV)")
                }
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
private fun BackupExportDialog(
    onDismiss: () -> Unit,
    onExport: (password: String?) -> Unit
) {
    var showPasswordInput by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val passwordsMatch = password.isNotBlank() && password == confirmPassword
    val isValid = passwordsMatch && password.length >= 6

    if (!showPasswordInput) {
        // Primer paso: elegir si cifrar o no
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("📦 Exportar Backup Completo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Elige cómo exportar tu backup completo (medicamentos, historial y fotos):",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🔒 Cifrado AES-256",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "El cifrado protege tus datos con encriptación de grado militar. Recomendado si vas a compartir el backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showPasswordInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Con cifrado AES-256")
                    }
                    OutlinedButton(
                        onClick = {
                            onExport(null) // Sin cifrado
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sin cifrar")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        )
    } else {
        // Segundo paso: ingresar contraseña
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("🔐 Contraseña para el backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Introduce una contraseña segura para cifrar el backup:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña (mín. 6 caracteres)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirmar contraseña") },
                        singleLine = true,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        isError = confirmPassword.isNotBlank() && password != confirmPassword,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (confirmPassword.isNotBlank() && password != confirmPassword) {
                        Text(
                            text = "Las contraseñas no coinciden",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (password.isNotBlank() && password.length < 6) {
                        Text(
                            text = "La contraseña debe tener al menos 6 caracteres",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onExport(password)
                        onDismiss()
                    },
                    enabled = isValid
                ) {
                    Text("Exportar cifrado")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordInput = false }) {
                    Text("Atrás")
                }
            }
        )
    }
}

@Composable
private fun BackupPasswordInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔐 Backup cifrado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Este backup está protegido con cifrado AES-256. Introduce la contraseña para descifrarlo:",
                    style = MaterialTheme.typography.bodyMedium
                )

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
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) {
                Text("Descifrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Exporta el historial con opción de incluir notas de voz encriptadas
 */
private suspend fun exportHistoryWithAudio(
    context: Context,
    repository: MedicationRepository,
    includeAudioNotes: Boolean
): File = withContext(Dispatchers.IO) {
    if (!includeAudioNotes) {
        // Exportación simple CSV sin audios
        return@withContext createExportFile(context, repository)
    }

    // Exportación completa con audios encriptados
    val audioNoteManager = com.d4vram.pillmate2.data.AudioNoteManager(context)
    val database = com.d4vram.pillmate2.data.local.PillMateDatabase.getInstance(context)
    val timestamp = System.currentTimeMillis()
    val zipFile = File(context.cacheDir, "PillMate_Export_$timestamp.zip")

    java.util.zip.ZipOutputStream(zipFile.outputStream().buffered()).use { zipOut ->
        // 1. Añadir CSV del historial
        val csvFile = createExportFile(context, repository)
        zipOut.putNextEntry(java.util.zip.ZipEntry("history.csv"))
        csvFile.inputStream().use { it.copyTo(zipOut) }
        zipOut.closeEntry()

        // 2. Añadir audios encriptados
        val history = database.historyDao().getAllHistory()
        val audioEntries = history.filter { entry ->
            !entry.audioNoteUri.isNullOrBlank()
        }

        if (audioEntries.isNotEmpty()) {
            // Crear directorio de audios en el ZIP
            zipOut.putNextEntry(java.util.zip.ZipEntry("audio_notes/"))
            zipOut.closeEntry()

            // Añadir cada audio encriptado
            audioEntries.forEach { entry ->
                entry.audioNoteUri?.let { audioId ->
                    // Archivo encriptado
                    audioNoteManager.getEncryptedFile(audioId)?.let { encFile ->
                        zipOut.putNextEntry(java.util.zip.ZipEntry("audio_notes/$audioId.enc"))
                        encFile.inputStream().use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }

                    // Metadatos de encriptación
                    audioNoteManager.getEncryptionMetadata(audioId)?.let { metadata ->
                        val metadataJson = """{"salt":"${metadata.salt}","iv":"${metadata.iv}"}"""
                        zipOut.putNextEntry(java.util.zip.ZipEntry("audio_notes/$audioId.json"))
                        zipOut.write(metadataJson.toByteArray())
                        zipOut.closeEntry()
                    }
                }
            }
        }

        // 3. Añadir archivo README con instrucciones
        val readme = """
            PillMate Export Package
            =======================

            Contenido:
            - history.csv: Historial completo en formato CSV
            - audio_notes/: Notas de voz encriptadas (AES-256-GCM)

            Las notas de voz están encriptadas y requieren la misma
            contraseña utilizada en la app para desencriptarlas.

            Formato de audios:
            - *.enc: Archivo de audio encriptado
            - *.json: Metadatos de encriptación (salt e IV)

            Exportado el: ${Date(timestamp)}
        """.trimIndent()

        zipOut.putNextEntry(java.util.zip.ZipEntry("README.txt"))
        zipOut.write(readme.toByteArray())
        zipOut.closeEntry()
    }

    return@withContext zipFile
}
