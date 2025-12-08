// kotlin
package com.d4vram.pillmate2.ui.stats.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.ui.stats.AdherenceDataPoint
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
import kotlin.math.roundToInt

// Enums para los filtros de tiempo disponibles
enum class FilterRange {
    WEEK,
    DAYS_15,
    DAYS_30,
    ALL_TIME
}

data class StatisticsUiState(
    val dailyAverage: String = "0", // Promedio diario (nuevo métrica)
    val dosesTaken: Int = 0, // Dosis realmente tomadas (suma de cantidades)
    val lastTaken: String = "Nunca",
    val selectedMedicationName: String = "Todos",
    val adherenceData: List<AdherenceDataPoint> = emptyList(),
    val activeFilter: FilterRange = FilterRange.WEEK,
    val dateRangeText: String = "",
    val recentHistory: List<com.d4vram.pillmate2.model.HistoryEntry> = emptyList(),
    val topMedications: List<Pair<String, Int>> = emptyList(),
    val availableMedications: List<com.d4vram.pillmate2.model.Medication> = emptyList(),
    val selectedMedicationId: String? = null,
    val selectedMedicationColor: androidx.compose.ui.graphics.Color? = null
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

            // 3. Ajustar fecha de inicio REAL para visualización y cálculos
            val effectiveStartDate = if (range == FilterRange.ALL_TIME) {
                val firstHistory = allHistory.minByOrNull { it.timestamp }?.timestamp?.toLocalDate()
                val firstMedCreation = allMedications.filter { it.createdAt > 0 }
                    .minByOrNull { it.createdAt }?.createdAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                listOfNotNull(firstHistory, firstMedCreation).minOrNull() ?: LocalDate.now().minusDays(30)
            } else {
                queryStartDate
            }

            // Filtrar historial si hay un medicamento seleccionado
            val filteredHistory = if (selectedMedicationId != null) {
                allHistory.filter { it.medicationId == selectedMedicationId }
            } else {
                allHistory
            }

            // --- Cálculos de Dosis Totales y Tomadas ---

            // Usar quantity (cantidad de pastillas)
            val dosesTaken = filteredHistory.sumOf { it.quantity }

            // Calcular días totales en el rango para el promedio
            val totalDays = ChronoUnit.DAYS.between(effectiveStartDate, endDate) + 1
            // Promedio Diario = Dosis Totales / Días en Rango
            val dailyAvg = if (totalDays > 0) {
                (dosesTaken.toFloat() / totalDays).let { (it * 10).roundToInt() / 10f }
            } else 0f

            // --- Última Toma ---
            val lastTaken = if (filteredHistory.isNotEmpty()) {
                val mostRecent = filteredHistory.maxByOrNull { it.timestamp }
                mostRecent?.let { entry ->
                    val today = LocalDate.now()
                    val entryDate = entry.timestamp.toLocalDate()
                    val time = entry.timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

                    when {
                        entryDate == today -> "Hoy $time"
                        entryDate == today.minusDays(1) -> "Ayer $time"
                        else -> "${entryDate.dayOfMonth} ${entryDate.month.name.lowercase().take(3)} $time"
                    }
                } ?: "Nunca"
            } else {
                "Nunca"
            }

            // --- Preparar UI ---
            val spanishLocale = java.util.Locale.forLanguageTag("es")
            val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM", spanishLocale)
            val dateRangeText = if (range == FilterRange.ALL_TIME) {
                "Desde siempre"
            } else {
                "${effectiveStartDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}"
            }

            // --- Generar Datos de la Gráfica (Consumo) ---
            val adherenceChartData = generateAdherenceData(
                start = effectiveStartDate,
                end = endDate,
                history = filteredHistory
            )

            // --- Top Medicamentos ---
            val topMedicationsList = filteredHistory.groupBy { it.medicationId }
                .mapValues { entry -> entry.value.sumOf { it.quantity } }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
                .map { (id, count) ->
                    val name = allMedications.find { it.id == id }?.name ?: "Desconocido"
                    name to count
                }

            val recentHistoryList = filteredHistory.take(5)

            val selectedMed = selectedMedicationId?.let { id -> allMedications.find { it.id == id } }

            _uiState.update {
                it.copy(
                    dailyAverage = String.format("%.1f", dailyAvg),
                    dosesTaken = dosesTaken,
                    lastTaken = lastTaken,
                    selectedMedicationName = selectedMed?.name ?: "Todos",
                    adherenceData = adherenceChartData,
                    activeFilter = range,
                    dateRangeText = dateRangeText,
                    recentHistory = recentHistoryList,
                    topMedications = topMedicationsList,
                    availableMedications = allMedications,
                    selectedMedicationId = selectedMedicationId,
                    selectedMedicationColor = selectedMed?.color
                )
            }
        }
    }

    private fun generateAdherenceData(
        start: LocalDate,
        end: LocalDate,
        history: List<com.d4vram.pillmate2.model.HistoryEntry>
    ): List<AdherenceDataPoint> {
        val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
        
        val (chartStart, chartDays) = if (days > 31) {
             end.minusDays(30) to 31
        } else {
             start to days
        }

        val spanishLocale = java.util.Locale.forLanguageTag("es")

        return (0 until chartDays).map { offset ->
            val date = chartStart.plusDays(offset.toLong())
            val dayName = if (chartDays <= 7) {
                date.format(java.time.format.DateTimeFormatter.ofPattern("EE", spanishLocale))
            } else {
                date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
            }

            val fullDate = date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM d 'de' MMMM", spanishLocale))

            // Tomas reales ese día
            val entriesOnDate = history.filter { it.timestamp.toLocalDate() == date }
            val takenCount = entriesOnDate.sumOf { it.quantity }

            // En "A demanda", no hay meta. Usamos 0.
            val dayGoal = 0 
            val percentage = 0f 

            AdherenceDataPoint(
                dayName = dayName,
                percentage = percentage,
                taken = takenCount,
                goal = dayGoal,
                totalValue = takenCount.toFloat(),
                unit = "dosis",
                fullDate = fullDate
            )
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
