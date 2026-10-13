package com.d4vram.pillmate2.ui.screens

import com.d4vram.pillmate2.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.d4vram.pillmate2.utils.localizedDateFormatter
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.navigation.NavController
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.model.SortMode
import com.d4vram.pillmate2.ui.components.MedicationListItem
import com.d4vram.pillmate2.ui.components.ReorderableMedicationGrid
import com.d4vram.pillmate2.ui.navigation.navigateToTab
import com.d4vram.pillmate2.ui.quicktake.QuickTapPulseBar
import com.d4vram.pillmate2.ui.timeline.RhythmClock
import com.d4vram.pillmate2.ui.timeline.dailyRhythmEntries
import com.d4vram.pillmate2.ui.theme.ChipCountPeriod
import com.d4vram.pillmate2.ui.theme.ThemeManager
import com.d4vram.pillmate2.widget.QuickTakeWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun AstraHomeScreen(
    repository: MedicationRepository,
    navController: NavController,
    themeManager: ThemeManager,
    onAddMedication: () -> Unit,
    onEditMedication: (String) -> Unit,
    onDeleteMedication: (String) -> Unit
) {
    val raw by repository.medications.collectAsState()
    val history by repository.historyEntries.collectAsState()
    val sort by themeManager.sortMode.collectAsState(initial = SortMode.MANUAL)
    val listView by themeManager.isListView.collectAsState(initial = false)
    val period by themeManager.chipCountPeriod.collectAsState(initial = ChipCountPeriod.Total)
    val ids by themeManager.quickTakeMedicationIds.collectAsState(initial = List(4) { null })
    val medications = remember(raw, sort) { repository.applySorting(raw, sort) }
    val slots = List(4) { index -> medications.firstOrNull { it.id == ids.getOrNull(index) } }
    var query by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf(false) }
    var configuring by remember { mutableStateOf<Int?>(null) }
    var adjusting by remember { mutableStateOf<Medication?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val filtered = medications.filter { it.name.contains(query, true) || it.dose.contains(query, true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    suspend fun refreshWidget() {
        try { QuickTakeWidget().updateAll(context) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { Log.w("AstraHome", "Widget refresh failed", failure) }
    }
    fun record(id: String, quantity: Int, timestamp: LocalDateTime?) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            val entryId = try {
                repository.recordMedicationTaken(id, quantity, timestamp)
                    ?: error("El medicamento ya no existe")
            } catch (cancelled: CancellationException) {
                busy = false
                throw cancelled
            } catch (failure: Exception) {
                busy = false
                error = context.getString(R.string.dose_error_save)
                return@launch
            }
            adjusting = null
            busy = false
            launch { refreshWidget() }
            if (snackbar.showSnackbar(context.getString(R.string.dose_logged), context.getString(R.string.action_undo), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                try {
                    if (repository.undoMedicationTaken(entryId)) {
                        launch { refreshWidget() }
                        snackbar.showSnackbar(context.getString(R.string.dose_undone))
                    } else snackbar.showSnackbar(context.getString(R.string.dose_gone))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = context.getString(R.string.dose_error_undo) }
            }
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("PillMate", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.headlineMedium)
                            Text(LocalDateTime.now().format(localizedDateFormatter("EEEEdMMMM", LocalConfiguration.current.locales[0])), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { navController.navigate("global_stats") }) { Icon(Icons.Default.BarChart, stringResource(R.string.stats_title)) }
                        IconButton(onClick = { search = !search; if (!search) query = "" }) { Icon(Icons.Default.Search, stringResource(R.string.action_search)) }
                        FilledIconButton(onClick = onAddMedication) { Icon(Icons.Default.Add, stringResource(R.string.action_add_medication)) }
                    }
                    if (search) OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.home_search_placeholder)) }, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { IconButton(onClick = { search = false; query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.history_search_close)) } })
                }
            }
            item {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.home_quick_access), style = MaterialTheme.typography.titleMedium)
                    QuickTapPulseBar(slots, busy, { record(it.id, 1, null) }, { error = null; adjusting = it }, { configuring = it },
                        lastTakes = history.groupBy { it.medicationId }.mapValues { (_, entries) -> entries.maxOf { it.timestamp } })
                }
            }
            item {
                // Una sola tarjeta resume el día y lleva al Historial: sustituye a "Última toma" y a la tarjeta de Estadísticas.
                val today = remember(history) { dailyRhythmEntries(history, LocalDate.now()) }
                val latest = history.maxByOrNull { it.timestamp }
                Card(onClick = { navController.navigateToTab("history") }, modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RhythmClock(today, diameter = 132.dp, interactive = false, showNow = true)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pluralStringResource(R.plurals.home_today_doses, today.size, today.size), style = MaterialTheme.typography.titleMedium)
                            if (latest == null) Text(stringResource(R.string.home_no_doses_yet), style = MaterialTheme.typography.bodyMedium)
                            else {
                                Text(stringResource(R.string.home_last_dose, latest.medicationName), style = MaterialTheme.typography.bodyMedium)
                                Text(latest.timestamp.format(DateTimeFormatter.ofPattern(if (latest.timestamp.toLocalDate() == LocalDate.now()) "HH:mm" else "dd/MM · HH:mm")),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(stringResource(R.string.home_view_history), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.home_my_medications), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { themeManager.setListView(!listView) } }) { Text(stringResource(if (listView) R.string.home_view_grid else R.string.home_view_list)) }
                }
            }
            if (filtered.isEmpty()) {
                item { EmptyMedicationState(query.isNotBlank(), { if (query.isNotBlank()) query = "" else onAddMedication() }, Modifier.padding(horizontal = 24.dp)) }
            } else if (listView) {
                items(filtered, key = { it.id }) { medication ->
                    MedicationListItem(medication, repository, period, ::record, onEditMedication, onDeleteMedication, Modifier.padding(horizontal = 24.dp), busy = busy)
                }
            } else {
                item {
                    ReorderableMedicationGrid(filtered, repository, period, ::record, onEditMedication, onDeleteMedication, onAddMedication,
                        { from, to -> if (sort == SortMode.MANUAL && query.isBlank()) repository.reorderMedications(from, to) },
                        dragEnabled = sort == SortMode.MANUAL && query.isBlank(), parentListState = listState, modifier = Modifier.padding(horizontal = 24.dp), busy = busy)
                }
            }
        }
    }
    configuring?.let { slot ->
        QuickTakeSlotPickerDialog(slot + 1, medications, slots[slot]?.id, { id ->
            scope.launch { themeManager.setQuickTakeMedication(slot, id); refreshWidget() }; configuring = null
        }, { scope.launch { themeManager.setQuickTakeMedication(slot, null); refreshWidget() }; configuring = null }, { configuring = null })
    }
    adjusting?.let { medication -> AstraDoseSheet(medication, busy, { adjusting = null }, { quantity, time -> record(medication.id, quantity, time) }, error) }
}
