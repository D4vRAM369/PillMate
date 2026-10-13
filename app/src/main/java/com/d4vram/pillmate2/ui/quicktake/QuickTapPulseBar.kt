package com.d4vram.pillmate2.ui.quicktake

import com.d4vram.pillmate2.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.model.Medication
import java.time.LocalDateTime

/** Four user-configured slots, compact horizontal bar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QuickTapPulseBar(
    slots: List<Medication?>,
    busy: Boolean,
    onQuickTake: (Medication) -> Unit,
    onAdjust: (Medication) -> Unit,
    onConfigure: (Int) -> Unit,
    lastTakes: Map<String, LocalDateTime> = emptyMap()
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(4) { index ->
            val medication = slots.getOrNull(index)
            // semantics { } no es @Composable: la descripción se pide antes.
            val description = if (medication != null) stringResource(R.string.quick_take_for, medication.name)
            else stringResource(R.string.quick_take_configure_quick, index + 1)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 1.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            contentDescription = description
                        }
                        .combinedClickable(
                            enabled = !busy,
                            onClickLabel = stringResource(if (medication != null) R.string.quick_take_log_label else R.string.quick_take_configure_label),
                            onLongClickLabel = if (medication != null) stringResource(R.string.quick_take_adjust_label) else null,
                            onClick = {
                                if (medication != null) {
                                    onQuickTake(medication)
                                } else {
                                    onConfigure(index)
                                }
                            },
                            onLongClick = {
                                if (medication != null) {
                                    onAdjust(medication)
                                } else {
                                    onConfigure(index)
                                }
                            }
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (medication != null) {
                            Text(
                                text = medication.emoji,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = medication.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.quick_take_slot_configure, index + 1),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
