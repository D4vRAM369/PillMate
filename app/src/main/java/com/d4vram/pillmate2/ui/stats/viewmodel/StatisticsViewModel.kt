// kotlin
package com.d4vram.pillmate2.ui.stats.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.data.MedicationRepository

import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.ui.stats.ActivityDataPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Enums para los filtros de tiempo disponibles
enum class FilterRange {
    WEEK,
    DAYS_15,
    DAYS_30,
    ALL_TIME
}

data class StatisticsUiState(
    val activityData: List<ActivityDataPoint> = emptyList(),
    val activeFilter: FilterRange = FilterRange.WEEK,
    val rangeStart: LocalDate? = null, // null: todo el tiempo
    val rangeEnd: LocalDate = LocalDate.now(),
    val recentHistory: List<com.d4vram.pillmate2.model.HistoryEntry> = emptyList(),
    // Todas las tomas del periodo y filtro activos (alimenta el reloj de 24 h y los insights)
    val periodEntries: List<com.d4vram.pillmate2.model.HistoryEntry> = emptyList(),
    val availableMedications: List<com.d4vram.pillmate2.model.Medication> = emptyList(),
    val selectedMedicationId: String? = null,
    val selectedMedicationColor: androidx.compose.ui.graphics.Color? = null,

    // Métricas principales
    val totalDosesLogged: Int = 0,
    val averageDailyDoses: String = "0",
    val daysSinceLastRecord: Long? = null, // null: sin registros en el periodo

    // Unidades dinámicas según tipo de medicamento
    val displayUnit: String = "tomas",
    val totalInUnit: String = "0",
    val averageInUnit: String = "0",

    // Desglose por variantes de dosis (para familias de medicamentos)
    val doseBreakdown: List<DoseBreakdownItem> = emptyList(),
    val showingFamilyStats: Boolean = false, // true si se está mostrando una familia
    val familyName: String = "", // Nombre de la familia (ej: "Ibuprofeno 400/600")

    val trend: Trend? = null, // null: no hay periodo anterior con el que comparar

    // --- Daily Details Selection ---
    val selectedDate: LocalDate? = null,
    val selectedDateHistory: List<HistoryEntryWithUnit> = emptyList()
)

data class HistoryEntryWithUnit(
    val entry: com.d4vram.pillmate2.model.HistoryEntry,
    val displayValue: String,
    val displayUnit: String,
    val medicationColor: androidx.compose.ui.graphics.Color? = null
)

/**
 * Desglose de dosis por variante de medicamento (para familias)
 */
data class DoseBreakdownItem(
    val medicationName: String,
    val dose: String,
    val color: androidx.compose.ui.graphics.Color,
    val totalValue: Float,
    val displayUnit: String,
    val percentage: Float // 0-1 para la barra de progreso
)


