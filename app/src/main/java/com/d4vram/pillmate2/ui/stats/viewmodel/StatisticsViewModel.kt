// kotlin
package com.d4vram.pillmate2.ui.stats.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.ui.stats.AdherenceDataPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

// 1. MOVIMOS ESTAS DEFINICIONES FUERA DE LA CLASE para que sean públicas.
enum class FilterRange {
    WEEK,
    DAYS_15,
    DAYS_30,
    ALL_TIME
}

data class StatisticsUiState(
    val totalDoses: Int = 0,
    val dosesTaken: Int = 0,
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

    private var currentEndDate: LocalDate = LocalDate.now()

    init {
        _filter.onEach { range ->
            // Al cambiar el filtro, reseteamos la fecha a hoy
            currentEndDate = LocalDate.now()
            loadStatistics()
        }.launchIn(viewModelScope)
    }

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
            FilterRange.ALL_TIME -> currentEndDate // No tiene sentido mover "todo el tiempo"
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

        // No permitir ir al futuro más allá de hoy (o el final del periodo actual que contenga hoy)
        if (nextDate.isAfter(today)) {
            currentEndDate = today
        } else {
            currentEndDate = nextDate
        }
        loadStatistics()
    }

    private fun loadStatistics() {
        val range = _filter.value
        viewModelScope.launch {
            val endDate = currentEndDate
            val startDate = when (range) {
                FilterRange.WEEK -> endDate.minusDays(6)
                FilterRange.DAYS_15 -> endDate.minusDays(14)
                FilterRange.DAYS_30 -> endDate.minusDays(29)
                FilterRange.ALL_TIME -> LocalDate.of(1970, 1, 1)
            }

            val allHistory = medicationRepository.getHistoryBetween(startDate.toEpochDay(), endDate.toEpochDay())
            val allMedications = medicationRepository.getMedicationsSnapshot()

            // Filtrar por medicamento si hay uno seleccionado
            val history = if (selectedMedicationId != null) {
                allHistory.filter { it.medicationId == selectedMedicationId }
            } else {
                allHistory
            }

            // Obtener color del medicamento seleccionado
            val selectedMedColor = selectedMedicationId?.let { id ->
                allMedications.find { it.id == id }?.color
            }

            // Obtener nombre del medicamento seleccionado (nuevo)
            val selectedMedName = selectedMedicationId?.let { id ->
                allMedications.find { it.id == id }?.name
            } ?: "Todos"

            val dosesTaken = history.size

            // Dosis totales: si hay medicamento seleccionado, solo contar ese; si no, todos
            val totalDosesInRange = if (selectedMedicationId != null) {
                val selectedMed = allMedications.find { it.id == selectedMedicationId }
                val days = if (range == FilterRange.ALL_TIME) 1 else (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1)
                (selectedMed?.dailyGoal ?: 0) * days.toInt()
            } else {
                allMedications.sumOf { it.dailyGoal } * (if (range == FilterRange.ALL_TIME) 1 else (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt())
            }

            // Última toma: obtener la más reciente del historial filtrado
            val lastTaken = if (history.isNotEmpty()) {
                val mostRecent = history.maxByOrNull { it.timestamp }
                mostRecent?.let { entry ->
                    val today = java.time.LocalDate.now()
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

            // Más Tomado siempre se calcula del historial completo (no filtrado)
            val mostTakenMedication = allHistory.groupBy { it.medicationId }
                .maxByOrNull { it.value.size }
                ?.let { entry -> allMedications.find { it.id == entry.key }?.name }
                ?: "N/A"

            val spanishLocale = java.util.Locale.forLanguageTag("es")
            val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM", spanishLocale)
            val dateRangeText = if (range == FilterRange.ALL_TIME) "Desde siempre" else "${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}"

            val adherenceData = if (range == FilterRange.ALL_TIME) {
                val chartStartDate = endDate.minusDays(29)
                val chartDays = java.time.temporal.ChronoUnit.DAYS.between(chartStartDate, endDate).toInt() + 1
                (0 until chartDays).map { offset ->
                    val date = chartStartDate.plusDays(offset.toLong())
                    val dayName = date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
                    val fullDate = date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM d 'de' MMMM", spanishLocale))
                    val entriesOnDate = history.filter { it.timestamp.toLocalDate() == date }
                    val takenCount = entriesOnDate.size
                    val totalGoal = allMedications.filterNot { it.isPrn }.sumOf { it.dailyGoal }
                    val percentage = if (totalGoal > 0) (takenCount.toFloat() / totalGoal) * 100f else 0f

                    AdherenceDataPoint(
                        dayName = dayName,
                        percentage = percentage,
                        taken = takenCount,
                        goal = totalGoal,
                        totalValue = takenCount.toFloat(),
                        unit = "dosis",
                        fullDate = fullDate
                    )
                }
            } else {
                val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
                (0 until days).map { offset ->
                    val date = startDate.plusDays(offset.toLong())
                    val dayName = date.format(java.time.format.DateTimeFormatter.ofPattern("EE", spanishLocale))
                    val fullDate = date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM d 'de' MMMM", spanishLocale))
                    val entriesOnDate = history.filter { it.timestamp.toLocalDate() == date }
                    val takenCount = entriesOnDate.size
                    val totalGoal = allMedications.filterNot { it.isPrn }.sumOf { it.dailyGoal }
                    val percentage = if (totalGoal > 0) (takenCount.toFloat() / totalGoal) * 100f else 0f

                    AdherenceDataPoint(
                        dayName = dayName,
                        percentage = percentage,
                        taken = takenCount,
                        goal = totalGoal,
                        totalValue = takenCount.toFloat(),
                        unit = "dosis",
                        fullDate = fullDate
                    )
                }
            }

            // Top Medicamentos (Top 5)
            val topMedicationsList = history.groupBy { it.medicationId }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
                .map { (id, count) ->
                    val name = allMedications.find { it.id == id }?.name ?: "Desconocido"
                    name to count
                }

            // Historial Reciente (Últimos 5 del periodo seleccionado)
            val recentHistoryList = history.take(5)

            _uiState.update {
                it.copy(
                    totalDoses = totalDosesInRange.toInt(),
                    dosesTaken = dosesTaken,
                    lastTaken = lastTaken,
                    selectedMedicationName = selectedMedName,
                    adherenceData = adherenceData,
                    activeFilter = range,
                    dateRangeText = dateRangeText,
                    recentHistory = recentHistoryList,
                    topMedications = topMedicationsList,
                    availableMedications = allMedications,
                    selectedMedicationId = selectedMedicationId,
                    selectedMedicationColor = selectedMedColor
                )
            }
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
