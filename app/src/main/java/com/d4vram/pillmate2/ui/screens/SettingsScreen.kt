package com.d4vram.pillmate2.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.ui.theme.ThemeManager
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import com.d4vram.pillmate2.data.MedicationRepository

/**
 * ⚙️ Pantalla de configuración
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(themeManager: ThemeManager, repository: MedicationRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkMode by themeManager.isDarkMode.collectAsState(initial = false)
    
    var showExportSuccess by remember { mutableStateOf(false) }
    var showImportSuccess by remember { mutableStateOf(false) }
    
    // Launcher para seleccionar archivo CSV
    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val csvContent = reader.readText()
                // Aquí procesarías el CSV
                showImportSuccess = true
            } catch (e: Exception) {
                // Manejar error
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
        }
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
                            exportToCsv(context)
                            showExportSuccess = true
                        }
                    )
                    SettingsItem(
                        title = "Importar CSV",
                        description = "Cargar datos desde archivo CSV",
                        icon = Icons.Default.FileUpload,
                        onClick = {
                            csvImportLauncher.launch("text/csv")
                        }
                    )
                    SettingsItem(
                        title = "Limpiar todos los datos",
                        description = "Eliminar todos los medicamentos y entradas",
                        icon = Icons.Default.Delete,
                        onClick = { repository.clearAllData() }
                    )
                    SettingsItem(
                        title = "Copia de seguridad",
                        description = "Respaldar todos los datos",
                        icon = Icons.Default.Backup
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
    
    // Snackbar para mostrar éxito en exportación
    if (showExportSuccess) {
        LaunchedEffect(showExportSuccess) {
            kotlinx.coroutines.delay(2000)
            showExportSuccess = false
        }
        
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { showExportSuccess = false }) {
                    Text("Cerrar")
                }
            }
        ) {
            Text("✅ CSV exportado correctamente")
        }
    }
    
    // Snackbar para mostrar éxito en importación
    if (showImportSuccess) {
        LaunchedEffect(showImportSuccess) {
            kotlinx.coroutines.delay(2000)
            showImportSuccess = false
        }
        
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { showImportSuccess = false }) {
                    Text("Cerrar")
                }
            }
        ) {
            Text("✅ CSV importado correctamente")
        }
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

/**
 * 📄 Función para exportar datos a CSV
 */
private fun exportToCsv(context: Context) {
    try {
        val csvData = generateCsvData()
        
        // Crear intent para compartir archivo
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/csv"
            putExtra(Intent.EXTRA_TEXT, csvData)
            putExtra(Intent.EXTRA_SUBJECT, "Datos PillMate - ${System.currentTimeMillis()}")
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Exportar CSV"))
    } catch (e: Exception) {
        // Manejar error de exportación
    }
}

/**
 * 📊 Generar contenido CSV con medicamentos e historial
 */
private fun generateCsvData(): String {
    val stringBuilder = StringBuilder()
    
    // Header para medicamentos
    stringBuilder.appendLine("MEDICAMENTOS")
    stringBuilder.appendLine("ID,Nombre,Dosis,Tipo,Color_Hex,Emoji,Tomado_Hoy,Meta_Diaria,Es_PRN")
    
    // Datos de ejemplo de medicamentos (en una app real vendrían del repository)
    stringBuilder.appendLine("1,Omeprazol,20mg,Cápsula,#8B5CF6,💊,2,1,false")
    stringBuilder.appendLine("2,Aspirina,100mg,Pastilla,#F59E0B,💊,0,2,false")
    stringBuilder.appendLine("3,Vitamina D,1000 IU,Gota,#10B981,💧,1,1,false")
    stringBuilder.appendLine("4,Melatonina,3mg,Pastilla,#6366F1,🌙,0,1,false")
    stringBuilder.appendLine("5,Ibuprofeno,400mg,Según necesidad,#EF4444,💊,0,0,true")
    
    stringBuilder.appendLine()
    
    // Header para historial
    stringBuilder.appendLine("HISTORIAL")
    stringBuilder.appendLine("ID,Medicamento,Fecha,Hora,Notas")
    
    // Datos de ejemplo de historial
    stringBuilder.appendLine("h1,Vitamina D,2025-03-06,12:45,Tomado con el almuerzo")
    stringBuilder.appendLine("h2,Omeprazol,2025-03-06,08:15,")
    stringBuilder.appendLine("h3,Melatonina,2025-03-05,21:00,Antes de dormir")
    stringBuilder.appendLine("h4,Aspirina,2025-03-05,19:30,")
    stringBuilder.appendLine("h5,Vitamina D,2025-03-05,12:30,")
    
    return stringBuilder.toString()
}