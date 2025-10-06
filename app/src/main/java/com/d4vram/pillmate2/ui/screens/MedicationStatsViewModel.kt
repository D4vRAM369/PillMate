package com.d4vram.pillmate2.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.HistoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MedicationStatsViewModel(
    repository: MedicationRepository,
    medicationId: String
) : ViewModel() {

    val history: StateFlow<List<HistoryEntry>> = repository.getHistoryForMedication(medicationId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Aquí añadiremos la lógica para los filtros de "Semana", "Mes", etc.

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
