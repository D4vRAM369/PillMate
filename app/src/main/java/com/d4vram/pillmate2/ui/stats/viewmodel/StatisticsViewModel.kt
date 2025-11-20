package com.d4vram.pillmate2.ui.stats.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.Medication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StatisticsViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _selectedMedicationId = MutableStateFlow<String?>(null)
    val selectedMedicationId: StateFlow<String?> = _selectedMedicationId.asStateFlow()

    private val _weekOffset = MutableStateFlow(0)
    val weekOffset: StateFlow<Int> = _weekOffset.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val adherenceLast7Days: StateFlow<List<com.d4vram.pillmate2.ui.stats.AdherenceDataPoint>> = 
        combine(_selectedMedicationId, _weekOffset) { medicationId, offset ->
            Pair(medicationId, offset)
        }.flatMapLatest { (medicationId, offset) ->
            repository.getAdherenceLast7Days(medicationId, offset)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Formateador de fecha para el rango
    private val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM")

    val dateRange: StateFlow<String> = _weekOffset.map { offset ->
        val end = java.time.LocalDate.now().minusWeeks(offset.toLong())
        val start = end.minusDays(6)
        "${start.format(dateFormatter)} - ${end.format(dateFormatter)}"
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    fun previousWeek() {
        _weekOffset.value += 1
    }

    fun nextWeek() {
        if (_weekOffset.value > 0) {
            _weekOffset.value -= 1
        }
    }

    val mostTakenMedications: StateFlow<List<Pair<Medication, Int>>> = repository.getMostTakenMedications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val stats: StateFlow<MedicationRepository.Stats> = repository.stats

    fun selectMedication(medicationId: String?) {
        // Si ya está seleccionado, deseleccionar (toggle)
        if (_selectedMedicationId.value == medicationId) {
            _selectedMedicationId.value = null
        } else {
            _selectedMedicationId.value = medicationId
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
