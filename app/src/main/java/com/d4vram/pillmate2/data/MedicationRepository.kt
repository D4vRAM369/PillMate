package com.d4vram.pillmate2.data

import androidx.compose.ui.graphics.Color
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.model.HistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * 🗃️ Repositorio que maneja todos los datos de medicamentos
 * En una app real, esto se conectaría a Room Database
 */
class MedicationRepository {

    // Estados observables para la UI
    private val _medications = MutableStateFlow(getSampleMedications())
    val medications: StateFlow<List<Medication>> = _medications.asStateFlow()

    private val _historyEntries = MutableStateFlow(getSampleHistory())
    val historyEntries: StateFlow<List<HistoryEntry>> = _historyEntries.asStateFlow()

    private val _stats = MutableStateFlow(calculateStats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    data class Stats(
        val takenToday: Int,
        val takenThisWeek: Int,
        val adherencePercentage: Int
    )

    /**
     * 💊 Registra que se tomó un medicamento
     */
    fun takeMedication(medicationId: String) {
        val currentMedications = _medications.value.toMutableList()
        val medicationIndex = currentMedications.indexOfFirst { it.id == medicationId }

        if (medicationIndex != -1) {
            val medication = currentMedications[medicationIndex]
            val updatedMedication = medication.copy(
                takenToday = medication.takenToday + 1,
                lastTaken = getCurrentTime()
            )
            currentMedications[medicationIndex] = updatedMedication
            _medications.value = currentMedications

            // Agregar al historial
            addToHistory(medication)

            // Actualizar estadísticas
            _stats.value = calculateStats()
        }
    }

    /**
     * ➕ Agregar un nuevo medicamento
     */
    fun addMedication(medication: Medication) {
        val currentMedications = _medications.value.toMutableList()
        currentMedications.add(medication)
        _medications.value = currentMedications
        _stats.value = calculateStats()
    }

    /**
     * ✏️ Actualizar un medicamento existente
     */
    fun updateMedication(medication: Medication) {
        val currentMedications = _medications.value.toMutableList()
        val index = currentMedications.indexOfFirst { it.id == medication.id }
        if (index != -1) {
            currentMedications[index] = medication
            _medications.value = currentMedications
            _stats.value = calculateStats()
        }
    }

    /**
     * 🗑️ Eliminar un medicamento
     */
    fun deleteMedication(medicationId: String) {
        val currentMedications = _medications.value.toMutableList()
        currentMedications.removeAll { it.id == medicationId }
        _medications.value = currentMedications
        _stats.value = calculateStats()
    }

    /**
     * 🔍 Obtener un medicamento por ID
     */
    fun getMedicationById(medicationId: String): Medication? {
        return _medications.value.find { it.id == medicationId }
    }

    private fun addToHistory(medication: Medication) {
        val currentHistory = _historyEntries.value.toMutableList()
        val newEntry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            medicationName = medication.name,
            time = getCurrentTime(),
            date = "Hoy",
            color = medication.color,
            emoji = medication.emoji
        )
        currentHistory.add(0, newEntry) // Agregar al inicio
        _historyEntries.value = currentHistory.take(10) // Mantener solo los últimos 10
    }

    private fun calculateStats(): Stats {
        val medications = _medications.value
        val takenToday = medications.sumOf { it.takenToday }
        val totalGoals = medications.sumOf { it.dailyGoal }
        val adherence = if (totalGoals > 0) (takenToday * 100) / totalGoals else 0

        return Stats(
            takenToday = takenToday,
            takenThisWeek = takenToday + 3, // Simulado
            adherencePercentage = adherence.coerceAtMost(100)
        )
    }

    private fun getCurrentTime(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        return formatter.format(Date())
    }

    /**
     * 📱 Datos de ejemplo para la demo
     */
    private fun getSampleMedications(): List<Medication> {
        return listOf(
            Medication(
                id = "1",
                name = "Omeprazol",
                dose = "20mg",
                type = "Cápsula",
                color = Color(0xFF8B5CF6),
                emoji = "💊",
                takenToday = 2,
                dailyGoal = 1,
                lastTaken = "08:15"
            ),
            Medication(
                id = "2",
                name = "Aspirina",
                dose = "100mg",
                type = "Pastilla",
                color = Color(0xFFF59E0B),
                emoji = "💊",
                takenToday = 0,
                dailyGoal = 2,
                lastTaken = "Ayer 19:30"
            ),
            Medication(
                id = "3",
                name = "Vitamina D",
                dose = "1000 IU",
                type = "Gota",
                color = Color(0xFF10B981),
                emoji = "💧",
                takenToday = 1,
                dailyGoal = 1,
                lastTaken = "12:45"
            ),
            Medication(
                id = "4",
                name = "Melatonina",
                dose = "3mg",
                type = "Pastilla",
                color = Color(0xFF6366F1),
                emoji = "🌙",
                takenToday = 0,
                dailyGoal = 1,
                lastTaken = "Ayer 21:00"
            ),
            Medication(
                id = "5",
                name = "Ibuprofeno",
                dose = "400mg",
                type = "Según necesidad",
                color = Color(0xFFEF4444),
                emoji = "💊",
                takenToday = 0,
                dailyGoal = 0,
                lastTaken = "Hace 3 días",
                isPrn = true
            )
        )
    }

    private fun getSampleHistory(): List<HistoryEntry> {
        return listOf(
            HistoryEntry("h1", "Vitamina D", "12:45", "Hoy", Color(0xFF10B981), "💧"),
            HistoryEntry("h2", "Omeprazol", "08:15", "Hoy", Color(0xFF8B5CF6), "💊"),
            HistoryEntry("h3", "Melatonina", "21:00", "Ayer", Color(0xFF6366F1), "🌙"),
            HistoryEntry("h4", "Aspirina", "19:30", "Ayer", Color(0xFFF59E0B), "💊")
        )
    }
}