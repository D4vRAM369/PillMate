package com.d4vram.pillmate2.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.data.ai.StoredLocalModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelBottomSheet(
    models: List<StoredLocalModel>,
    activeModelPath: String?,
    candidatePaths: List<String>,
    onDismiss: () -> Unit,
    onImportModel: () -> Unit,
    onDownloadModel: () -> Unit,
    onActivateModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Modelos locales",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onImportModel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Importar .task", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = onDownloadModel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Descargar URL", style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()

            if (models.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Sin modelos instalados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (candidatePaths.isNotEmpty()) {
                        Text(
                            text = "Copia un archivo .task a una de estas rutas:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        candidatePaths.forEach { path ->
                            Text(
                                text = path,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(models, key = { it.id }) { model ->
                        ModelRow(
                            model = model,
                            isActive = model.localPath == activeModelPath,
                            onActivate = { onActivateModel(model.id) },
                            onDelete = { onDeleteModel(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: StoredLocalModel,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    val sizeLabel = when {
        model.sizeBytes > 1_000_000_000L -> "${"%.1f".format(model.sizeBytes / 1_000_000_000.0)} GB"
        model.sizeBytes > 1_000_000L -> "${"%.0f".format(model.sizeBytes / 1_000_000.0)} MB"
        else -> "< 1 MB"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isActive) "● Activo · $sizeLabel" else sizeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) Color(0xFF22C55E)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isActive) {
                IconButton(onClick = onActivate) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Activar",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