class StatisticsViewModel(
    private val medicationRepository: MedicationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val _filter = MutableStateFlow(FilterRange.WEEK)
    private var selectedMedicationId: String? = null

    // Fecha fin del rango (normalmente hoy, pero permite navegar al pasado)
    private var currentEndDate: LocalDate = LocalDate.now()

    init {
        // Recargar estadísticas cuando cambia el filtro
        _filter.onEach { range ->
            // Al cambiar filtro, reseteamos a hoy para no perdernos en el pasado
            currentEndDate = LocalDate.now()
            loadStatistics()
        }.launchIn(viewModelScope)
    }

    // --- Métodos Públicos ---

    fun setFilter(range: FilterRange) {
        _filter.value = range
    }

    fun selectMedication(medicationId: String?) {
        selectedMedicationId = medicationId
        loadStatistics()
    }

    fun previousPeriod() {
        val range = _filter.value
        currentEndDate = when (range) {
            FilterRange.WEEK -> currentEndDate.minusWeeks(1)
            FilterRange.DAYS_15 -> currentEndDate.minusDays(15)
            FilterRange.DAYS_30 -> currentEndDate.minusDays(30)
            FilterRange.ALL_TIME -> currentEndDate 
        }
        loadStatistics()
    }

    fun nextPeriod() {
        val range = _filter.value
        val today = LocalDate.now()
        val nextDate = when (range) {
            FilterRange.WEEK -> currentEndDate.plusWeeks(1)
            FilterRange.DAYS_15 -> currentEndDate.plusDays(15)
            FilterRange.DAYS_30 -> currentEndDate.plusDays(30)
            FilterRange.ALL_TIME -> currentEndDate
        }

        // No permitir navegar al futuro
        if (nextDate.isAfter(today)) {
            currentEndDate = today
        } else {
            currentEndDate = nextDate
        }
        loadStatistics()
    }

    fun selectDay(date: LocalDate?) {
        val currentState = _uiState.value
        // Si ya está seleccionado, deseleccionar (toggle)
        if (currentState.selectedDate == date) {
             _uiState.update { it.copy(selectedDate = null, selectedDateHistory = emptyList()) }
             return
        }

        if (date == null) {
            _uiState.update { it.copy(selectedDate = null, selectedDateHistory = emptyList()) }
            return
        }

        // Filtrar historial para ese día
        viewModelScope.launch {
            val history = medicationRepository.getHistorySnapshot() 
            val allMedications = medicationRepository.getMedicationsSnapshot()
            
            val entriesOnDate = history.filter { it.timestamp.toLocalDate() == date }
                .sortedByDescending { it.timestamp }

            val historyWithUnits = entriesOnDate.map { entry ->
                val entryMed = allMedications.find { it.id == entry.medicationId }
                val entryUnit = getDisplayUnitForMedication(entryMed)
                // Usamos lógica similar a calculateTotalInUnit pero para una entrada
                val entryValue = when (entryUnit) {
                    "mg" -> {
                        val doseValue = entryMed?.dose?.let { extractDoseValue(it) } ?: 1f
                        entry.quantity * doseValue
                    }
                    else -> entry.quantity.toFloat()
                }
                HistoryEntryWithUnit(
                    entry = entry,
                    displayValue = formatValueWithUnit(entryValue, entryUnit),
                    displayUnit = entryUnit,
                    medicationColor = entryMed?.color
                )
            }

            _uiState.update { 
                it.copy(
                    selectedDate = date,
                    selectedDateHistory = historyWithUnits
                )
            }
        }
    }

    // --- Lógica Principal de Cálculo ---

    private fun loadStatistics() {
        val range = _filter.value
        viewModelScope.launch {
            val endDate = currentEndDate
            
            // 1. Determinar fecha de inicio según rango para la query
            val queryStartDate = when (range) {
                FilterRange.WEEK -> endDate.minusDays(6) // 7 días total
                FilterRange.DAYS_15 -> endDate.minusDays(14)
                FilterRange.DAYS_30 -> endDate.minusDays(29)
                FilterRange.ALL_TIME -> LocalDate.of(2020, 1, 1) // Base temporal safe
            }

            // 2. Cargar datos del repositorio
            val allHistory = if (range == FilterRange.ALL_TIME) {
                medicationRepository.getHistorySnapshot()
            } else {
                medicationRepository.getHistoryBetween(queryStartDate.toEpochDay(), endDate.toEpochDay())
            }
            val allMedications = medicationRepository.getMedicationsSnapshot()

            // Capturar una copia local para evitar problemas de smart cast
            val currentSelectedId = selectedMedicationId

            // Determinar la familia del medicamento seleccionado
            val selectedMed = currentSelectedId?.let { id -> allMedications.find { it.id == id } }
            val familyIds = if (currentSelectedId != null) {
                getFamilyMedicationIds(currentSelectedId, allMedications)
            } else {
                emptySet()
            }

            val filteredHistory = allHistory.filter { entry ->
                if (currentSelectedId == null) true
                else {
                    // Incluir entradas de cualquier miembro de la familia
                    entry.medicationId in familyIds
                }
            }

            // La tendencia compara con el periodo anterior de igual duración, que la query de arriba no trae
            val periodDays = ChronoUnit.DAYS.between(queryStartDate, endDate) + 1
            val previousPeriodDoses = if (range == FilterRange.ALL_TIME) 0 else {
                medicationRepository.getHistoryBetween(
                    queryStartDate.minusDays(periodDays).toEpochDay(),
                    queryStartDate.minusDays(1).toEpochDay()
                )
                    .filter { currentSelectedId == null || it.medicationId in familyIds }
                    .sumOf { it.quantity }
            }

            // Calcular desglose por variante si hay familia seleccionada
            val (doseBreakdown, showingFamily, familyName) = if (currentSelectedId != null && familyIds.size > 1) {
                val familyMeds = allMedications.filter { it.id in familyIds }
                val breakdown = calculateDoseBreakdown(filteredHistory, familyMeds, selectedMed)
                val names = familyMeds.map { it.name }.distinct()
                Triple(breakdown, true, names.joinToString("/"))
            } else {
                Triple(emptyList<DoseBreakdownItem>(), false, "")
            }

            val effectiveStartDate = if (range == FilterRange.ALL_TIME && allHistory.isNotEmpty()) {
                allHistory.minOf { it.timestamp.toLocalDate() }
            } else {
                queryStartDate
            }

            val totalDays = ChronoUnit.DAYS.between(effectiveStartDate, endDate) + 1
            val trackingMetrics = calculateTrackingMetrics(
                startDate = effectiveStartDate,
                endDate = endDate,
                history = filteredHistory
            )

            // Calcular unidades dinámicas (mover antes para usar en activityChartData)
            val displayUnit = getDisplayUnitForMedication(selectedMed)

            val activityChartData = generateActivityData(
                start = effectiveStartDate,
                end = endDate,
                history = filteredHistory,
                allMedications = allMedications,
                selectedMedication = selectedMed,
                displayUnit = displayUnit
            )

            val recentHistoryList = filteredHistory.take(5)
            val totalInUnit = calculateTotalInUnit(filteredHistory, selectedMed, displayUnit)
            val averageInUnit = if (totalDays > 0) totalInUnit / totalDays else 0f

            _uiState.update {
                it.copy(
                    activityData = activityChartData,
                    activeFilter = range,
                    rangeStart = effectiveStartDate.takeIf { range != FilterRange.ALL_TIME },
                    rangeEnd = endDate,
                    recentHistory = recentHistoryList,
                    periodEntries = filteredHistory,
                    availableMedications = allMedications, // Incluir todos (padres e hijos) para mostrar familias completas
                    selectedMedicationId = currentSelectedId,
                    selectedMedicationColor = selectedMed?.color,
                    // Unidades dinámicas
                    displayUnit = displayUnit,
                    totalInUnit = formatValueWithUnit(totalInUnit, displayUnit),
                    averageInUnit = formatValueWithUnit(averageInUnit, displayUnit),
                    // Métricas principales
                    totalDosesLogged = trackingMetrics.totalDosesLogged,
                    averageDailyDoses = String.format("%.1f", trackingMetrics.averageDailyDoses),
                    daysSinceLastRecord = trackingMetrics.daysSinceLastRecord,
                    trend = trendOf(trackingMetrics.totalDosesLogged, previousPeriodDoses, periodDays),
                    // Desglose por variantes de dosis
                    doseBreakdown = doseBreakdown,
                    showingFamilyStats = showingFamily,
                    familyName = familyName
                )
            }
        }
    }

    /**
     * Obtiene todos los IDs de medicamentos que pertenecen a la misma familia
     */
    private fun getFamilyMedicationIds(medicationId: String, allMedications: List<Medication>): Set<String> {
        val medication = allMedications.find { it.id == medicationId } ?: return setOf(medicationId)

        // Determinar el ID del padre
        val parentId = medication.parentMedicationId ?: medication.id

        // Encontrar todos los miembros de la familia (padre + hijos)
        val familyIds = mutableSetOf(parentId)
        allMedications.filter { it.parentMedicationId == parentId }.forEach {
            familyIds.add(it.id)
        }

        return familyIds
    }

    /**
     * Calcula el desglose de dosis por cada variante de la familia
     */
    private fun calculateDoseBreakdown(
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        familyMeds: List<Medication>,
        selectedMed: Medication?
    ): List<DoseBreakdownItem> {
        val unit = getDisplayUnitForMedication(selectedMed)

        // Calcular total por cada medicamento
        val totals = familyMeds.map { med ->
            val medHistory = history.filter { it.medicationId == med.id }
            val totalValue = when (unit) {
                "mg" -> {
                    val doseValue = extractDoseValue(med.dose)
                    medHistory.sumOf { it.quantity } * doseValue
                }
                else -> medHistory.sumOf { it.quantity }.toFloat()
            }
            med to totalValue
        }

        val maxTotal = totals.maxOfOrNull { it.second } ?: 1f

        // Ordenar por dosis descendente
        return totals
            .sortedByDescending { extractDoseValue(it.first.dose) }
            .map { (med, total) ->
                DoseBreakdownItem(
                    medicationName = med.name,
                    dose = med.dose,
                    color = med.color,
                    totalValue = total,
                    displayUnit = unit,
                    percentage = if (maxTotal > 0) total / maxTotal else 0f
                )
            }
    }

    private fun calculateTrackingMetrics(
        startDate: LocalDate,
        endDate: LocalDate,
        history: List<com.d4vram.pillmate2.model.HistoryEntry>
    ): TrackingMetrics {
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1
        val totalDosesLogged = history.sumOf { it.quantity }
        val averageDailyDoses = if (totalDays > 0) totalDosesLogged.toFloat() / totalDays else 0f

        val daysSinceLastRecord = history.maxOfOrNull { it.timestamp.toLocalDate() }
            ?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) }

        return TrackingMetrics(totalDosesLogged, averageDailyDoses, daysSinceLastRecord)
    }

    private data class TrackingMetrics(
        val totalDosesLogged: Int,
        val averageDailyDoses: Float,
        val daysSinceLastRecord: Long?
    )

    private fun generateActivityData(
        start: LocalDate,
        end: LocalDate,
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        allMedications: List<Medication> = emptyList(),
        selectedMedication: Medication? = null,
        displayUnit: String = "tomas"
    ): List<ActivityDataPoint> {
        val days = ChronoUnit.DAYS.between(start, end).toInt() + 1

        val (chartStart, chartDays) = if (days > 31) {
              end.minusDays(30) to 31
         } else {
              start to days
         }

        // Etiquetas cortas de fecha en el idioma del móvil (sin palabras propias de la app).
        val locale = java.util.Locale.getDefault()
        val shortDate = com.d4vram.pillmate2.utils.localizedDateFormatter("ddMM", locale)

        return (0 until chartDays).map { offset ->
            val date = chartStart.plusDays(offset.toLong())
            val dayName = if (chartDays <= 7) {
                date.format(java.time.format.DateTimeFormatter.ofPattern("EE", locale))
            } else {
                date.format(shortDate)
            }

            // Tomas reales ese día
            val entriesOnDate = history.filter { it.timestamp.toLocalDate() == date }
            val takenCount = entriesOnDate.sumOf { it.quantity }

            // Calcular valor en la unidad correcta
            val valueInUnit = when (displayUnit) {
                "mg" -> {
                    // Sumar mg de cada entrada según la dosis del medicamento
                    entriesOnDate.sumOf { entry ->
                        val entryMed = allMedications.find { it.id == entry.medicationId }
                        val doseValue = entryMed?.dose?.let { extractDoseValue(it) } ?: 1f
                        entry.quantity * doseValue.toDouble()
                    }.toFloat()
                }
                "gotas", "ml" -> takenCount.toFloat()
                else -> takenCount.toFloat() // tomas
            }

            ActivityDataPoint(
                dayName = dayName,
                totalValue = takenCount.toFloat(),
                unit = displayUnit,
                hasRecords = takenCount > 0,
                date = date,
                valueInUnit = valueInUnit
            )
        }
    }
    // Determina la unidad de display según el tipo de medicamento
    private fun getDisplayUnitForMedication(medication: com.d4vram.pillmate2.model.Medication?): String {
        if (medication == null) return "tomas"
        return when {
            medication.type.equals("Gota", ignoreCase = true) ||
            medication.type.equals("Gotas", ignoreCase = true) -> "gotas"
            medication.type.equals("Jarabe", ignoreCase = true) -> "ml"
            medication.type.equals("Pastilla", ignoreCase = true) ||
            medication.type.equals("Cápsula", ignoreCase = true) ||
            medication.type.equals("Comprimido", ignoreCase = true) -> "mg"
            else -> "tomas"
        }
    }

    // Extrae el valor numérico de la dosis (ej: "10mg" -> 10.0)
    private fun extractDoseValue(dose: String): Float {
        val regex = Regex("([0-9]+\\.?[0-9]*)")
        val match = regex.find(dose)
        return match?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
    }

    // Calcula el total en la unidad correcta
    private fun calculateTotalInUnit(
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        medication: com.d4vram.pillmate2.model.Medication?,
        unit: String
    ): Float {
        return when (unit) {
            "mg" -> {
                // Para mg, multiplicamos cantidad por el valor de dosis del medicamento
                val doseValue = medication?.dose?.let { extractDoseValue(it) } ?: 1f
                history.sumOf { it.quantity }.toFloat() * doseValue
            }
            "gotas", "ml" -> {
                // Para gotas/ml, sumamos directamente las cantidades
                history.sumOf { it.quantity }.toFloat()
            }
            else -> {
                // Para tomas, sumamos las cantidades
                history.sumOf { it.quantity }.toFloat()
            }
        }
    }

    // Formatea el valor para mostrar
    private fun formatValueWithUnit(value: Float, unit: String): String {
        return when {
            value == value.toInt().toFloat() -> "${value.toInt()}"
            else -> String.format("%.1f", value)
        }
    }

    companion object {
        fun provideFactory(
            repository: MedicationRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StatisticsViewModel(repository) as T
            }
        }
    }
}

/**
 * Diferencia en tomas frente a los [days] días anteriores. En tomas y no en %: con pocas tomas el
 * porcentaje se dispara (de 1 a 18 es +1700 %). La frase la compone la pantalla, en su idioma.
 */
data class Trend(val diff: Int, val days: Long)

/** null si en el periodo anterior no hubo tomas con las que comparar. */
internal fun trendOf(current: Int, previous: Int, days: Long): Trend? =
    if (previous <= 0) null else Trend(current - previous, days)
