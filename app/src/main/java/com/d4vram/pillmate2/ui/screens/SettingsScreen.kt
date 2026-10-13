package com.d4vram.pillmate2.ui.screens

import com.d4vram.pillmate2.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.data.BackupManager
import com.d4vram.pillmate2.data.BackupSafManager
import com.d4vram.pillmate2.data.BackupScheduler
import com.d4vram.pillmate2.data.BackupZipManager
import com.d4vram.pillmate2.data.CsvUtils
import com.d4vram.pillmate2.ui.security.SecurityManager
import com.d4vram.pillmate2.ui.security.SecuritySettings
import com.d4vram.pillmate2.ui.security.rememberVoiceNoteSecurityManager
import com.d4vram.pillmate2.ui.export.PdfExportSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.d4vram.pillmate2.ui.theme.AppDesign
import com.d4vram.pillmate2.ui.theme.AppUiStyle
import com.d4vram.pillmate2.ui.theme.QuickTakeWidgetColor
import com.d4vram.pillmate2.ui.theme.QuickTakeWidgetShape
import com.d4vram.pillmate2.ui.theme.QuickTakeWidgetSize
import com.d4vram.pillmate2.ui.theme.ThemeManager
import com.d4vram.pillmate2.widget.QuickTakeWidget
import com.d4vram.pillmate2.widget.fittedNameSp
import com.d4vram.pillmate2.widget.readableOn
import com.d4vram.pillmate2.notifications.NotificationHelper
import android.os.Build
import android.Manifest
import android.provider.Settings
import androidx.biometric.BiometricManager
import com.d4vram.pillmate2.model.SortMode
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.glance.appwidget.updateAll

