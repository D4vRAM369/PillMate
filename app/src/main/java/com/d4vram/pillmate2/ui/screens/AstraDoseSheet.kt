package com.d4vram.pillmate2.ui.screens

import com.d4vram.pillmate2.utils.formatQuantity
import com.d4vram.pillmate2.utils.parseQuantity
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
    // El texto del campo es la única fuente de verdad; la cantidad se deriva de él (null = no válida).
    var quantityText by rememberSaveable(medication.id) { mutableStateOf(formatQuantity(1.0)) }
    val quantity = parseQuantity(quantityText)
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
                FilledIconButton(onClick = { quantityText = stepQuantity(quantity, -0.25) }, enabled = !busy && (quantity ?: 0.0) > 0.25, modifier = Modifier.size(48.dp).semantics { contentDescription = decreaseLabel }) { Text("−") }
                DoseQuantityField(quantityText, { quantityText = it }, enabled = !busy, textStyle = MaterialTheme.typography.headlineMedium)
                FilledIconButton(onClick = { quantityText = stepQuantity(quantity, 0.25) }, enabled = !busy && (quantity ?: 0.0) < 999, modifier = Modifier.size(48.dp).semantics { contentDescription = increaseLabel }) { Text("+") }
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
            Button(onClick = { quantity?.let { onConfirm(it, timestamp) } }, enabled = !busy && !future && quantity != null, modifier = Modifier.fillMaxWidth()) {
                Text(when {
                    busy -> stringResource(R.string.dose_saving)
                    quantity == null -> stringResource(R.string.dose_quantity_invalid)
                    else -> pluralStringResource(R.plurals.dose_log_units, if (quantity == 1.0) 1 else 2, formatQuantity(quantity))
                })
            }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

/** ± button: steps from the typed amount (or 1 if the field is empty/invalid), clamped to 0,25…999. */
internal fun stepQuantity(current: Double?, delta: Double): String =
    formatQuantity(((current ?: 1.0) + delta).coerceIn(0.25, 999.0))

/**
 * Editable quantity between the − and + buttons, so 8 drops are typed instead of tapping + 28 times.
 * Shared by the Astra sheet and the classic TakeMedicationDialog.
 */
@Composable
internal fun DoseQuantityField(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.dose_sheet_units)
    OutlinedTextField(
        value = text,
        // Solo dígitos y un separador; el resto ni se escribe. Máx. 6 caracteres ("999,25").
        onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() || it == ',' || it == '.' }) onTextChange(new) },
        enabled = enabled,
        singleLine = true,
        isError = parseQuantity(text) == null,
        textStyle = textStyle.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.width(120.dp).semantics { contentDescription = label }
    )
}
