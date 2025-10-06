package com.d4vram.pillmate2.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.HistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Enum para los períodos de filtro
enum class StatsPeriod(val label: String) {
    WEEK("Última semana"),
    MONTH("Último mes"),
    ALL("Todo")
}

class MedicationStatsViewModel(
    repository: MedicationRepository,
    medicationId: String
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(StatsPeriod.MONTH)
    val selectedPeriod: StateFlow<StatsPeriod> = _selectedPeriod

    private val fullHistory: StateFlow<List<HistoryEntry>> = repository.getHistoryForMedication(medicationId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Flujo de historial que reacciona a los cambios de filtro
    val filteredHistory: StateFlow<List<HistoryEntry>> = combine(fullHistory, selectedPeriod) { history, period ->
        val today = LocalDate.now()
        when (period) {
            StatsPeriod.WEEK -> history.filter {
                ChronoUnit.DAYS.between(it.timestamp.toLocalDate(), today) < 7
            }
            StatsPeriod.MONTH -> history.filter {
                ChronoUnit.MONTHS.between(it.timestamp.toLocalDate().withDayOfMonth(1), today.withDayOfMonth(1)) < 1
            }
            StatsPeriod.ALL -> history
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setPeriod(period: StatsPeriod) {
        _selectedPeriod.value = period
    }

    companion object {
        fun provideFactory(
            repository: MedicationRepository,
            medicationId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MedicationStatsViewModel(repository, medicationId) as T
            }
        }
    }
}