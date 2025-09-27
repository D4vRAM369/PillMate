package com.d4vram.pillmate2.data

import androidx.compose.ui.graphics.Color
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.model.HistoryEntry
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 🗃️ Repositorio que maneja todos los datos de medicamentos
 * En una app real, esto se conectaría a Room Database
 */
class MedicationRepository(
    private val storage: MedicationStorage
) {

    // Usamos un scope propio para lanzar las operaciones de IO sin bloquear la UI
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Estados observables para la UI
    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications.asStateFlow()

    private val _historyEntries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val historyEntries: StateFlow<List<HistoryEntry>> = _historyEntries.asStateFlow()

    private val _stats = MutableStateFlow(Stats(0, 0, 0))
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    init {
        // Cargamos datos persistidos; si no hay, inicializamos con datos demo y los guardamos
        scope.launch {
            val storedMedications = storage.loadMedications()
            val initialMedications = if (storedMedications.isEmpty()) {
                val samples = getSampleMedications()
                storage.saveMedications(samples)
                samples
            } else {
                storedMedications
            }

            val storedHistory = storage.loadHistory()
            val initialHistory = if (storedHistory.isEmpty()) {
                val samples = getSampleHistory()
                storage.saveHistory(samples)
                samples
            } else {
                storedHistory
            }

            _medications.value = initialMedications
            _historyEntries.value = initialHistory
            _stats.value = calculateStats(initialMedications)
        }
    }

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
            updateMedications(currentMedications)

            // Agregamos al historial la versión actualizada para reflejar la hora correcta
            addToHistory(updatedMedication)
        }
    }

    /**
     * ➕ Agregar un nuevo medicamento
     */
    fun addMedication(medication: Medication) {
        val currentMedications = _medications.value.toMutableList()
        currentMedications.add(medication)
        updateMedications(currentMedications)
    }

    /**
     * ✏️ Actualizar un medicamento existente
     */
    fun updateMedication(medication: Medication) {
        val currentMedications = _medications.value.toMutableList()
        val index = currentMedications.indexOfFirst { it.id == medication.id }
        if (index != -1) {
            currentMedications[index] = medication
            updateMedications(currentMedications)
        }
    }

    /**
     * 🗑️ Eliminar un medicamento
     */
    fun deleteMedication(medicationId: String) {
        val currentMedications = _medications.value.toMutableList()
        currentMedications.removeAll { it.id == medicationId }
        updateMedications(currentMedications)
    }

    /**
     * 🔍 Obtener un medicamento por ID
     */
    fun getMedicationById(medicationId: String): Medication? {
        return _medications.value.find { it.id == medicationId }
    }

    /**
     * 🔄 Reordenar medicamentos
     */
    fun reorderMedications(fromIndex: Int, toIndex: Int) {
        val currentMedications = _medications.value.toMutableList()
        if (fromIndex in 0 until currentMedications.size && toIndex in 0 until currentMedications.size) {
            val item = currentMedications.removeAt(fromIndex)
            currentMedications.add(toIndex, item)
            updateMedications(currentMedications)
        }
    }

    fun clearAllData() {
        _medications.value = emptyList()
        _historyEntries.value = emptyList()
        _stats.value = Stats(0, 0, 0)
        scope.launch {
            storage.saveMedications(emptyList())
            storage.saveHistory(emptyList())
        }
    }

    private fun addToHistory(medication: Medication) {
        val currentHistory = _historyEntries.value.toMutableList()
        val now = LocalDateTime.now()
        val newEntry = HistoryEntry(
            id = UUID.randomUUID().toString(),
            medicationName = medication.name,
            time = getCurrentTime(),
            date = formatDate(now),
            color = medication.color,
            emoji = medication.emoji,
            notes = "",
            timestamp = now
        )
        currentHistory.add(0, newEntry) // Agregar al inicio
        updateHistory(currentHistory)
    }

    /**
     * 📝 Actualizar las notas de una entrada del historial
     */
    fun updateHistoryNotes(entryId: String, notes: String) {
        val currentHistory = _historyEntries.value.toMutableList()
        val entryIndex = currentHistory.indexOfFirst { it.id == entryId }
        
        if (entryIndex != -1) {
            val updatedEntry = currentHistory[entryIndex].copy(notes = notes)
            currentHistory[entryIndex] = updatedEntry
            updateHistory(currentHistory)
        }
    }

    /**
     * 🗑️ Eliminar las notas de una entrada del historial
     */
    fun deleteHistoryNotes(entryId: String) {
        val currentHistory = _historyEntries.value.toMutableList()
        val entryIndex = currentHistory.indexOfFirst { it.id == entryId }
        
        if (entryIndex != -1) {
            val updatedEntry = currentHistory[entryIndex].copy(notes = "")
            currentHistory[entryIndex] = updatedEntry
            updateHistory(currentHistory)
        }
    }

    fun deleteHistoryEntry(entryId: String) {
        val updatedHistory = _historyEntries.value.filter { it.id != entryId }
        updateHistory(updatedHistory)
    }

    private fun formatDate(dateTime: LocalDateTime): String {
        val today = LocalDateTime.now()
        val yesterday = today.minusDays(1)
        
        return when {
            dateTime.toLocalDate() == today.toLocalDate() -> "Hoy"
            dateTime.toLocalDate() == yesterday.toLocalDate() -> "Ayer"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                dateTime.format(formatter)
            }
        }
    }

    private fun calculateStats(medications: List<Medication>): Stats {
        val takenToday = medications.sumOf { it.takenToday }
        val totalGoals = medications.sumOf { it.dailyGoal }
        val adherence = if (totalGoals > 0) (takenToday * 100) / totalGoals else 0

        return Stats(
            takenToday = takenToday,
            takenThisWeek = takenToday + 3, // Simulado
            adherencePercentage = adherence.coerceAtMost(100)
        )
    }

    private fun updateMedications(updated: List<Medication>) {
        _medications.value = updated
        _stats.value = calculateStats(updated)
        scope.launch { storage.saveMedications(updated) }
    }

    private fun updateHistory(updated: List<HistoryEntry>) {
        _historyEntries.value = updated
        scope.launch { storage.saveHistory(updated) }
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
        val now = LocalDateTime.now()
        return listOf(
            // Hoy
            HistoryEntry("h1", "Vitamina D", "12:45", "Hoy", Color(0xFF10B981), "💧", 
                notes = "Tomado con el almuerzo", timestamp = now.minusHours(2)),
            HistoryEntry("h2", "Omeprazol", "08:15", "Hoy", Color(0xFF8B5CF6), "💊", 
                timestamp = now.minusHours(6)),
            
            // Ayer
            HistoryEntry("h3", "Melatonina", "21:00", "Ayer", Color(0xFF6366F1), "🌙", 
                notes = "Antes de dormir", timestamp = now.minusDays(1).minusHours(3)),
            HistoryEntry("h4", "Aspirina", "19:30", "Ayer", Color(0xFFF59E0B), "💊", 
                timestamp = now.minusDays(1).minusHours(5)),
            HistoryEntry("h5", "Vitamina D", "12:30", "Ayer", Color(0xFF10B981), "💧", 
                timestamp = now.minusDays(1).minusHours(12)),
            HistoryEntry("h6", "Omeprazol", "08:00", "Ayer", Color(0xFF8B5CF6), "💊", 
                timestamp = now.minusDays(1).minusHours(16)),
                
            // Esta semana
            HistoryEntry("h7", "Ibuprofeno", "22:15", "02/03/2025", Color(0xFF6366F1), "💊", 
                timestamp = now.minusDays(2).minusHours(2)),
            HistoryEntry("h8", "Aspirina", "18:00", "02/03/2025", Color(0xFFF59E0B), "💊", 
                timestamp = now.minusDays(2).minusHours(6)),
            HistoryEntry("h9", "Vitamina D", "13:15", "01/03/2025", Color(0xFF10B981), "💧", 
                notes = "Con la comida principal", timestamp = now.minusDays(3).minusHours(1)),
            HistoryEntry("h10", "Omeprazol", "09:00", "01/03/2025", Color(0xFF8B5CF6), "💊", 
                timestamp = now.minusDays(3).minusHours(7)),
                
            // Semana pasada
            HistoryEntry("h11", "Melatonina", "21:30", "28/02/2025", Color(0xFF6366F1), "🌙", 
                timestamp = now.minusDays(6).minusHours(3)),
            HistoryEntry("h12", "Aspirina", "17:45", "27/02/2025", Color(0xFFF59E0B), "💊", 
                notes = "Por dolor de cabeza", timestamp = now.minusDays(7).minusHours(5)),
            HistoryEntry("h13", "Vitamina D", "12:00", "26/02/2025", Color(0xFF10B981), "💧", 
                timestamp = now.minusDays(8).minusHours(0)),
            HistoryEntry("h14", "Omeprazol", "08:30", "25/02/2025", Color(0xFF8B5CF6), "💊", 
                timestamp = now.minusDays(9).minusHours(6)),
                
            // Mes pasado
            HistoryEntry("h15", "Ibuprofeno", "20:00", "20/02/2025", Color(0xFF6366F1), "💊", 
                notes = "Dosis de la noche", timestamp = now.minusDays(14).minusHours(4)),
            HistoryEntry("h16", "Aspirina", "16:30", "15/02/2025", Color(0xFFF59E0B), "💊", 
                timestamp = now.minusDays(19).minusHours(2)),
            HistoryEntry("h17", "Vitamina D", "11:45", "10/02/2025", Color(0xFF10B981), "💧", 
                timestamp = now.minusDays(24).minusHours(1)),
            HistoryEntry("h18", "Omeprazol", "07:15", "05/02/2025", Color(0xFF8B5CF6), "💊", 
                notes = "En ayunas como siempre", timestamp = now.minusDays(29).minusHours(9))
        )
    }
}
