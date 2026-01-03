// kotlin
package com.d4vram.pillmate2.ui.stats.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.data.MedicationRepository

import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.ui.stats.ActivityDataPoint
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
import kotlin.math.abs

// Enums para los filtros de tiempo disponibles
enum class FilterRange {
    WEEK,
    DAYS_15,
    DAYS_30,
    ALL_TIME
}

data class StatisticsUiState(
    val dailyAverage: String = "0", 
    val dosesTaken: Int = 0, 
    val lastTaken: String = "Nunca",
    val selectedMedicationName: String = "Todos",
    val activityData: List<ActivityDataPoint> = emptyList(),
    val activeFilter: FilterRange = FilterRange.WEEK,
    val dateRangeText: String = "",
    val recentHistory: List<com.d4vram.pillmate2.model.HistoryEntry> = emptyList(),
    val topMedications: List<Triple<String, Int, String>> = emptyList(), // name, count, id
    val availableMedications: List<com.d4vram.pillmate2.model.Medication> = emptyList(),
    val selectedMedicationId: String? = null,
    val selectedMedicationColor: androidx.compose.ui.graphics.Color? = null,
    
    // 🔥 MÉTRICAS DE SEGUIMIENTO (Sin juicios)
    val narrativeSummary: String = "", // Resumen textual neutral
    val totalDosesLogged: Int = 0,
    val averageDailyDoses: String = "0",
    val mostActiveDay: String = "",
    val leastActiveDay: String = "",
    val loggingTrend: String = "0%",
    val weeklyActivityPattern: Map<String, Int> = emptyMap(),
    val monthlyActivityData: List<StatisticsViewModel.MonthlyActivityPoint> = emptyList(),
    val loggingConsistency: String = "", 
    val totalDaysWithRecords: Int = 0,
    val averageDosesPerActiveDay: String = "0",
    val timeSinceLastRecord: String = "", // Tiempo transcurrido desde el último registro
    
    // 🔥 PATRONES TEMPORALES
    val hourlyPattern: Map<String, Int> = emptyMap()
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
            
            val filteredHistory = allHistory.filter { entry ->
                if (selectedMedicationId == null) true
                else {
                    val med = allMedications.find { it.id == entry.medicationId }
                    entry.medicationId == selectedMedicationId || med?.parentMedicationId == selectedMedicationId
                }
            }

            val effectiveStartDate = if (range == FilterRange.ALL_TIME && allHistory.isNotEmpty()) {
                allHistory.minOf { it.timestamp.toLocalDate() }
            } else {
                queryStartDate
            }

            val totalDays = ChronoUnit.DAYS.between(effectiveStartDate, endDate) + 1
            val dateRangeText = if (range == FilterRange.ALL_TIME) "Todo el tiempo" 
                               else "${formatDate(effectiveStartDate)} - ${formatDate(endDate)}"

            val trackingMetrics = calculateTrackingMetrics(
                startDate = effectiveStartDate,
                endDate = endDate,
                history = filteredHistory,
                medications = allMedications,
                selectedMedicationId = selectedMedicationId,
                weeklyPatternHistory = allHistory.filter { it.timestamp.toLocalDate() in endDate.minusDays(27)..endDate }
            )

            val activityChartData = generateActivityData(
                start = effectiveStartDate,
                end = endDate,
                history = filteredHistory
            )

            val topMedicationsList = filteredHistory.groupBy { entry ->
                allMedications.find { it.id == entry.medicationId }?.parentMedicationId ?: entry.medicationId
            }
                .mapValues { entry -> entry.value.sumOf { it.quantity } }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
                .map { (id, count) ->
                    val medication = allMedications.find { it.id == id }
                    val name = medication?.name ?: "Desconocido"
                    Triple(name, count, id)
                }

            val recentHistoryList = filteredHistory.take(5)

            val selectedMed = selectedMedicationId?.let { id -> allMedications.find { it.id == id } }

            // Generar Narrativa Humana
            val narrative = generateNarrativeSummary(
                history = filteredHistory,
                range = range,
                daysWithRecords = trackingMetrics.totalDaysWithRecords,
                totalDays = totalDays,
                trend = trackingMetrics.loggingTrend
            )

            val lastTakenText = filteredHistory.firstOrNull()?.let { "${it.date} ${it.time}" } ?: "Nunca"

            _uiState.update {
                it.copy(
                    dailyAverage = String.format("%.1f", trackingMetrics.averageDailyDoses),
                    dosesTaken = trackingMetrics.totalDosesLogged,
                    lastTaken = lastTakenText,
                    selectedMedicationName = selectedMed?.name ?: "Todos",
                    activityData = activityChartData,
                    activeFilter = range,
                    dateRangeText = dateRangeText,
                    recentHistory = recentHistoryList,
                    topMedications = topMedicationsList,
                    availableMedications = allMedications.filter { it.parentMedicationId == null }, // Solo mostrar padres
                    selectedMedicationId = selectedMedicationId,
                    selectedMedicationColor = selectedMed?.color,
                    narrativeSummary = narrative,
                    totalDosesLogged = trackingMetrics.totalDosesLogged,
                    averageDailyDoses = String.format("%.1f", trackingMetrics.averageDailyDoses),
                    mostActiveDay = trackingMetrics.mostActiveDay,
                    leastActiveDay = trackingMetrics.leastActiveDay,
                    loggingTrend = if (trackingMetrics.loggingTrend > 0) "+${trackingMetrics.loggingTrend}%" else "${trackingMetrics.loggingTrend}%",
                    weeklyActivityPattern = trackingMetrics.weeklyActivityPattern,
                    monthlyActivityData = trackingMetrics.monthlyActivityData,
                    loggingConsistency = trackingMetrics.loggingConsistency,
                    totalDaysWithRecords = trackingMetrics.totalDaysWithRecords,
                    averageDosesPerActiveDay = String.format("%.1f", trackingMetrics.averageDosesPerActiveDay),
                    timeSinceLastRecord = trackingMetrics.timeSinceLastRecord,
                    hourlyPattern = trackingMetrics.hourlyPattern
                )
            }
        }
    }

    private fun generateNarrativeSummary(
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        range: FilterRange,
        daysWithRecords: Int,
        totalDays: Long,
        trend: Int
    ): String {
        if (history.isEmpty()) {
            return "No se han registrado dosis en este periodo."
        }

        val frequency = daysWithRecords.toFloat() / totalDays.coerceAtLeast(1)
        val frequencyText = when {
            frequency >= 0.8f -> "La actividad ha sido muy constante."
            frequency >= 0.5f -> "Se observa un patrón de actividad regular."
            frequency >= 0.2f -> "La actividad ha sido puntual y espaciada."
            else -> "Se han realizado registros de forma ocasional."
        }

        val trendText = when {
            trend > 20 -> "Este periodo muestra un incremento notable de actividad respecto al anterior."
            trend > 5 -> "La actividad ha aumentado ligeramente en comparación con el periodo previo."
            trend < -20 -> "Se observa una disminución significativa de la actividad en este periodo."
            trend < -5 -> "Este periodo muestra menos actividad que el anterior."
            else -> "El ritmo de actividad se mantiene estable frente al periodo anterior."
        }

        return "$frequencyText $trendText"
    }

    private fun calculateTrackingMetrics(
        startDate: LocalDate,
        endDate: LocalDate,
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        medications: List<com.d4vram.pillmate2.model.Medication>,
        selectedMedicationId: String?,
        weeklyPatternHistory: List<com.d4vram.pillmate2.model.HistoryEntry>
    ): TrackingMetrics {
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1

        // Cálculos básicos de seguimiento
        val totalDosesLogged = history.sumOf { it.quantity }
        val distinctDates = history.map { it.timestamp.toLocalDate() }.distinct()
        val totalDaysWithRecords = distinctDates.size
        val averageDailyDoses = if (totalDays > 0) totalDosesLogged.toFloat() / totalDays else 0f
        val averageDosesPerActiveDay = if (totalDaysWithRecords > 0) totalDosesLogged.toFloat() / totalDaysWithRecords else 0f

        // Patrón semanal
        val weeklyActivityPattern = calculateWeeklyActivityPattern(weeklyPatternHistory)

        // Día más y menos activo
        val mostActiveDay = weeklyActivityPattern.maxByOrNull { it.value }?.key ?: ""
        val leastActiveDay = weeklyActivityPattern.minByOrNull { it.value }?.key ?: ""

        // Tendencia
        val previousPeriodStart = startDate.minusDays(totalDays)
        val previousPeriodEnd = startDate.minusDays(1)
        val previousPeriodDoses = history.filter { 
            it.timestamp.toLocalDate() in previousPeriodStart..previousPeriodEnd 
        }.sumOf { it.quantity }

        val loggingTrend = if (previousPeriodDoses > 0) {
            ((totalDosesLogged - previousPeriodDoses).toFloat() / previousPeriodDoses * 100).roundToInt()
        } else 0

        // Tiempo desde el último registro
        val timeSinceLastRecord = if (history.isNotEmpty()) {
            val lastDate = history.maxOf { it.timestamp.toLocalDate() }
            val days = ChronoUnit.DAYS.between(lastDate, LocalDate.now())
            when {
                days == 0L -> "Hoy"
                days == 1L -> "Hace 1 día"
                else -> "Hace $days días"
            }
        } else "Sin registros"

        val monthlyActivityData = calculateMonthlyActivityData(history, selectedMedicationId)

        val loggingConsistency = when {
            totalDaysWithRecords.toFloat() / totalDays.coerceAtLeast(1) >= 0.8f -> "Muy frecuente"
            totalDaysWithRecords.toFloat() / totalDays.coerceAtLeast(1) >= 0.5f -> "Frecuente"
            totalDaysWithRecords.toFloat() / totalDays.coerceAtLeast(1) >= 0.2f -> "Poco frecuente"
            else -> "Espaciado"
        }

        val hourlyPattern = calculateHourlyPattern(history)

        return TrackingMetrics(
            totalDosesLogged = totalDosesLogged,
            averageDailyDoses = averageDailyDoses,
            mostActiveDay = mostActiveDay,
            leastActiveDay = leastActiveDay,
            loggingTrend = loggingTrend,
            weeklyActivityPattern = weeklyActivityPattern,
            monthlyActivityData = monthlyActivityData,
            loggingConsistency = loggingConsistency,
            totalDaysWithRecords = totalDaysWithRecords,
            averageDosesPerActiveDay = averageDosesPerActiveDay,
            timeSinceLastRecord = timeSinceLastRecord,
            hourlyPattern = hourlyPattern
        )
    }


    private fun calculateWeeklyActivityPattern(
        history: List<com.d4vram.pillmate2.model.HistoryEntry>
    ): Map<String, Int> {
        val dayNames = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

        return dayNames.associateWith { dayName ->
            val dayIndex = dayNames.indexOf(dayName) + 1 // DayOfWeek.MONDAY = 1
            val dayEntries = history.filter { it.timestamp.dayOfWeek.value == dayIndex }

            if (dayEntries.isNotEmpty()) {
                // Calcular promedio diario para este día de la semana
                val totalQuantity = dayEntries.sumOf { it.quantity }
                val uniqueDays = dayEntries.map { it.timestamp.toLocalDate() }.distinct().size
                (totalQuantity.toFloat() / uniqueDays).roundToInt()
            } else {
                0
            }
        }
    }

    private fun calculateMonthlyActivityData(
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        selectedMedicationId: String?
    ): List<MonthlyActivityPoint> {
        val now = LocalDate.now()
        val spanishLocale = java.util.Locale.forLanguageTag("es")
        val monthFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", spanishLocale)

        return (0..5).map { monthsBack ->
            val monthStart = now.minusMonths(monthsBack.toLong()).withDayOfMonth(1)
            val monthEnd = monthStart.plusMonths(1).minusDays(1)

            val monthHistory = history.filter { it.timestamp.toLocalDate() in monthStart..monthEnd }

            val totalDoses = monthHistory.sumOf { it.quantity }
            val activeDays = monthHistory.map { it.timestamp.toLocalDate() }.distinct().size
            val daysInMonth = ChronoUnit.DAYS.between(monthStart, monthEnd) + 1
            val averagePerDay = if (daysInMonth > 0) totalDoses.toFloat() / daysInMonth else 0f

            MonthlyActivityPoint(
                month = monthStart.format(monthFormatter),
                totalDoses = totalDoses,
                activeDays = activeDays,
                averagePerDay = averagePerDay
            )
        }.reversed() // Más reciente primero
    }


    private data class TrackingMetrics(
        val totalDosesLogged: Int,
        val averageDailyDoses: Float,
        val mostActiveDay: String,
        val leastActiveDay: String,
        val loggingTrend: Int,
        val weeklyActivityPattern: Map<String, Int>,
        val monthlyActivityData: List<MonthlyActivityPoint>,
        val loggingConsistency: String,
        val totalDaysWithRecords: Int,
        val averageDosesPerActiveDay: Float,
        val timeSinceLastRecord: String,
        val hourlyPattern: Map<String, Int>
    )

    data class MonthlyActivityPoint(
        val month: String,
        val totalDoses: Int,
        val activeDays: Int,
        val averagePerDay: Float
    )

    private fun generateActivityData(
        start: LocalDate,
        end: LocalDate,
        history: List<com.d4vram.pillmate2.model.HistoryEntry>
    ): List<ActivityDataPoint> {
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

            ActivityDataPoint(
                dayName = dayName,
                totalValue = takenCount.toFloat(),
                unit = "dosis",
                fullDate = fullDate,
                hasRecords = takenCount > 0
            )
        }
    }
    private fun calculateHourlyPattern(history: List<com.d4vram.pillmate2.model.HistoryEntry>): Map<String, Int> {
        val hourlyMap = mutableMapOf<String, Int>()
        // Inicializar todas las franjas horarias
        listOf("Madrugada", "Mañana", "Tarde", "Noche").forEach { hourlyMap[it] = 0 }
        
        history.forEach { entry ->
            val hour = entry.timestamp.hour
            val slot = when (hour) {
                in 0..5 -> "Madrugada"
                in 6..11 -> "Mañana"
                in 12..18 -> "Tarde"
                else -> "Noche"
            }
            hourlyMap[slot] = (hourlyMap[slot] ?: 0) + entry.quantity
        }
        return hourlyMap
    }

    private fun formatDate(date: LocalDate): String {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        return when (date) {
            today -> "Hoy"
            yesterday -> "Ayer"
            else -> date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
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
