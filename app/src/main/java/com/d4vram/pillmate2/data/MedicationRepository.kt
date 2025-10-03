package com.d4vram.pillmate2.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.d4vram.pillmate2.data.local.HistoryDao
import com.d4vram.pillmate2.data.local.HistoryEntryEntity
import com.d4vram.pillmate2.data.local.MedicationDao
import com.d4vram.pillmate2.data.local.MedicationEntity
import com.d4vram.pillmate2.data.local.PillMateDatabase
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 🗃️ Repositorio que conecta la UI con Room
 * Gestiona medicamentos, historial y estadísticas reactivas.
 */
class MedicationRepository(
    private val database: PillMateDatabase,
    private val medicationDao: MedicationDao,
    private val historyDao: HistoryDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val medicationsFlow = medicationDao.observeMedications()
        .map { entities -> entities.map { it.toDomain() } }

    val medications: StateFlow<List<Medication>> = medicationsFlow.stateIn(
        scope,
        SharingStarted.Eagerly,
        emptyList()
    )

    private val historyWithMedicationsFlow = historyDao.observeHistory()
        .combine(medicationDao.observeMedications()) { historyEntities, medicationEntities ->
            val doseFallback = medicationEntities.associateBy { it.name }
            historyEntities.map { it.toDomain(doseFallback) }
        }

    val historyEntries: StateFlow<List<HistoryEntry>> = historyWithMedicationsFlow
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val stats: StateFlow<Stats> = combine(historyEntries, medicationsFlow) { history, medications ->
        calculateStats(history, medications)
    }.stateIn(scope, SharingStarted.Eagerly, Stats(0, 0, 0))

    init {
        scope.launch {
            if (medicationDao.countMedications() == 0) {
                val sampleMedications = getSampleMedications()
                    .mapIndexed { index, medication -> medication.copy(sortOrder = index) }
                    .map { it.toEntity() }
                val sampleHistory = getSampleHistory().map { it.toEntity() }

                medicationDao.upsertMedications(sampleMedications)
                historyDao.insertAll(sampleHistory)
            }
        }
    }

    data class Stats(
        val takenToday: Int,
        val takenThisWeek: Int,
        val adherencePercentage: Int
    )

    fun takeMedication(medicationId: String, quantity: Int = 1) {
        scope.launch {
            val medication = medicationDao.getMedicationById(medicationId) ?: return@launch
            val safeQuantity = quantity.coerceAtLeast(1)
            val now = LocalDateTime.now().withSecond(0).withNano(0)
            val formattedTime = formatTime(now)
            val updated = medication.copy(
                takenToday = medication.takenToday + safeQuantity,
                lastTaken = formattedTime
            )
            medicationDao.upsertMedication(updated)

            val historyEntry = HistoryEntryEntity(
                id = UUID.randomUUID().toString(),
                medicationName = updated.name,
                time = formattedTime,
                date = formatDate(now),
                colorArgb = updated.colorArgb,
                emoji = updated.emoji,
                quantity = safeQuantity,
                notes = "",
                timestampIso = now.toString(),
                dose = updated.dose
            )
            historyDao.insert(historyEntry)
        }
    }

    fun addMedication(medication: Medication) {
        scope.launch {
            val order = medicationDao.getMaxSortOrder() + 1
            medicationDao.upsertMedication(
                medication.copy(sortOrder = order).toEntity()
            )
        }
    }

    fun updateMedication(medication: Medication) {
        scope.launch {
            val current = medicationDao.getMedicationById(medication.id) ?: return@launch
            val updated = current.copy(
                name = medication.name,
                dose = medication.dose,
                type = medication.type,
                colorArgb = medication.color.toArgb(),
                emoji = medication.emoji,
                dailyGoal = medication.dailyGoal,
                isPrn = medication.isPrn
            )
            medicationDao.upsertMedication(updated)
        }
    }

    fun deleteMedication(medicationId: String) {
        scope.launch {
            medicationDao.deleteById(medicationId)
        }
    }

    fun getMedicationById(medicationId: String): Medication? {
        return medications.value.find { it.id == medicationId }
    }

    fun reorderMedications(fromIndex: Int, toIndex: Int) {
        scope.launch {
            val current = medications.value.toMutableList()
            if (fromIndex !in current.indices || toIndex !in current.indices) return@launch
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)

            val reordered = current.mapIndexed { index, medication ->
                medication.copy(sortOrder = index).toEntity()
            }
            medicationDao.upsertMedications(reordered)
        }
    }

    fun clearAllData() {
        scope.launch {
            medicationDao.clear()
            historyDao.clear()
        }
    }

    fun updateHistoryNotes(entryId: String, notes: String) {
        scope.launch {
            historyDao.updateNotes(entryId, notes)
        }
    }

    fun deleteHistoryNotes(entryId: String) {
        scope.launch {
            historyDao.updateNotes(entryId, "")
        }
    }

    fun deleteHistoryEntry(entryId: String) {
        scope.launch {
            historyDao.deleteById(entryId)
        }
    }

    fun updateHistoryTimestamp(entryId: String, newDateTime: LocalDateTime) {
        scope.launch {
            val formattedTime = formatTime(newDateTime)
            val formattedDate = formatDate(newDateTime)
            historyDao.updateTimestamp(
                id = entryId,
                time = formattedTime,
                date = formattedDate,
                timestampIso = newDateTime.toString()
            )
        }
    }

    suspend fun getMedicationsSnapshot(): List<Medication> {
        return medicationDao.getAllMedications().map { it.toDomain() }
    }

    suspend fun getHistorySnapshot(): List<HistoryEntry> {
        return historyDao.getAllHistory().map { it.toDomain() }
    }

    suspend fun replaceAllData(
        medications: List<Medication>,
        history: List<HistoryEntry>
    ) {
        database.withTransaction {
            medicationDao.clear()
            historyDao.clear()
            if (medications.isNotEmpty()) {
                val ordered = medications.mapIndexed { index, medication ->
                    medication.copy(sortOrder = index).toEntity()
                }
                medicationDao.upsertMedications(ordered)
            }
            if (history.isNotEmpty()) {
                historyDao.insertAll(history.map { it.toEntity() })
            }
        }
    }

    private fun calculateStats(history: List<HistoryEntry>, medications: List<Medication>): Stats {
        val today = LocalDate.now()
        val takenToday = history
            .filter { it.timestamp.toLocalDate() == today }
            .sumOf { it.quantity }

        val takenThisWeek = history
            .filter {
                val daysDiff = ChronoUnit.DAYS.between(it.timestamp.toLocalDate(), today)
                daysDiff in 0..6
            }
            .sumOf { it.quantity }

        val totalGoals = medications.filterNot { it.isPrn }.sumOf { it.dailyGoal }
        val adherence = if (totalGoals > 0) {
            ((takenToday.toDouble() / totalGoals.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        return Stats(
            takenToday = takenToday,
            takenThisWeek = takenThisWeek,
            adherencePercentage = adherence
        )
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

    private fun formatTime(dateTime: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return dateTime.format(formatter)
    }

    private fun String.toScheduleDaysList(): List<String> =
        split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun List<String>.toScheduleDaysString(): String =
        joinToString(separator = ",") { it.trim().uppercase() }

    private fun MedicationEntity.toDomain(): Medication = Medication(
        id = id,
        name = name,
        dose = dose,
        type = type,
        color = Color(colorArgb),
        emoji = emoji,
        takenToday = takenToday,
        dailyGoal = dailyGoal,
        lastTaken = lastTaken,
        isPrn = isPrn,
        sortOrder = sortOrder,
        scheduleTime = scheduleTime,
        scheduleDays = scheduleDays.toScheduleDaysList()
    )

    private fun Medication.toEntity(): MedicationEntity = MedicationEntity(
        id = id,
        name = name,
        dose = dose,
        type = type,
        colorArgb = color.toArgb(),
        emoji = emoji,
        takenToday = takenToday,
        dailyGoal = dailyGoal,
        lastTaken = lastTaken,
        isPrn = isPrn,
        sortOrder = sortOrder,
        scheduleTime = scheduleTime,
        scheduleDays = scheduleDays.toScheduleDaysString()
    )

    private fun HistoryEntryEntity.toDomain(
        doseFallback: Map<String, MedicationEntity> = emptyMap()
    ): HistoryEntry {
        val parsedTimestamp = runCatching { LocalDateTime.parse(timestampIso) }
            .getOrElse { LocalDateTime.now() }

        return HistoryEntry(
            id = id,
            medicationName = medicationName,
            time = formatTime(parsedTimestamp),
            date = formatDate(parsedTimestamp),
            color = Color(colorArgb),
            emoji = emoji,
            quantity = quantity,
            notes = notes,
            timestamp = parsedTimestamp,
            dose = dose ?: doseFallback[medicationName]?.dose
        )
    }

    private fun HistoryEntry.toEntity(): HistoryEntryEntity = HistoryEntryEntity(
        id = id,
        medicationName = medicationName,
        time = time,
        date = date,
        colorArgb = color.toArgb(),
        emoji = emoji,
        quantity = quantity,
        notes = notes,
        timestampIso = timestamp.toString(),
        dose = dose
    )

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
                lastTaken = "08:15",
                isPrn = false,
                sortOrder = 0,
                scheduleTime = "08:00",
                scheduleDays = listOf("L", "M", "X", "J", "V")
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
                lastTaken = "Ayer 19:30",
                isPrn = false,
                sortOrder = 1,
                scheduleTime = "19:00",
                scheduleDays = listOf("L", "M", "X", "J", "V")
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
                lastTaken = "12:45",
                isPrn = false,
                sortOrder = 2,
                scheduleTime = "12:30",
                scheduleDays = listOf("L", "M", "X", "J", "V", "S")
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
                lastTaken = "Ayer 21:00",
                isPrn = false,
                sortOrder = 3,
                scheduleTime = "22:00",
                scheduleDays = listOf("L", "M", "X", "J", "V", "S", "D")
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
                isPrn = true,
                sortOrder = 4,
                scheduleTime = null,
                scheduleDays = emptyList()
            )
        )
    }

    private fun getSampleHistory(): List<HistoryEntry> {
        val now = LocalDateTime.now()
        return listOf(
            HistoryEntry("h1", "Vitamina D", "12:45", "Hoy", Color(0xFF10B981), "💧",
                notes = "Tomado con el almuerzo", timestamp = now.minusHours(2), dose = "1000 IU"),
            HistoryEntry("h2", "Omeprazol", "08:15", "Hoy", Color(0xFF8B5CF6), "💊",
                timestamp = now.minusHours(6), dose = "20mg"),

            HistoryEntry("h3", "Melatonina", "21:00", "Ayer", Color(0xFF6366F1), "🌙",
                notes = "Antes de dormir", timestamp = now.minusDays(1).minusHours(3), dose = "3mg"),
            HistoryEntry("h4", "Aspirina", "19:30", "Ayer", Color(0xFFF59E0B), "💊",
                timestamp = now.minusDays(1).minusHours(5), dose = "100mg"),
            HistoryEntry("h5", "Vitamina D", "12:30", "Ayer", Color(0xFF10B981), "💧",
                timestamp = now.minusDays(1).minusHours(12), dose = "1000 IU"),
            HistoryEntry("h6", "Omeprazol", "08:00", "Ayer", Color(0xFF8B5CF6), "💊",
                timestamp = now.minusDays(1).minusHours(16), dose = "20mg"),

            HistoryEntry("h7", "Ibuprofeno", "22:15", "02/03/2025", Color(0xFF6366F1), "💊",
                timestamp = now.minusDays(2).minusHours(2), dose = "5mg"),
            HistoryEntry("h8", "Aspirina", "18:00", "02/03/2025", Color(0xFFF59E0B), "💊",
                timestamp = now.minusDays(2).minusHours(6), dose = "100mg"),
            HistoryEntry("h9", "Vitamina D", "13:15", "01/03/2025", Color(0xFF10B981), "💧",
                notes = "Con la comida principal", timestamp = now.minusDays(3).minusHours(1), dose = "1000 IU"),
            HistoryEntry("h10", "Omeprazol", "09:00", "01/03/2025", Color(0xFF8B5CF6), "💊",
                timestamp = now.minusDays(3).minusHours(7), dose = "20mg"),

            HistoryEntry("h11", "Melatonina", "21:30", "28/02/2025", Color(0xFF6366F1), "🌙",
                timestamp = now.minusDays(6).minusHours(3), dose = "3mg"),
            HistoryEntry("h12", "Aspirina", "17:45", "27/02/2025", Color(0xFFF59E0B), "💊",
                notes = "Por dolor de cabeza", timestamp = now.minusDays(7).minusHours(5), dose = "100mg"),
            HistoryEntry("h13", "Vitamina D", "12:00", "26/02/2025", Color(0xFF10B981), "💧",
                timestamp = now.minusDays(8).minusHours(0), dose = "1000 IU"),
            HistoryEntry("h14", "Omeprazol", "08:30", "25/02/2025", Color(0xFF8B5CF6), "💊",
                timestamp = now.minusDays(9).minusHours(6), dose = "20mg"),

            HistoryEntry("h15", "Ibuprofeno", "20:00", "20/02/2025", Color(0xFF6366F1), "💊",
                notes = "Dosis de la noche", timestamp = now.minusDays(14).minusHours(4), dose = "5mg"),
            HistoryEntry("h16", "Aspirina", "16:30", "15/02/2025", Color(0xFFF59E0B), "💊",
                timestamp = now.minusDays(19).minusHours(2), dose = "100mg"),
            HistoryEntry("h17", "Vitamina D", "11:45", "10/02/2025", Color(0xFF10B981), "💧",
                notes = "", timestamp = now.minusDays(24).minusHours(1), dose = "1000 IU"),
            HistoryEntry("h18", "Omeprazol", "07:15", "05/02/2025", Color(0xFF8B5CF6), "💊",
                notes = "En ayunas como siempre", timestamp = now.minusDays(29).minusHours(9), dose = "20mg")
        )
    }
}
