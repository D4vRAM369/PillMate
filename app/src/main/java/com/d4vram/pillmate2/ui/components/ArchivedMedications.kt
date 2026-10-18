package com.d4vram.pillmate2.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.notifications.ReminderScheduler
import com.d4vram.pillmate2.ui.theme.ThemeManager
import com.d4vram.pillmate2.widget.QuickTakeWidget
import kotlinx.coroutines.launch

/**
 * 📦 Archiva o desarchiva un medicamento.
 * No borra nada: el historial y las estadísticas lo siguen viendo. Solo deja de
 * salir en Inicio, widget, Toma rápida y recordatorios.
 */
suspend fun setMedicationArchived(
    context: Context,
    repository: MedicationRepository,
    medication: Medication,
    archived: Boolean
) {
    repository.setArchived(medication.id, archived)
    if (archived) {
        ReminderScheduler.cancelReminder(context, medication.id)
    } else if (medication.reminderEnabled && !medication.scheduleTime.isNullOrBlank()) {
        ReminderScheduler.scheduleReminder(context, medication)
    }
    // Después de escribir en la base: el widget relee y vacía/rellena la casilla.
    runCatching { QuickTakeWidget().updateAll(context.applicationContext) }
    val message = if (archived) R.string.archived_toast_archived else R.string.archived_toast_restored
    Toast.makeText(context, context.getString(message, medication.name), Toast.LENGTH_SHORT).show()
}

/** Hoja inferior con los medicamentos archivados y su botón Desarchivar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedMedicationsSheet(
    archived: List<Medication>,
    repository: MedicationRepository,
    themeManager: ThemeManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inHistory by themeManager.archivedInHistory.collectAsState(initial = true)
    val badge by themeManager.archivedBadge.collectAsState(initial = true)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.archived_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = stringResource(R.string.archived_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            ArchivedOption(stringResource(R.string.archived_option_history), inHistory) {
                scope.launch { themeManager.setArchivedInHistory(it) }
            }
            ArchivedOption(stringResource(R.string.archived_option_badge), badge) {
                scope.launch { themeManager.setArchivedBadge(it) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            if (archived.isEmpty()) {
                Text(
                    text = stringResource(R.string.archived_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(archived, key = { it.id }) { medication ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            PillIcon(
                                emoji = medication.emoji,
                                color = medication.color,
                                isTaken = false,
                                photoPath = medication.photoPath,
                                usePhotoInstead = medication.usePhotoInstead
                            )
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    text = medication.name,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (medication.lastTaken != null || medication.lastTakenTimestamp != null) {
                                    Text(
                                        // lastTakenLabel usa el idioma del móvil; medication.lastTaken va siempre en español
                                        text = stringResource(R.string.archived_last_taken, lastTakenLabel(medication)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(onClick = {
                                scope.launch { setMedicationArchived(context, repository, medication, archived = false) }
                            }) {
                                Icon(Icons.Outlined.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_unarchive))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