/**
 * ⚙️ Pantalla de configuración
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeManager: ThemeManager,
    repository: MedicationRepository,
    securityManager: SecurityManager,
    onNavigateToPdfExport: () -> Unit = {},
    onShowOnboarding: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode by themeManager.isDarkMode.collectAsState(initial = false)
    val appDesign by themeManager.appDesign.collectAsState(initial = AppDesign.Astra)
    val uiStyle by themeManager.uiStyle.collectAsState(initial = AppUiStyle.Default)
    val chipCountPeriod by themeManager.chipCountPeriod.collectAsState(initial = com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Total)
    val quickTakeWidgetShape by themeManager.quickTakeWidgetShape.collectAsState(initial = QuickTakeWidgetShape.Rounded)
    val quickTakeWidgetColor by themeManager.quickTakeWidgetColor.collectAsState(initial = QuickTakeWidgetColor.Green)
    val quickTakeWidgetSize by themeManager.quickTakeWidgetSize.collectAsState(initial = QuickTakeWidgetSize.Normal)
    val quickTakeMedicationIds by themeManager.quickTakeMedicationIds.collectAsState(initial = emptyList())
    val quickTakeNotificationEnabled by themeManager.quickTakeNotificationEnabled.collectAsState(initial = false)
    var previewSlots by remember { mutableStateOf<List<Medication?>>(List(4) { null }) }

    LaunchedEffect(quickTakeMedicationIds) {
        val allMeds = withContext(Dispatchers.IO) {
            repository.getMedicationsSnapshot().associateBy { it.id }
        }
        previewSlots = quickTakeMedicationIds.map { id -> id?.let { allMeds[it] } }
    }

    val sortMode by themeManager.sortMode.collectAsState(initial = SortMode.MANUAL)
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var showUiDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showChipPeriodDialog by remember { mutableStateOf(false) }
    var showSortModeDialog by remember { mutableStateOf(false) }
    val securitySettings by securityManager.settings.collectAsState(initial = SecuritySettings())
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var showTimeoutPicker by remember { mutableStateOf(false) }
    var pendingEnableLock by remember { mutableStateOf(false) }
    var showVoiceNotePasswordDialog by remember { mutableStateOf(false) }
    var showVoiceNoteResetDialog by remember { mutableStateOf(false) }
    var showPinVerificationForReset by remember { mutableStateOf(false) }
    var showHistoryExportDialog by remember { mutableStateOf(false) }
    var showBackupExportDialog by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var pendingEncryptedBackupFile by remember { mutableStateOf<File?>(null) }
    var showPdfExportSheet by remember { mutableStateOf(false) }
    var showAutoBackupIntervalDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }

    // Estado de permisos de notificación
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationHelper.hasNotificationPermission(context))
    }
    var hasExactAlarmPermission by remember {
        mutableStateOf(NotificationHelper.hasExactAlarmPermission(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Launcher para solicitar permiso de notificaciones (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        hasExactAlarmPermission = NotificationHelper.hasExactAlarmPermission(context)
        if (isGranted) {
            snackbarMessage = context.getString(R.string.settings_notifications_on)
        } else {
            snackbarMessage = context.getString(R.string.settings_notifications_denied)
        }
    }

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
    val backupSafManager = remember { BackupSafManager(context.applicationContext, repository) }
    val lastBackupMillis by backupManager.lastBackupFlow.collectAsState(initial = null)
    val autoBackupEnabled by backupManager.autoBackupEnabledFlow.collectAsState(initial = false)
    val autoBackupIntervalHours by backupManager.autoBackupIntervalHoursFlow.collectAsState(initial = 24)
    val autoBackupFolderName by backupSafManager.autoBackupFolderUri.collectAsState(initial = null)
    val voiceNoteSecurityManager = rememberVoiceNoteSecurityManager()
    val noBackupsText = stringResource(R.string.settings_no_backups)
    val configuredText = stringResource(R.string.settings_configured)
    val notConfiguredText = stringResource(R.string.settings_not_configured)
    val lastBackupText = remember(lastBackupMillis, noBackupsText) {
        lastBackupMillis?.let { millis ->
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
        } ?: noBackupsText
    }
    val autoBackupFolderText = remember(autoBackupFolderName, configuredText, notConfiguredText) {
        if (autoBackupFolderName != null) configuredText else notConfiguredText
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = NotificationHelper.hasNotificationPermission(context)
                hasExactAlarmPermission = NotificationHelper.hasExactAlarmPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            snackbarMessage = null
        }
    }

    // 📦 Launcher para crear backup manual con SAF
    val createBackupSafLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val success = backupSafManager.createManualBackupToUri(it)
                    if (success) {
                        snackbarMessage = context.getString(R.string.settings_backup_created)
                    } else {
                        snackbarMessage = context.getString(R.string.settings_backup_create_failed)
                    }
                } catch (e: Exception) {
                    snackbarMessage = context.getString(R.string.settings_error_with_message, e.message.orEmpty())
                }
            }
        }
    }

    // 📁 Launcher para elegir carpeta de auto-backups
    val selectAutoBackupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    backupSafManager.saveAutoBackupFolderUri(it)
                    BackupScheduler.refresh(context.applicationContext)
                    snackbarMessage = context.getString(R.string.settings_autobackup_folder_set)
                } catch (e: Exception) {
                    snackbarMessage = context.getString(R.string.settings_autobackup_folder_failed, e.message.orEmpty())
                }
            }
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
                        snackbarMessage = context.getString(R.string.settings_pick_zip)
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
                        pendingRestore = PendingRestore(
                            title = context.getString(R.string.settings_restore_backup_title),
                            description = context.getString(R.string.settings_restore_backup_desc),
                            zipFile = tempFile
                        )
                        snackbarMessage = null
                    }
                } catch (e: Exception) {
                    snackbarMessage = context.getString(R.string.settings_restore_failed, e.message.orEmpty())
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
                        snackbarMessage = context.getString(R.string.settings_pick_csv)
                        return@launch
                    }

                    val csvContent = readTextFromUri(context, it)
                    val parsed = CsvUtils.parseCsv(csvContent)
                    pendingRestore = PendingRestore(
                        title = context.getString(R.string.settings_import_csv_title),
                        description = context.getString(R.string.settings_import_csv_desc, parsed.medications.size, parsed.history.size),
                        medications = parsed.medications,
                        history = parsed.history
                    )
                } catch (e: Exception) {
                    snackbarMessage = context.getString(R.string.settings_import_csv_failed, e.message.orEmpty())
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
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
                SettingsSection(title = stringResource(R.string.settings_appearance)) {
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
                                text = stringResource(R.string.settings_dark_mode),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(if (isDarkMode) R.string.settings_on else R.string.settings_off),
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(stringResource(R.string.settings_screen_design), style = MaterialTheme.typography.titleMedium)
                    AppDesign.entries.forEach { design ->
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = appDesign == design,
                                role = Role.RadioButton,
                                onClick = { scope.launch { themeManager.setAppDesign(design) } }
                            ).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appDesign == design,
                                onClick = null
                            )
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(design.titleRes))
                                Text(
                                    stringResource(if (design == AppDesign.Astra) R.string.settings_design_rhythm_desc else R.string.settings_design_classic_desc),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingsItem(
                        title = stringResource(R.string.settings_ui_style),
                        description = stringResource(R.string.settings_current, stringResource(uiStyle.titleRes)),
                        icon = Icons.Default.Palette,
                        onClick = { showUiDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingsItem(
                        title = stringResource(R.string.settings_chip_count),
                        description = stringResource(R.string.settings_chip_period, stringResource(chipCountPeriod.titleRes)),
                        icon = Icons.Default.Tag,
                        onClick = { showChipPeriodDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    WidgetCustomizationCard(
                        color = quickTakeWidgetColor,
                        size = quickTakeWidgetSize,
                        shape = quickTakeWidgetShape,
                        slots = previewSlots,
                        onColorChange = { newColor ->
                            scope.launch {
                                themeManager.setQuickTakeWidgetColor(newColor)
                                QuickTakeWidget().updateAll(context)
                            }
                        },
                        onSizeChange = { newSize ->
                            scope.launch {
                                themeManager.setQuickTakeWidgetSize(newSize)
                                QuickTakeWidget().updateAll(context)
                            }
                        },
                        onShapeChange = { newShape ->
                            scope.launch {
                                themeManager.setQuickTakeWidgetShape(newShape)
                                QuickTakeWidget().updateAll(context)
                            }
                        }
                    )
                }
            }

            // Sección de Ordenamiento
            item {
                SettingsSection(title = stringResource(R.string.settings_sorting)) {
                    SettingsItem(
                        title = stringResource(R.string.settings_sort_by),
                        description = stringResource(R.string.settings_current, stringResource(sortMode.titleRes)),
                        icon = Icons.AutoMirrored.Filled.Sort,
                        onClick = { showSortModeDialog = true }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_security)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_app_lock),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(if (securitySettings.lockEnabled) R.string.settings_on else R.string.settings_off),
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
                                            snackbarMessage = context.getString(R.string.settings_set_pin_first)
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
                            title = stringResource(R.string.settings_change_pin),
                            description = stringResource(R.string.settings_change_pin_desc),
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
                                        text = stringResource(R.string.settings_fingerprint),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(if (securitySettings.biometricEnabled) R.string.settings_on else R.string.settings_off),
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
                                text = stringResource(R.string.settings_no_biometrics),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        val timeoutLabel = lockTimeoutLabel(securitySettings.autoLockDurationMs)
                        SettingsItem(
                            title = stringResource(R.string.settings_lock_timeout),
                            description = timeoutLabel,
                            icon = Icons.Default.Schedule,
                            onClick = { showTimeoutPicker = true }
                        )
                    } else if (!securitySettings.isPinConfigured) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsItem(
                            title = stringResource(R.string.settings_setup_pin),
                            description = stringResource(R.string.settings_setup_pin_desc),
                            icon = Icons.Default.Security,
                            onClick = { showPinSetupDialog = true }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_notifications)) {
                    // Estado del permiso de notificaciones
                    if (!hasNotificationPermission) {
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
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_permission_required),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_permission_required_desc),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.action_enable))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_exact_alarms_off),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_exact_alarms_off_desc),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Button(
                                    onClick = {
                                        NotificationHelper.openExactAlarmSettings(context)
                                    }
                                ) {
                                    Text(stringResource(R.string.action_allow))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    SettingsItem(
                        title = stringResource(R.string.settings_reminders),
                        description = when {
                            !hasNotificationPermission -> stringResource(R.string.settings_reminders_need_permission)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission -> stringResource(R.string.settings_reminders_need_exact)
                            else -> stringResource(R.string.settings_reminders_per_medication)
                        },
                        icon = Icons.Default.Notifications,
                        onClick = {
                            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission) {
                                NotificationHelper.openExactAlarmSettings(context)
                            } else {
                                snackbarMessage = context.getString(R.string.settings_reminders_hint)
                            }
                        }
                    )

                    SettingsItem(
                        title = stringResource(R.string.settings_quick_notification),
                        description = if (quickTakeNotificationEnabled)
                            stringResource(R.string.settings_quick_notification_on)
                        else
                            stringResource(R.string.settings_quick_notification_off),
                        hasSwitch = true,
                        checked = quickTakeNotificationEnabled,
                        icon = Icons.Default.Bolt,
                        onCheckedChange = { enabled ->
                            if (enabled && !hasNotificationPermission &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            scope.launch { themeManager.setQuickTakeNotificationEnabled(enabled) }
                            if (enabled) {
                                NotificationHelper.showQuickTakeNotification(context)
                            } else {
                                NotificationHelper.cancelQuickTakeNotification(context)
                            }
                        }
                    )

                    SettingsItem(
                        title = stringResource(R.string.settings_sound),
                        description = stringResource(R.string.settings_sound_desc),
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        onClick = {
                            // Abrir ajustes de notificación de la app
                            val intent = Intent().apply {
                                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_data)) {
                    // 📄 Exportar CSV (sin fotos)
                    SettingsItem(
                        title = stringResource(R.string.export_csv),
                        description = stringResource(R.string.settings_export_csv_desc),
                        icon = Icons.Default.FileDownload,
                        onClick = {
                            scope.launch {
                                try {
                                    val file = createExportFile(context, repository)
                                    shareFile(context, file)
                                    snackbarMessage = context.getString(R.string.settings_csv_exported, file.name)
                                } catch (e: Exception) {
                                    snackbarMessage = context.getString(R.string.settings_export_failed, e.message.orEmpty())
                                }
                            }
                        }
                    )

                    // 📑 Exportar PDF (reporte visual)
                    SettingsItem(
                        title = stringResource(R.string.export_pdf),
                        description = stringResource(R.string.settings_export_pdf_desc),
                        icon = Icons.Default.PictureAsPdf,
                        onClick = onNavigateToPdfExport
                    )

                    // 🎙️ Exportar historial con audios
                    SettingsItem(
                        title = stringResource(R.string.settings_export_audio),
                        description = stringResource(R.string.settings_export_audio_desc),
                        icon = Icons.Default.Mic,
                        onClick = {
                            showHistoryExportDialog = true
                        }
                    )

                    // 🔄 Restaurar Backup (importar ZIP con fotos)
                    SettingsItem(
                        title = stringResource(R.string.settings_restore_backup),
                        description = stringResource(R.string.settings_restore_backup_hint),
                        icon = Icons.Default.Restore,
                        onClick = {
                            zipBackupLauncher.launch("*/*")
                        }
                    )

                    // 📥 Importar CSV (sin fotos)
                    SettingsItem(
                        title = context.getString(R.string.settings_import_csv_title),
                        description = stringResource(R.string.settings_import_csv_hint),
                        icon = Icons.Default.FileUpload,
                        onClick = {
                            csvImportLauncher.launch("*/*")
                        }
                    )

                    // 🗑️ Limpiar todos los datos
                    SettingsItem(
                        title = stringResource(R.string.settings_clear_all),
                        description = stringResource(R.string.settings_clear_all_desc),
                        icon = Icons.Default.Delete,
                        onClick = {
                            showClearAllDialog = true
                        }
                    )

                    // 💾 Crear Backup Manual con SAF
                    SettingsItem(
                        title = stringResource(R.string.settings_create_backup),
                        description = stringResource(R.string.settings_create_backup_desc),
                        icon = Icons.Default.Save,
                        onClick = {
                            val timestamp = System.currentTimeMillis()
                            createBackupSafLauncher.launch("pillmate_backup_$timestamp.zip")
                        }
                    )

                    // 📁 Configurar carpeta de auto-backup
                    SettingsItem(
                        title = stringResource(R.string.settings_autobackup_folder),
                        description = autoBackupFolderText,
                        icon = Icons.Default.FolderOpen,
                        onClick = {
                            selectAutoBackupFolderLauncher.launch(null)
                        }
                    )

                    // 🧹 Limpiar configuración (solo si hay carpeta configurada)
                    if (autoBackupFolderName != null) {
                        SettingsItem(
                            title = stringResource(R.string.settings_clear_folder),
                            description = stringResource(R.string.settings_clear_folder_desc),
                            icon = Icons.Default.Clear,
                            onClick = {
                                scope.launch {
                                    backupSafManager.clearAutoBackupFolder()
                                    backupManager.setAutoBackupEnabled(false)
                                    BackupScheduler.refresh(context.applicationContext)
                                    snackbarMessage = context.getString(R.string.settings_folder_cleared)
                                }
                            }
                        )
                    }

                    // 💾 Info de último backup automático
                    SettingsItem(
                        title = stringResource(R.string.settings_last_autobackup),
                        description = lastBackupText,
                        icon = Icons.Default.Info,
                        onClick = {}
                    )

                    // 🚀 Crear auto-backup ahora
                    SettingsItem(
                        title = stringResource(R.string.settings_autobackup),
                        description = if (autoBackupEnabled) stringResource(R.string.settings_autobackup_on_every, formatInterval(autoBackupIntervalHours)) else stringResource(R.string.settings_off),
                        icon = Icons.Default.Autorenew,
                        hasSwitch = true,
                        checked = autoBackupEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                if (enabled && autoBackupFolderName == null) {
                                    snackbarMessage = context.getString(R.string.settings_choose_folder_first)
                                    return@launch
                                }
                                backupManager.setAutoBackupEnabled(enabled)
                                BackupScheduler.refresh(context.applicationContext)
                                snackbarMessage = if (enabled) {
                                    context.getString(R.string.settings_autobackup_enabled)
                                } else {
                                    context.getString(R.string.settings_autobackup_disabled)
                                }
                            }
                        }
                    )

                    SettingsItem(
                        title = stringResource(R.string.settings_autobackup_frequency),
                        description = stringResource(R.string.settings_every, formatInterval(autoBackupIntervalHours)),
                        icon = Icons.Default.Schedule,
                        onClick = {
                            showAutoBackupIntervalDialog = true
                        }
                    )

                    SettingsItem(
                        title = stringResource(R.string.settings_backup_now),
                        description = stringResource(R.string.settings_backup_now_desc),
                        icon = Icons.Default.Backup,
                        onClick = {
                            scope.launch {
                                try {
                                    val success = backupSafManager.createAutoBackupToFolder(force = true)
                                    if (success) {
                                        BackupScheduler.refresh(context.applicationContext)
                                        snackbarMessage = context.getString(R.string.settings_backup_in_folder)
                                    } else {
                                        snackbarMessage = context.getString(R.string.settings_choose_folder_first)
                                    }
                                } catch (e: Exception) {
                                    snackbarMessage = context.getString(R.string.settings_backup_failed, e.message.orEmpty())
                                }
                            }
                        }
                    )

                    // 📦 Exportar Backup (con opción de cifrado AES-256)
                    SettingsItem(
                        title = stringResource(R.string.settings_export_backup),
                        description = stringResource(R.string.settings_export_backup_desc),
                        icon = Icons.Default.Lock,
                        onClick = {
                            showBackupExportDialog = true
                        }
                    )

                    // 📁 Ver carpeta de backups
                    SettingsItem(
                        title = stringResource(R.string.settings_view_backups),
                        description = stringResource(R.string.settings_view_backups_desc),
                        icon = Icons.Default.Folder,
                        onClick = {
                            openBackupsFolder(context, scope, backupSafManager)
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

                    // 🔄 Restablecer contraseña (requiere PIN de la app)
                    if (securitySettings.isPinConfigured) {
                        SettingsItem(
                            title = "Restablecer contraseña de notas",
                            description = "Crear nueva contraseña (requiere PIN de la app)",
                            icon = Icons.Default.RestartAlt,
                            onClick = {
                                showPinVerificationForReset = true
                            }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = "ℹ️ Información") {
                    SettingsItem(
                        title = "Versión",
                        description = "PillMate v" + context.packageManager.getPackageInfo(context.packageName, 0).versionName,
                        icon = Icons.Default.Info
                    )
                    SettingsItem(
                        title = "Ver la introducción",
                        description = "Repasa cómo funciona PillMate",
                        icon = Icons.Default.School,
                        onClick = onShowOnboarding
                    )
                    SettingsItem(
                        title = "Acerca de",
                        description = "Autor, código fuente y cómo apoyar el proyecto",
                        icon = Icons.Default.QuestionMark,
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
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
                                    text = stringResource(style.titleRes),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = stringResource(style.descriptionRes),
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
                                    text = stringResource(period.titleRes),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = stringResource(period.descriptionRes),
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
                                    snackbarMessage = context.getString(R.string.settings_sort_changed, context.getString(mode.titleRes))
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == sortMode,
                                onClick = {
                                    showSortModeDialog = false
                                    scope.launch { themeManager.setSortMode(mode) }
                                    snackbarMessage = context.getString(R.string.settings_sort_changed, context.getString(mode.titleRes))
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(mode.titleRes),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = stringResource(mode.descriptionRes),
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

    // 🔐 Diálogo de verificación de PIN para restablecer contraseña
    if (showPinVerificationForReset) {
        PinVerificationDialog(
            onDismiss = { showPinVerificationForReset = false },
            onPinVerified = {
                showPinVerificationForReset = false
                showVoiceNoteResetDialog = true
            },
            securityManager = securityManager
        )
    }

    // 🔄 Diálogo para crear nueva contraseña de notas
    if (showVoiceNoteResetDialog) {
        VoiceNoteResetDialog(
            onDismiss = { showVoiceNoteResetDialog = false },
            onConfirm = { newPassword, newHint ->
                scope.launch {
                    try {
                        voiceNoteSecurityManager.setPassword(newPassword, newHint)
                        snackbarMessage = "✅ Contraseña de notas restablecida"
                        showVoiceNoteResetDialog = false
                    } catch (e: Exception) {
                        snackbarMessage = e.localizedMessage ?: "Error al restablecer contraseña"
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
                            pendingRestore = PendingRestore(
                                title = "Restaurar backup cifrado",
                                description = "Se reemplazarán tus datos actuales por el contenido del backup cifrado seleccionado. Antes se creará una copia local de seguridad.",
                                zipFile = tempFile,
                                password = password
                            )
                            pendingEncryptedBackupFile = null
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

    pendingRestore?.let { restore ->
        ConfirmRestoreDialog(
            restore = restore,
            onDismiss = {
                restore.zipFile?.delete()
                pendingRestore = null
            },
            onConfirm = {
                scope.launch {
                    try {
                        backupManager.createManualBackup()
                        val parsed = restore.zipFile?.let { file ->
                            BackupZipManager.extractBackupZip(context, file, restore.password)
                        }
                        val medications = parsed?.medications ?: restore.medications
                        val history = parsed?.history ?: restore.history
                        repository.replaceAllData(medications, history)
                        snackbarMessage = "✅ Datos restaurados correctamente"
                    } catch (e: Exception) {
                        snackbarMessage = "❌ Restauración cancelada: ${e.message}"
                    } finally {
                        restore.zipFile?.delete()
                        pendingRestore = null
                    }
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Eliminar todos los datos") },
            text = { Text("Antes de borrar se creará una copia local de seguridad. Esta acción eliminará medicamentos e historial de la app.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                backupManager.createManualBackup()
                                repository.clearAllData()
                                snackbarMessage = "✅ Datos eliminados"
                            } catch (e: Exception) {
                                snackbarMessage = "❌ No se eliminaron datos: ${e.message}"
                            } finally {
                                showClearAllDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showAutoBackupIntervalDialog) {
        AutoBackupIntervalDialog(
            selectedHours = autoBackupIntervalHours,
            onDismiss = { showAutoBackupIntervalDialog = false },
            onSelect = { hours ->
                scope.launch {
                    backupManager.setAutoBackupIntervalHours(hours)
                    BackupScheduler.refresh(context.applicationContext)
                    showAutoBackupIntervalDialog = false
                    snackbarMessage = "✅ Frecuencia actualizada"
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

private data class PendingRestore(
    val title: String,
    val description: String,
    val medications: List<Medication> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val zipFile: File? = null,
    val password: String? = null
)

private fun formatInterval(hours: Int): String = when {
    hours < 24 -> "$hours h"
    hours % 24 == 0 -> {
        val days = hours / 24
        if (days == 1) "1 día" else "$days días"
    }
    else -> "$hours h"
}

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
        title = { Text(stringResource(R.string.settings_setup_pin)) },
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
        title = { Text(stringResource(R.string.settings_change_pin)) },
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
        title = { Text(stringResource(R.string.settings_lock_timeout)) },
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

// Maqueta del widget 2x2. Sigue las mismas reglas que QuickTakeWidget (celdas que
// se reparten el espacio, círculo con el nombre debajo, sin dosis).
@Composable
private fun WidgetPreview(
    color: QuickTakeWidgetColor,
    size: QuickTakeWidgetSize,
    shape: QuickTakeWidgetShape,
    slots: List<Medication?>
) {
    val background = Color(color.backgroundArgb)
    val gap = size.gapDp.dp
    val previewSide = 190
    // Ancho de cada celda, para encoger los nombres largos igual que el widget real
    val cellWidthDp = (previewSide - size.paddingDp * 2 - size.gapDp) / 2f

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .size(previewSide.dp)
                .background(background, RoundedCornerShape(24.dp))
                .padding(size.paddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            if (size.showHeader) {
                Text(
                    text = "Toma rápida",
                    fontSize = size.textSp.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(color.accentArgb),
                    maxLines = 1
                )
            }
            for (row in 0..1) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    for (column in 0..1) {
                        PreviewCell(
                            medication = slots.getOrNull(row * 2 + column),
                            color = color,
                            size = size,
                            shape = shape,
                            cellWidthDp = cellWidthDp,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCell(
    medication: Medication?,
    color: QuickTakeWidgetColor,
    size: QuickTakeWidgetSize,
    shape: QuickTakeWidgetShape,
    cellWidthDp: Float,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalDensity.current.fontScale
    val fill = medication?.color ?: Color(color.emptyCellArgb)
    val glyph = when {
        medication == null -> "+"
        medication.emoji.isNotBlank() -> medication.emoji
        else -> medication.name.take(1).uppercase()
    }
    val glyphColor = if (medication == null) Color(color.accentArgb) else readableOn(fill)

    if (shape == QuickTakeWidgetShape.Circle) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .background(fill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(glyph, fontSize = (size.textSp + 6).sp, fontWeight = FontWeight.Bold, color = glyphColor)
            }
            if (medication != null) {
                Text(
                    text = medication.name,
                    fontSize = fittedNameSp(medication.name, size.textSp - 1, cellWidthDp, 1, fontScale).sp,
                    fontWeight = FontWeight.Medium,
                    color = readableOn(Color(color.backgroundArgb)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }

    val radius = if (shape == QuickTakeWidgetShape.Rounded) 16.dp else 4.dp
    Column(
        modifier = modifier
            .background(fill, RoundedCornerShape(radius))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(glyph, fontSize = (size.textSp + 6).sp, fontWeight = FontWeight.Bold, color = glyphColor)
        if (medication != null) {
            Text(
                text = medication.name,
                // 8 = el padding de 4 dp a cada lado de la celda
                fontSize = fittedNameSp(medication.name, size.textSp, cellWidthDp - 8, 1, fontScale).sp,
                fontWeight = FontWeight.Medium,
                color = readableOn(fill),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WidgetCustomizationCard(
    color: QuickTakeWidgetColor,
    size: QuickTakeWidgetSize,
    shape: QuickTakeWidgetShape,
    slots: List<Medication?>,
    onColorChange: (QuickTakeWidgetColor) -> Unit,
    onSizeChange: (QuickTakeWidgetSize) -> Unit,
    onShapeChange: (QuickTakeWidgetShape) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Widget de toma rápida",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        WidgetPreview(color = color, size = size, shape = shape, slots = slots)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Color",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTakeWidgetColor.entries.forEach { theme ->
                    val isSelected = theme == color
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(theme.accentArgb), CircleShape)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier.border(1.dp, Color.Transparent, CircleShape)
                            )
                            .clickable { onColorChange(theme) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Tamaño",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTakeWidgetSize.entries.forEach { preset ->
                    FilterChip(
                        selected = preset == size,
                        onClick = { onSizeChange(preset) },
                        label = { Text(stringResource(preset.titleRes)) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Forma",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Letra algo menor y una sola línea para que quepan los tres; con la letra del sistema
            // muy ampliada la fila se desliza en vez de partir "Cuadrada" en dos.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickTakeWidgetShape.entries.forEach { s ->
                    FilterChip(
                        selected = s == shape,
                        onClick = { onShapeChange(s) },
                        label = {
                            Text(stringResource(s.titleRes), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AboutDialog(onDismiss: () -> Unit) {
    // La app no tiene permiso de internet: los enlaces se abren en el navegador del móvil.
    val uriHandler = LocalUriHandler.current
    val open = { url: String -> runCatching { uriHandler.openUri(url) }; Unit }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acerca de PillMate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Creada por D4vRAM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Software libre (licencia GPL v3 o posterior). Sin anuncios, sin cuentas y sin internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { open("https://github.com/D4vRAM369/PillMate") },
                        label = { Text("GitHub") },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { open("https://www.buymeacoffee.com/D4vRAM369") },
                        label = { Text("Buy Me a Coffee") },
                        leadingIcon = { Icon(Icons.Default.Coffee, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
    checked: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit = {}
) {
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
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
    val mimeType = when (file.extension.lowercase()) {
        "csv" -> "text/csv"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
    val label = when (file.extension.lowercase()) {
        "csv" -> "Compartir CSV"
        "pdf" -> "Compartir PDF"
        "zip" -> "Compartir backup"
        else -> "Compartir archivo"
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, label))
}

/**
 * Abre la carpeta de auto-backups configurada con SAF
 * Si no hay carpeta configurada, muestra mensaje al usuario
 */
private fun openBackupsFolder(
    context: Context,
    scope: CoroutineScope,
    backupSafManager: BackupSafManager
) {
    scope.launch {
        try {
            // 1. Obtener URI de la carpeta configurada
            val folderUriString = backupSafManager.autoBackupFolderUri.first()
            
            // Validar que no sea null o vacío
            if (folderUriString.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "⚠️ Configura primero una carpeta de auto-backup",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            // 2. Parsear URI con validación
            val folderUri = try {
                Uri.parse(folderUriString)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "❌ URI guardado inválido. Reconfigura la carpeta de auto-backup.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            // Verificar que el URI no esté vacío
            if (folderUri.toString().isEmpty()) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "❌ URI vacío. Reconfigura la carpeta de auto-backup.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            // 3. Crear Intent para abrir la carpeta
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 4. Intentar abrir carpeta
            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "❌ No se pudo abrir la carpeta. Intenta reconfigurarla.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "❌ Error: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
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
private fun PinVerificationDialog(
    onDismiss: () -> Unit,
    onPinVerified: () -> Unit,
    securityManager: SecurityManager
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verificar PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ingresa el PIN de la app para continuar")
                
                OutlinedTextField(
                    value = pin,
                    onValueChange = { 
                        pin = it
                        errorMessage = null
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )
                
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        if (securityManager.verifyPin(pin)) {
                            onPinVerified()
                        } else {
                            errorMessage = "PIN incorrecto"
                        }
                    }
                }
            ) {
                Text("Verificar")
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
private fun VoiceNoteResetDialog(
    onDismiss: () -> Unit,
    onConfirm: (newPassword: String, newHint: String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newHint by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restablecer Contraseña de Notas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "⚠️ Esto creará una nueva contraseña. Las notas antiguas NO se podrán recuperar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { 
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("Nueva contraseña") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Ocultar" else "Mostrar"
                            )
                        }
                    },
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { 
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirmar contraseña") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newHint,
                    onValueChange = { newHint = it },
                    label = { Text("Pista (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        newPassword.isBlank() -> errorMessage = "La contraseña no puede estar vacía"
                        newPassword != confirmPassword -> errorMessage = "Las contraseñas no coinciden"
                        else -> onConfirm(newPassword, newHint)
                    }
                }
            ) {
                Text("Restablecer")
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
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar")
                }
            }
        }
    )
}

@Composable
private fun ConfirmRestoreDialog(
    restore: PendingRestore,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(restore.title) },
        text = { Text(restore.description) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Reemplazar datos")
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
private fun AutoBackupIntervalDialog(
    selectedHours: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(
        6 to "Cada 6 horas",
        12 to "Cada 12 horas",
        24 to "Cada día",
        72 to "Cada 3 días",
        168 to "Cada semana"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_autobackup_frequency)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (hours, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(hours) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedHours == hours,
                            onClick = { onSelect(hours) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
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
    val isValid = passwordsMatch && password.length >= 12

    if (!showPasswordInput) {
        // Primer paso: elegir si cifrar o no
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("📦 Exportar Backup Completo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Elige cómo exportar tu backup completo (medicamentos, historial, fotos y audios):",
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
                                text = "El cifrado protege tus datos con AES-256. Recomendado si vas a guardar o compartir el backup fuera de tu teléfono.",
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
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancelar")
                    }
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
                        label = { Text("Contraseña (mín. 12 caracteres)") },
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

                    if (password.isNotBlank() && password.length < 12) {
                        Text(
                            text = "La contraseña debe tener al menos 12 caracteres",
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
        // 1. Añadir CSV compatible con el restaurador general
        val csvFile = createExportFile(context, repository)
        zipOut.putNextEntry(java.util.zip.ZipEntry("backup.csv"))
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

            val tempAudioDir = File(context.cacheDir, "history_audio_export_$timestamp").apply { mkdirs() }
            audioNoteManager.exportEncryptionSecret(tempAudioDir)
            File(tempAudioDir, "install_secret").let { secretFile ->
                if (secretFile.exists()) {
                    zipOut.putNextEntry(java.util.zip.ZipEntry("audio_notes/${secretFile.name}"))
                    secretFile.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
            audioEntries.forEach { entry ->
                entry.audioNoteUri?.let { audioId ->
                    if (audioNoteManager.exportEncryptedAudio(audioId, tempAudioDir)) {
                        listOf("$audioId.enc", "$audioId.salt", "$audioId.iv").forEach { fileName ->
                            val file = File(tempAudioDir, fileName)
                            zipOut.putNextEntry(java.util.zip.ZipEntry("audio_notes/$fileName"))
                            file.inputStream().use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
            tempAudioDir.deleteRecursively()
        }

        // 3. Añadir archivo README con instrucciones
        val readme = """
            PillMate Export Package
            =======================

            Contenido:
            - backup.csv: Datos completos en formato PillMate CSV
            - audio_notes/: Notas de voz encriptadas (AES-256-GCM)

            Formato de audios:
            - *.enc: Archivo de audio encriptado
            - *.salt: Salt para derivación de clave
            - *.iv: Vector de inicialización

            Exportado el: ${Date(timestamp)}
        """.trimIndent()

        zipOut.putNextEntry(java.util.zip.ZipEntry("README.txt"))
        zipOut.write(readme.toByteArray())
        zipOut.closeEntry()
    }

    return@withContext zipFile
}
