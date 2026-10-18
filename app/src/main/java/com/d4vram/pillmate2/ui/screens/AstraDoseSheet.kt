package com.d4vram.pillmate2.ui.screens

import com.d4vram.pillmate2.utils.formatQuantity
import com.d4vram.pillmate2.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.model.Medication
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AstraDoseSheet(
    medication: Medication,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, LocalDateTime?) -> Unit,
    error: String? = null,
    family: List<Medication> = emptyList(),
    onSelectMedication: (Medication) -> Unit = {}
) {
    var quantity by rememberSaveable(medication.id) { mutableDoubleStateOf(1.0) }
    var selectedTime by rememberSaveable(medication.id) { mutableStateOf<String?>(null) }
    val timestamp = selectedTime?.let(LocalDateTime::parse)
    val context = LocalContext.current
    val future = timestamp?.isAfter(LocalDateTime.now()) == true
    // semantics { } no es composable: las descripciones se resuelven antes.
    val decreaseLabel = stringResource(R.string.dose_sheet_decrease)
    val increaseLabel = stringResource(R.string.dose_sheet_increase)
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.dose_sheet_title), style = MaterialTheme.typography.headlineSmall)
            Text("${medication.emoji} ${medication.name} · ${medication.dose}", style = MaterialTheme.typography.titleMedium)
            if (family.size > 1) {
                Text(stringResource(R.string.dose_sheet_doses))
                family.forEach { variant ->
                    FilterChip(selected = variant.id == medication.id, enabled = !busy,
                        onClick = { onSelectMedication(variant) }, label = { Text(variant.dose) })
                }
            }
            Text(stringResource(R.string.dose_sheet_units))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = { quantity = (quantity - 0.25).coerceAtLeast(0.25) }, enabled = !busy && quantity > 0.25, modifier = Modifier.size(48.dp).semantics { contentDescription = decreaseLabel }) { Text("−") }
                Text(formatQuantity(quantity), style = MaterialTheme.typography.displaySmall)
                FilledIconButton(onClick = { quantity = (quantity + 0.25).coerceAtMost(999.0) }, enabled = !busy && quantity < 999, modifier = Modifier.size(48.dp).semantics { contentDescription = increaseLabel }) { Text("+") }
            }
            Text(timestamp?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")) ?: stringResource(R.string.dose_now), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = { selectedTime = null }) { Text(stringResource(R.string.dose_now)) }
                TextButton(enabled = !busy, onClick = {
                    val initial = timestamp ?: LocalDateTime.now()
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            selectedTime = LocalDateTime.of(year, month + 1, day, hour, minute).toString()
                        }, initial.hour, initial.minute, true).show()
                    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }) { Text(stringResource(R.string.dose_change_datetime)) }
            }
            if (future) Text(stringResource(R.string.dose_future_error), color = MaterialTheme.colorScheme.error)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onConfirm(quantity, timestamp) }, enabled = !busy && !future, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) stringResource(R.string.dose_saving) else pluralStringResource(R.plurals.dose_log_units, if (quantity == 1.0) 1 else 2, formatQuantity(quantity)))
            }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
