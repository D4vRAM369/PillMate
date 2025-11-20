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
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.d4vram.pillmate2.model.SortMode

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

    private val historyFlow = historyDao.observeHistory()

    private val medicationsFlow = medicationDao.observeMedications()
        .combine(historyFlow) { medicationEntities, historyEntities ->
            val latestTimestampByMedication = mutableMapOf<String, LocalDateTime>()
            historyEntities.forEach { entry ->
                val timestamp = parseTimestampOrNull(entry.timestampIso) ?: return@forEach
                val key = entry.medicationId.takeIf { it.isNotBlank() } ?: entry.medicationName
                val current = latestTimestampByMedication[key]
                if (current == null || timestamp.isAfter(current)) {
                    latestTimestampByMedication[key] = timestamp
                }
            }

            medicationEntities.map { entity ->
                val lastTakenTimestamp = latestTimestampByMedication[entity.id]
                    ?: latestTimestampByMedication[entity.name]
                entity.toDomain(lastTakenTimestamp)
            }
        }

    /**
     * Calcula el número de tomas de un medicamento según el período especificado
     */
    fun calculateTakenCount(medicationId: String, medicationName: String, period: com.d4vram.pillmate2.ui.theme.ChipCountPeriod): Int {
        val history = historyEntries.value
        val today = LocalDate.now()

        return when (period) {
            com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Daily -> {
                history
                    .filter { (it.medicationId == medicationId || it.medicationName == medicationName) &&
                              it.timestamp.toLocalDate() == today }
                    .sumOf { it.quantity }
            }
            com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Weekly -> {
                history
                    .filter {
                        val daysDiff = ChronoUnit.DAYS.between(it.timestamp.toLocalDate(), today)
                        (it.medicationId == medicationId || it.medicationName == medicationName) &&
                        daysDiff in 0..6
                    }
                    .sumOf { it.quantity }
            }
            com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Monthly -> {
                val firstDayOfMonth = today.withDayOfMonth(1)
                history
                    .filter {
                        (it.medicationId == medicationId || it.medicationName == medicationName) &&
                        !it.timestamp.toLocalDate().isBefore(firstDayOfMonth)
                    }
                    .sumOf { it.quantity }
            }
            com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Total -> {
                history
                    .filter { it.medicationId == medicationId || it.medicationName == medicationName }
                    .sumOf { it.quantity }
            }
            com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Hidden -> 0
        }
    }

    val medications: StateFlow<List<Medication>> = medicationsFlow.stateIn(
        scope,
        SharingStarted.Eagerly,
        emptyList()
    )

    private val historyWithMedicationsFlow = historyFlow
        .combine(medicationDao.observeMedications()) { historyEntities, medicationEntities ->
            val doseFallback = medicationEntities.associateBy { it.name }
            historyEntities.map { it.toDomain(doseFallback) }
        }

    val historyEntries: StateFlow<List<HistoryEntry>> = historyWithMedicationsFlow
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun getHistoryForMedication(medicationId: String): Flow<List<HistoryEntry>> {
        return historyDao.observeHistoryForMedication(medicationId)
            .combine(medicationDao.observeMedications()) { historyEntities, medicationEntities ->
                val doseFallback = medicationEntities.associateBy { it.name }
                historyEntities.map { it.toDomain(doseFallback) }
            }
    }

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
            val formattedLastTaken = formatDateTime(now)
            val updated = medication.copy(
                takenToday = medication.takenToday + safeQuantity,
                lastTaken = formattedLastTaken
            )
            medicationDao.upsertMedication(updated)

            val historyEntry = HistoryEntryEntity(
                id = UUID.randomUUID().toString(),
                medicationId = medication.id,
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
            val createdTimestamp = if (medication.createdAt == 0L) {
                System.currentTimeMillis()
            } else {
                medication.createdAt
            }
            medicationDao.upsertMedication(
                medication.copy(
                    sortOrder = order,
                    createdAt = createdTimestamp
                ).toEntity()
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
                isPrn = medication.isPrn,
                photoPath = medication.photoPath, // 📸 Actualizar foto
                usePhotoInstead = medication.usePhotoInstead // 🖼️ Actualizar preferencia de visualización
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

    fun updateHistoryNotes(entryId: String, notes: String, audioNoteUri: String? = null) {
        scope.launch {
            historyDao.updateNotes(entryId, notes)
            audioNoteUri?.let { uri ->
                historyDao.updateAudioNoteUri(entryId, uri)
            }
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

    /**
     * 🔢 Aplica el ordenamiento seleccionado a la lista de medicamentos
     */
    fun applySorting(medications: List<Medication>, sortMode: SortMode): List<Medication> {
        return when (sortMode) {
            SortMode.MANUAL -> {
                // Orden manual: usar sortOrder de la base de datos
                medications.sortedBy { it.sortOrder }
            }

            SortMode.LAST_TAKEN -> {
                // Por última toma: los más recientemente tomados primero
                // Medicamentos sin lastTaken van al final
                medications.sortedWith(
                    compareByDescending<Medication> { medication ->
                        medication.lastTaken?.let { lastTaken ->
                            // Parseamos el timestamp si es posible
                            parseTimestampOrNull(lastTaken)?.let { timestamp ->
                                timestamp.toEpochSecond(java.time.ZoneOffset.UTC)
                            } ?: 0L
                        } ?: 0L
                    }.thenBy { it.name }
                )
            }

            SortMode.MOST_USED_WEEK -> {
                // Por más utilizados en la última semana
                val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                val recentHistory = historyEntries.value.filter { entry ->
                    entry.timestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() >= oneWeekAgo
                }

                medications.sortedWith(
                    compareByDescending<Medication> { medication ->
                        // Contar cuántas veces aparece en el historial reciente
                        recentHistory.count { entry ->
                            entry.medicationId == medication.id || entry.medicationName == medication.name
                        }
                    }.thenBy { it.name }
                )
            }

            SortMode.ALPHABETICAL -> {
                // Orden alfabético por nombre
                medications.sortedBy { it.name.lowercase() }
            }

            SortMode.CREATION_ORDER -> {
                // Por fecha de creación (más antiguos primero)
                medications.sortedWith(
                    compareBy<Medication> { it.createdAt }
                        .thenBy { it.name }
                )
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

    /**
     * 📊 Obtiene la adherencia de los últimos 7 días para gráficas
     * Retorna una lista de pares (Día de la semana, Porcentaje)
     */
    fun getAdherenceLast7Days(): Flow<List<Pair<String, Float>>> {
        return combine(historyEntries, medicationsFlow) { history, medications ->
            val today = LocalDate.now()
            val last7Days = (0..6).map { today.minusDays(it.toLong()) }.reversed()
            
            last7Days.map { date ->
                val dayName = date.format(DateTimeFormatter.ofPattern("EE")) // Lun, Mar, etc.
                
                val takenOnDate = history
                    .filter { it.timestamp.toLocalDate() == date }
                    .sumOf { it.quantity }

                // Calcular meta total para ese día (excluyendo PRN)
                // Nota: Esto asume que la meta es constante, lo cual es una simplificación válida por ahora
                val totalGoal = medications.filterNot { it.isPrn }.sumOf { it.dailyGoal }
                
                val percentage = if (totalGoal > 0) {
                    (takenOnDate.toFloat() / totalGoal.toFloat()) * 100f
                } else {
                    0f
                }
                
                dayName to percentage.coerceIn(0f, 100f)
            }
        }
    }

    /**
     * 🏆 Obtiene los medicamentos más tomados (Top 5)
     */
    fun getMostTakenMedications(): Flow<List<Pair<Medication, Int>>> {
        return combine(historyEntries, medicationsFlow) { history, medications ->
            val medicationCounts = history
                .groupingBy { it.medicationId }
                .fold(0) { acc, entry -> acc + entry.quantity }
            
            medications
                .map { med -> med to (medicationCounts[med.id] ?: 0) }
                .sortedByDescending { it.second }
                .take(5)
        }
    }

    private fun formatDateTime(dateTime: LocalDateTime): String {
        return "${formatDate(dateTime)} ${formatTime(dateTime)}"
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

    private fun parseTimestampOrNull(timestampIso: String?): LocalDateTime? =
        timestampIso?.let { iso -> runCatching { LocalDateTime.parse(iso) }.getOrNull() }

    private fun String.toScheduleDaysList(): List<String> =
        split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun List<String>.toScheduleDaysString(): String =
        joinToString(separator = ",") { it.trim().uppercase() }

    private fun MedicationEntity.toDomain(
        lastTakenTimestamp: LocalDateTime? = null
    ): Medication = Medication(
        id = id,
        name = name,
        dose = dose,
        type = type,
        color = Color(colorArgb),
        emoji = emoji,
        takenToday = takenToday,
        dailyGoal = dailyGoal,
        lastTaken = lastTakenTimestamp?.let { formatDateTime(it) }
            ?: lastTaken?.let { stored ->
                parseTimestampOrNull(stored)?.let { parsed -> formatDateTime(parsed) } ?: stored
            },
        isPrn = isPrn,
        sortOrder = sortOrder,
        scheduleTime = scheduleTime,
        scheduleDays = scheduleDays.toScheduleDaysList(),
        createdAt = createdAt,
        photoPath = photoPath, // 📸 Ruta de la foto
        usePhotoInstead = usePhotoInstead // 🖼️ Usar foto como identificador
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
        scheduleDays = scheduleDays.toScheduleDaysString(),
        createdAt = createdAt,
        photoPath = photoPath, // 📸 Guardar ruta de foto
        usePhotoInstead = usePhotoInstead // 🖼️ Guardar preferencia de visualización
    )

    private fun HistoryEntryEntity.toDomain(
        doseFallback: Map<String, MedicationEntity> = emptyMap()
    ): HistoryEntry {
        val parsedTimestamp = runCatching { LocalDateTime.parse(timestampIso) }
            .getOrElse { LocalDateTime.now() }

        return HistoryEntry(
            id = id,
            medicationId = medicationId,
            medicationName = medicationName,
            time = formatTime(parsedTimestamp),
            date = formatDate(parsedTimestamp),
            color = Color(colorArgb),
            emoji = emoji,
            quantity = quantity,
            notes = notes,
            timestamp = parsedTimestamp,
            dose = dose ?: doseFallback[medicationName]?.dose,
            audioNoteUri = audioNoteUri
        )
    }

    private fun HistoryEntry.toEntity(): HistoryEntryEntity = HistoryEntryEntity(
        id = id,
        medicationId = medicationId,
        medicationName = medicationName,
        time = time,
        date = date,
        colorArgb = color.toArgb(),
        emoji = emoji,
        quantity = quantity,
        notes = notes,
        timestampIso = timestamp.toString(),
        dose = dose,
        audioNoteUri = audioNoteUri
    )

    /**
     * 📱 Datos de ejemplo para la demo
     */
    private fun getSampleMedications(): List<Medication> {
        val now = System.currentTimeMillis()
        val dayInMillis = 24 * 60 * 60 * 1000L

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
                scheduleDays = listOf("L", "M", "X", "J", "V"),
                createdAt = now - (30 * dayInMillis) // Creado hace 30 días
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
                scheduleDays = listOf("L", "M", "X", "J", "V"),
                createdAt = now - (25 * dayInMillis) // Creado hace 25 días
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
                scheduleDays = listOf("L", "M", "X", "J", "V", "S"),
                createdAt = now - (20 * dayInMillis) // Creado hace 20 días
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
                scheduleDays = listOf("L", "M", "X", "J", "V", "S", "D"),
                createdAt = now - (15 * dayInMillis) // Creado hace 15 días
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
                scheduleDays = emptyList(),
                createdAt = now - (10 * dayInMillis) // Creado hace 10 días
            )
        )
    }

    private fun getSampleHistory(): List<HistoryEntry> {
        val now = LocalDateTime.now()
        return listOf(
            HistoryEntry(id = "h1", medicationId = "3", medicationName = "Vitamina D", time = "12:45", date = "Hoy", color = Color(0xFF10B981), emoji = "💧",
                notes = "Tomado con el almuerzo", timestamp = now.minusHours(2), dose = "1000 IU"),
            HistoryEntry(id = "h2", medicationId = "1", medicationName = "Omeprazol", time = "08:15", date = "Hoy", color = Color(0xFF8B5CF6), emoji = "💊",
                timestamp = now.minusHours(6), dose = "20mg"),

            HistoryEntry(id = "h3", medicationId = "4", medicationName = "Melatonina", time = "21:00", date = "Ayer", color = Color(0xFF6366F1), emoji = "🌙",
                notes = "Antes de dormir", timestamp = now.minusDays(1).minusHours(3), dose = "3mg"),
            HistoryEntry(id = "h4", medicationId = "2", medicationName = "Aspirina", time = "19:30", date = "Ayer", color = Color(0xFFF59E0B), emoji = "💊",
                timestamp = now.minusDays(1).minusHours(5), dose = "100mg"),
            HistoryEntry(id = "h5", medicationId = "3", medicationName = "Vitamina D", time = "12:30", date = "Ayer", color = Color(0xFF10B981), emoji = "💧",
                timestamp = now.minusDays(1).minusHours(12), dose = "1000 IU"),
            HistoryEntry(id = "h6", medicationId = "1", medicationName = "Omeprazol", time = "08:00", date = "Ayer", color = Color(0xFF8B5CF6), emoji = "💊",
                timestamp = now.minusDays(1).minusHours(16), dose = "20mg"),

            HistoryEntry(id = "h7", medicationId = "5", medicationName = "Ibuprofeno", time = "22:15", date = "02/03/2025", color = Color(0xFFEF4444), emoji = "💊",
                timestamp = now.minusDays(2).minusHours(2), dose = "400mg"),
            HistoryEntry(id = "h8", medicationId = "2", medicationName = "Aspirina", time = "18:00", date = "02/03/2025", color = Color(0xFFF59E0B), emoji = "💊",
                timestamp = now.minusDays(2).minusHours(6), dose = "100mg"),
            HistoryEntry(id = "h9", medicationId = "3", medicationName = "Vitamina D", time = "13:15", date = "01/03/2025", color = Color(0xFF10B981), emoji = "💧",
                notes = "Con la comida principal", timestamp = now.minusDays(3).minusHours(1), dose = "1000 IU"),
            HistoryEntry(id = "h10", medicationId = "1", medicationName = "Omeprazol", time = "09:00", date = "01/03/2025", color = Color(0xFF8B5CF6), emoji = "💊",
                timestamp = now.minusDays(3).minusHours(7), dose = "20mg"),

            HistoryEntry(id = "h11", medicationId = "4", medicationName = "Melatonina", time = "21:30", date = "28/02/2025", color = Color(0xFF6366F1), emoji = "🌙",
                timestamp = now.minusDays(6).minusHours(3), dose = "3mg"),
            HistoryEntry(id = "h12", medicationId = "2", medicationName = "Aspirina", time = "17:45", date = "27/02/2025", color = Color(0xFFF59E0B), emoji = "💊",
                notes = "Por dolor de cabeza", timestamp = now.minusDays(7).minusHours(5), dose = "100mg"),
            HistoryEntry(id = "h13", medicationId = "3", medicationName = "Vitamina D", time = "12:00", date = "26/02/2025", color = Color(0xFF10B981), emoji = "💧",
                timestamp = now.minusDays(8).minusHours(0), dose = "1000 IU"),
            HistoryEntry(id = "h14", medicationId = "1", medicationName = "Omeprazol", time = "08:30", date = "25/02/2025", color = Color(0xFF8B5CF6), emoji = "💊",
                timestamp = now.minusDays(9).minusHours(6), dose = "20mg"),

            HistoryEntry(id = "h15", medicationId = "5", medicationName = "Ibuprofeno", time = "20:00", date = "20/02/2025", color = Color(0xFFEF4444), emoji = "💊",
                notes = "Dosis de la noche", timestamp = now.minusDays(14).minusHours(4), dose = "400mg"),
            HistoryEntry(id = "h16", medicationId = "2", medicationName = "Aspirina", time = "16:30", date = "15/02/2025", color = Color(0xFFF59E0B), emoji = "💊",
                timestamp = now.minusDays(19).minusHours(2), dose = "100mg"),
            HistoryEntry(id = "h17", medicationId = "3", medicationName = "Vitamina D", time = "11:45", date = "10/02/2025", color = Color(0xFF10B981), emoji = "💧",
                notes = "", timestamp = now.minusDays(24).minusHours(1), dose = "1000 IU"),
            HistoryEntry(id = "h18", medicationId = "1", medicationName = "Omeprazol", time = "07:15", date = "05/02/2025", color = Color(0xFF8B5CF6), emoji = "💊",
                notes = "En ayunas como siempre", timestamp = now.minusDays(29).minusHours(9), dose = "20mg")
        )
    }
}
