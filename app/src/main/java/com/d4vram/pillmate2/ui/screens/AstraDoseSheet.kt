package com.d4vram.pillmate2.ui.screens

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
    onConfirm: (Int, LocalDateTime?) -> Unit,
    error: String? = null,
    family: List<Medication> = emptyList(),
    onSelectMedication: (Medication) -> Unit = {}
) {
    var quantity by rememberSaveable(medication.id) { mutableIntStateOf(1) }
    var selectedTime by rememberSaveable(medication.id) { mutableStateOf<String?>(null) }
    val timestamp = selectedTime?.let(LocalDateTime::parse)
    val context = LocalContext.current
    val future = timestamp?.isAfter(LocalDateTime.now()) == true
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Registrar toma", style = MaterialTheme.typography.headlineSmall)
            Text("${medication.emoji} ${medication.name} · ${medication.dose}", style = MaterialTheme.typography.titleMedium)
            if (family.size > 1) {
                Text("Dosis disponibles")
                family.forEach { variant ->
                    FilterChip(selected = variant.id == medication.id, enabled = !busy,
                        onClick = { onSelectMedication(variant) }, label = { Text(variant.dose) })
                }
            }
            Text("Cantidad de unidades")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = { quantity-- }, enabled = !busy && quantity > 1, modifier = Modifier.size(48.dp).semantics { contentDescription = "Reducir cantidad" }) { Text("−") }
                Text("$quantity", style = MaterialTheme.typography.displaySmall)
                FilledIconButton(onClick = { quantity++ }, enabled = !busy && quantity < 999, modifier = Modifier.size(48.dp).semantics { contentDescription = "Aumentar cantidad" }) { Text("+") }
            }
            Text(timestamp?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")) ?: "Ahora", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = { selectedTime = null }) { Text("Ahora") }
                TextButton(enabled = !busy, onClick = {
                    val initial = timestamp ?: LocalDateTime.now()
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            selectedTime = LocalDateTime.of(year, month + 1, day, hour, minute).toString()
                        }, initial.hour, initial.minute, true).show()
                    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }) { Text("Cambiar fecha y hora") }
            }
            if (future) Text("Selecciona una hora que ya haya pasado.", color = MaterialTheme.colorScheme.error)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onConfirm(quantity, timestamp) }, enabled = !busy && !future, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Guardando…" else "Registrar $quantity ${if (quantity == 1) "unidad" else "unidades"}")
            }
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
    }
}
