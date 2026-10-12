package com.d4vram.pillmate2.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.navigation.NavController
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.model.SortMode
import com.d4vram.pillmate2.ui.components.MedicationListItem
import com.d4vram.pillmate2.ui.components.ReorderableMedicationGrid
import com.d4vram.pillmate2.ui.components.StatisticsEntryCard
import com.d4vram.pillmate2.ui.quicktake.QuickTapPulseBar
import com.d4vram.pillmate2.ui.theme.ChipCountPeriod
import com.d4vram.pillmate2.ui.theme.ThemeManager
import com.d4vram.pillmate2.widget.QuickTakeWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
                error = "No se pudo guardar la toma. Inténtalo de nuevo."
                return@launch
            }
            adjusting = null
            busy = false
            launch { refreshWidget() }
            if (snackbar.showSnackbar("Toma registrada", "Deshacer", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                try {
                    if (repository.undoMedicationTaken(entryId)) {
                        launch { refreshWidget() }
                        snackbar.showSnackbar("Toma deshecha")
                    } else snackbar.showSnackbar("La toma ya no existe")
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = "No se pudo deshacer la toma. Inténtalo desde Historial." }
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
                            Text("Tu día, a tu ritmo", style = MaterialTheme.typography.headlineMedium)
                            Text(LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.getDefault())), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { search = !search; if (!search) query = "" }) { Icon(Icons.Default.Search, "Buscar") }
                        FilledIconButton(onClick = onAddMedication) { Icon(Icons.Default.Add, "Agregar medicamento") }
                    }
                    if (search) OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("Buscar medicamento…") }, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { IconButton(onClick = { search = false; query = "" }) { Icon(Icons.Default.Close, "Cerrar búsqueda") } })
                }
            }
            item {
                StatisticsEntryCard(
                    onClick = { navController.navigate("global_stats") },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            item {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium)
                    QuickTapPulseBar(slots, busy, { record(it.id, 1, null) }, { error = null; adjusting = it }, { configuring = it },
                        lastTakes = history.groupBy { it.medicationId }.mapValues { (_, entries) -> entries.maxOf { it.timestamp } })
                }
            }
            item {
                val latest = history.maxByOrNull { it.timestamp }
                Card(Modifier.padding(horizontal = 24.dp).fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Última toma", style = MaterialTheme.typography.titleMedium)
                        Text(latest?.let { "${it.medicationName} · ${it.quantity} unidad(es)" } ?: "Todavía no hay tomas registradas")
                        latest?.let { Text(it.timestamp.format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm"))) }
                        TextButton(onClick = { navController.navigate("history") }) { Text("Ver historial") }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { navController.navigate("global_stats") }) { Text("Estadísticas") }
                    TextButton(onClick = { navController.navigate("medications") }) { Text("Medicamentos") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mis medicamentos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { themeManager.setListView(!listView) } }) { Text(if (listView) "Cuadrícula" else "Lista") }
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
