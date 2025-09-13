package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * ⚙️ Pantalla de configuración
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
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

            item {
                SettingsSection(title = "🔔 Notificaciones") {
                    SettingsItem(
                        title = "Recordatorios",
                        description = "Activar recordatorios automáticos",
                        hasSwitch = true
                    )
                    SettingsItem(
                        title = "Sonido",
                        description = "Configurar tono de notificación"
                    )
                }
            }

            item {
                SettingsSection(title = "📊 Datos") {
                    SettingsItem(
                        title = "Exportar datos",
                        description = "Crear copia de seguridad"
                    )
                    SettingsItem(
                        title = "Importar datos",
                        description = "Restaurar desde copia"
                    )
                }
            }

            item {
                SettingsSection(title = "ℹ️ Información") {
                    SettingsItem(
                        title = "Versión",
                        description = "1.0.0"
                    )
                    SettingsItem(
                        title = "Acerca de",
                        description = "Información de la app"
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
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
    onClick: () -> Unit = {}
) {
    var switchState by remember { mutableStateOf(false) }

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