package com.d4vram.pillmate2.utils

import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.model.MedicationType

internal data class MedicationAmount(
    val value: Float,
    val unit: String
)

internal data class DailyDoseMetrics(
    val takeCount: Int,
    val amount: Float
)

internal fun countTakeEvents(entries: Collection<HistoryEntry>): Int = entries.size

internal fun averageTakeEvents(entries: Collection<HistoryEntry>, days: Long): Float =
    if (days > 0) countTakeEvents(entries).toFloat() / days else 0f

internal fun amountForMedication(
    medication: Medication,
    entries: List<HistoryEntry>
): MedicationAmount {
    val quantity = entries
        .asSequence()
        .filter { it.medicationId == medication.id }
        .sumOf { it.quantity }
        .toFloat()

    return when (MedicationType.fromStored(medication.type)) {
        MedicationType.TABLET, MedicationType.PILL, MedicationType.CAPSULE ->
            MedicationAmount(quantity * medicationStrength(medication.dose), "mg")
        MedicationType.DROPS -> MedicationAmount(quantity, "gotas")
        MedicationType.SYRUP -> MedicationAmount(quantity, "ml")
        null -> MedicationAmount(quantity, "units")
    }
}

internal fun amountsByMedication(
    entries: List<HistoryEntry>,
    medications: List<Medication>
): Map<String, MedicationAmount> {
    val entriesByMedication = entries.groupBy { it.medicationId }
    return medications.associate { medication ->
        medication.id to amountForMedication(
            medication = medication,
            entries = entriesByMedication[medication.id].orEmpty()
        )
    }
}

internal fun dailyDoseMetrics(
    entries: List<HistoryEntry>,
    medicationsById: Map<String, Medication>,
    unit: String
): DailyDoseMetrics {
    val amount = when (unit) {
        "mg" -> entries.sumOf { entry ->
            val strength = medicationsById[entry.medicationId]
                ?.let { medicationStrength(it.dose) }
                ?: 1f
            entry.quantity * strength.toDouble()
        }.toFloat()
        "gotas", "ml", "units" -> entries.sumOf { it.quantity }.toFloat()
        else -> countTakeEvents(entries).toFloat()
    }
    return DailyDoseMetrics(
        takeCount = countTakeEvents(entries),
        amount = amount
    )
}

private fun medicationStrength(dose: String): Float {
    val normalized = dose.replace(',', '.')
    val number = Regex("([0-9]+(?:\\.[0-9]+)?)").find(normalized)
    return number?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
}
