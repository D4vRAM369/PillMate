package com.d4vram.pillmate2.ui.quicktake

import com.d4vram.pillmate2.R
import androidx.compose.ui.res.stringResource
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.glance.appwidget.updateAll
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.data.local.PillMateDatabase
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.notifications.NotificationHelper
import com.d4vram.pillmate2.notifications.ToastNotifier
import com.d4vram.pillmate2.ui.theme.PillMateTheme
import com.d4vram.pillmate2.widget.QuickTakeWidget
import kotlinx.coroutines.launch

/**
 * Activity transparente que se lanza desde la notificación persistente de toma
 * rápida. Muestra un bottom-sheet con los medicamentos más tomados; al elegir
 * uno se registra la toma sin abrir la app completa.
 *
 * Tema translúcido (ver Manifest): el sheet flota sobre lo que hubiera detrás.
 */
class QuickTakeSheetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = run {
            val database = PillMateDatabase.getInstance(applicationContext)
            MedicationRepository(
                database = database,
                medicationDao = database.medicationDao(),
                historyDao = database.historyDao()
            )
        }

        setContent {
            PillMateTheme {
                QuickTakeSheet(
                    repository = repository,
                    onTake = { medication ->
                        repository.takeMedication(medication.id, quantity = 1, customTimestamp = null)
                        ToastNotifier.showTakeConfirmation(applicationContext, medication.name)
                        lifecycleScope.launch {
                            // Refresca el ranking de la notif y el widget tras la toma.
                            NotificationHelper.showQuickTakeNotification(applicationContext)
                            QuickTakeWidget().updateAll(applicationContext)
                        }
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTakeSheet(
    repository: MedicationRepository,
    onTake: (Medication) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val medications by repository.medications.collectAsState()
    val history by repository.historyEntries.collectAsState()
    // Top-5 por frecuencia 30d; si aún no hay tomas, mostrar los primeros 5.
    val top = remember(medications, history) {
        repository.topTakenMedications(limit = 5, withinDays = 30)
            .ifEmpty { medications.take(5) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_sheet_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.quick_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(16.dp))

            if (top.isEmpty()) {
                Text(
                    text = stringResource(R.string.quick_sheet_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = top, key = { it.id }) { medication ->
                        QuickTakeRow(medication = medication, onTake = { onTake(medication) })
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTakeRow(
    medication: Medication,
    onTake: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTake)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color = medication.color.copy(alpha = 0.14f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = medication.emoji)
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = medication.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = medication.dose,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        TextButton(onClick = onTake) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = medication.color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.quick_take_take),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = medication.color
            )
        }
    }
}
