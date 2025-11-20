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
import kotlinx.coroutines.flow.stateIn

class StatisticsViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _selectedMedicationId = MutableStateFlow<String?>(null)
    val selectedMedicationId: StateFlow<String?> = _selectedMedicationId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val adherenceLast7Days: StateFlow<List<com.d4vram.pillmate2.ui.stats.AdherenceDataPoint>> = _selectedMedicationId
        .flatMapLatest { medicationId ->
            repository.getAdherenceLast7Days(medicationId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
