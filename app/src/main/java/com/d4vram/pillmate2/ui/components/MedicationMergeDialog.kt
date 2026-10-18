package com.d4vram.pillmate2.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.data.mergeFactorPreservesStrength
import com.d4vram.pillmate2.utils.formatQuantity
import com.d4vram.pillmate2.data.MedicationMerge
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import com.d4vram.pillmate2.widget.QuickTakeWidget

@Composable
fun MedicationMergeDialog(repository: MedicationRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var medications by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var history by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    var sourceId by remember { mutableStateOf<String?>(null) }
    var destinationId by remember { mutableStateOf<String?>(null) }
    var factorText by remember { mutableStateOf(formatQuantity(0.5)) }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(repository) {
        medications = repository.getMedicationsSnapshot()
        history = repository.getHistorySnapshot()
    }
    val source = medications.find { it.id == sourceId }
    val destination = medications.find { it.id == destinationId }
    val factor = factorText.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val linked = source != null && (source.parentMedicationId != null || medications.any { it.parentMedicationId == source.id })
    val factorMatches = source == null || destination == null || factor == null || mergeFactorPreservesStrength(source.dose, destination.dose, factor)
    val unresolved = source != null && history.any { entry -> medications.none { it.id == entry.medicationId } && entry.medicationName.equals(source.name, ignoreCase = true) }
    val sourceCount = history.count { it.medicationId == sourceId }
    val destinationCount = history.count { it.medicationId == destinationId }
    val eligible = source != null && destination != null && source.id != destination.id && source.type == destination.type && !linked && !unresolved && factorMatches && factor != null
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.merge_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.merge_explanation))
                if (!done) {
                    MergeMedicationPicker(stringResource(R.string.merge_source), medications, sourceId, !busy) { sourceId = it; confirmed = false }
                    MergeMedicationPicker(stringResource(R.string.merge_destination), medications.filter { it.id != sourceId }, destinationId, !busy) { destinationId = it; confirmed = false }
                    OutlinedTextField(value = factorText, onValueChange = { factorText = it; confirmed = false }, enabled = !busy,
                        label = { Text(stringResource(R.string.merge_factor)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (eligible) {
                        Text(stringResource(R.string.merge_example, source!!.name, source.dose, destination!!.name, destination.dose, formatQuantity(factor!!)))
                        Text(stringResource(R.string.merge_preview, sourceCount, destinationCount, sourceCount + destinationCount, history.size))
                    }
                    if (!factorMatches) Text(stringResource(R.string.merge_factor_mismatch), color = MaterialTheme.colorScheme.error)
                    if (unresolved) Text(stringResource(R.string.merge_unresolved), color = MaterialTheme.colorScheme.error)
                    if (linked) Text(stringResource(R.string.merge_linked))
                    if (source != null && destination != null && source.type != destination.type) Text(stringResource(R.string.merge_type_mismatch, source.type, destination.type))
                    Row {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it }, enabled = !busy)
                        Text(stringResource(R.string.merge_confirmation))
                    }
                }
                result?.let { Text(it) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (!done) TextButton(enabled = eligible && confirmed && !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val backup = MedicationMerge(context.applicationContext, repository).merge(sourceId!!, destinationId!!, factor!!)
                        result = context.getString(R.string.merge_success, backup.name)
                        done = true
                        runCatching { QuickTakeWidget().updateAll(context) }
                    } catch (_: Exception) {
                        result = context.getString(R.string.merge_failed)
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.merge_action)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(if (done) R.string.action_close else R.string.action_cancel)) } }
    )
}

@Composable
private fun MergeMedicationPicker(label: String, medications: List<Medication>, selectedId: String?, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = medications.find { it.id == selectedId }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(selected?.let { "${it.name} · ${it.dose}" } ?: stringResource(R.string.merge_choose)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                medications.forEach { medication ->
                    DropdownMenuItem(text = { Text("${medication.name} · ${medication.dose}") }, onClick = { onSelect(medication.id); expanded = false })
                }
            }
        }
    }
}
