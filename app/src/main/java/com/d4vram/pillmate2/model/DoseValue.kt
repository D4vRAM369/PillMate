package com.d4vram.pillmate2.model

/**
 * Sistema de unidades para dosis de medicamentos
 * Soporta conversiones precisas entre diferentes unidades métricas
 */
enum class DoseUnit(val displayName: String, val abbreviation: String, val category: UnitCategory) {
    // Unidades de masa
    MG("miligramos", "mg", UnitCategory.MASS),
    G("gramos", "g", UnitCategory.MASS),
    MCG("microgramos", "mcg", UnitCategory.MASS),

    // Unidades de volumen
    ML("mililitros", "ml", UnitCategory.VOLUME),
    L("litros", "l", UnitCategory.VOLUME),
    UL("microlitros", "µl", UnitCategory.VOLUME),

    // Unidades específicas de medicamentos
    IU("unidades internacionales", "IU", UnitCategory.SPECIFIC),
    DROPS("gotas", "gotas", UnitCategory.SPECIFIC),
    TABLETS("tabletas", "tabs", UnitCategory.SPECIFIC),
    CAPSULES("cápsulas", "caps", UnitCategory.SPECIFIC),

    // Unidades genéricas
    UNITS("unidades", "u", UnitCategory.GENERIC),
    DOSES("dosis", "dosis", UnitCategory.GENERIC);

    enum class UnitCategory {
        MASS, VOLUME, SPECIFIC, GENERIC
    }

    companion object {
        fun fromString(unitStr: String): DoseUnit {
            return when (unitStr.lowercase().trim()) {
                "mg", "miligramos", "milligrams" -> MG
                "g", "gramos", "grams" -> G
                "mcg", "µg", "microgramos", "micrograms" -> MCG
                "ml", "mililitros", "milliliters" -> ML
                "l", "litros", "liters" -> L
                "µl", "microlitros", "microliters" -> UL
                "iu", "unidades internacionales", "international units" -> IU
                "gotas", "drops", "gota" -> DROPS
                "tabletas", "tabs", "tablets", "pastillas" -> TABLETS
                "cápsulas", "caps", "capsules" -> CAPSULES
                "unidades", "units", "u" -> UNITS
                "dosis", "doses" -> DOSES
                else -> UNITS // fallback
            }
        }

        fun parseDose(doseString: String): Pair<Float, DoseUnit>? {
            val regex = """^(\d+(?:\.\d+)?)\s*([a-zA-Zµ]+)$""".toRegex()
            val match = regex.find(doseString.trim())

            return match?.let { m ->
                val (value, unit) = m.destructured
                val numericValue = value.toFloatOrNull() ?: return null
                val doseUnit = fromString(unit)
                Pair(numericValue, doseUnit)
            }
        }
    }
}

/**
 * Representa una dosis con valor y unidad
 */
data class DoseValue(
    val value: Float,
    val unit: DoseUnit
) {
    val displayValue: String
        get() = "${value.formatDecimal()} ${unit.abbreviation}"

    val fullDisplayValue: String
        get() = "${value.formatDecimal()} ${unit.displayName}"

    /**
     * Convierte la dosis a un valor numérico comparable (para estadísticas)
     * Normaliza diferentes unidades a una escala común
     */
    val normalizedValue: Float
        get() = when (unit) {
            DoseUnit.MG -> value
            DoseUnit.G -> value * 1000 // g -> mg
            DoseUnit.MCG -> value / 1000 // mcg -> mg
            DoseUnit.ML -> value * 1000 // ml -> µl
            DoseUnit.L -> value * 1000000 // l -> µl
            DoseUnit.UL -> value
            DoseUnit.IU -> value // mantener como está
            DoseUnit.DROPS -> value // gotas se mantienen como unidades
            DoseUnit.TABLETS -> value
            DoseUnit.CAPSULES -> value
            DoseUnit.UNITS -> value
            DoseUnit.DOSES -> value
        }

    /**
     * Categoría de la unidad para agrupar en estadísticas
     */
    val category: DoseUnit.UnitCategory
        get() = unit.category

    companion object {
        fun parse(doseString: String): DoseValue? {
            return DoseUnit.parseDose(doseString)?.let { (value, unit) ->
                DoseValue(value, unit)
            }
        }

        fun zero(unit: DoseUnit = DoseUnit.UNITS) = DoseValue(0f, unit)
    }
}

/**
 * Extensión para formatear números decimales
 */
private fun Float.formatDecimal(): String {
    return if (this % 1 == 0f) {
        this.toInt().toString()
    } else {
        String.format("%.1f", this)
    }
}