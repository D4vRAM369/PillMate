package com.d4vram.pillmate2.ui.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Estilos visuales disponibles para el PDF
 */
enum class PdfStyleType {
    CLINICAL,    // Clínico/Formal - aspecto médico profesional
    PILLMATE,    // Consistente con la app - colores y estilo de PillMate
    MINIMAL      // Minimalista - limpio, solo datos esenciales
}

/**
 * Configuración de estilo para el PDF.
 * Define colores, fuentes y dimensiones según el estilo seleccionado.
 */
data class PdfStyle(
    // Colores principales
    val backgroundColor: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accentColor: Int,
    val cardBackground: Int,
    val borderColor: Int,

    // Tipografía
    val titleTypeface: Typeface,
    val bodyTypeface: Typeface,

    // Dimensiones (en puntos)
    val titleSize: Float,
    val subtitleSize: Float,
    val bodySize: Float,
    val smallSize: Float,

    // Márgenes y espaciado
    val pageMargin: Float,
    val sectionSpacing: Float,
    val lineSpacing: Float,

    // Características del estilo
    val showCardBorders: Boolean,
    val useColoredMedications: Boolean,
    val showTableBorders: Boolean
) {
    companion object {
        /**
         * Obtiene la configuración de estilo según el tipo
         */
        fun fromType(type: PdfStyleType): PdfStyle {
            return when (type) {
                PdfStyleType.CLINICAL -> clinicalStyle()
                PdfStyleType.PILLMATE -> pillmateStyle()
                PdfStyleType.MINIMAL -> minimalStyle()
            }
        }

        /**
         * Estilo Clínico/Formal
         * - Fondo blanco, texto negro
         * - Tipografía serif para títulos
         * - Azul institucional para acentos
         * - Tablas con bordes definidos
         */
        private fun clinicalStyle() = PdfStyle(
            backgroundColor = Color.WHITE,
            textPrimary = Color.BLACK,
            textSecondary = Color.parseColor("#4B5563"), // Gris oscuro
            accentColor = Color.parseColor("#1E40AF"),   // Azul institucional
            cardBackground = Color.parseColor("#F9FAFB"), // Gris muy claro
            borderColor = Color.parseColor("#D1D5DB"),   // Gris borde

            titleTypeface = Typeface.SERIF,
            bodyTypeface = Typeface.SANS_SERIF,

            titleSize = 24f,
            subtitleSize = 16f,
            bodySize = 11f,
            smallSize = 9f,

            pageMargin = 40f,
            sectionSpacing = 24f,
            lineSpacing = 6f,

            showCardBorders = true,
            useColoredMedications = false, // Monocromático
            showTableBorders = true
        )

        /**
         * Estilo PillMate (App)
         * - Consistente con la app
         * - Colores de medicamentos preservados
         * - Verde PillMate como acento
         */
        private fun pillmateStyle() = PdfStyle(
            backgroundColor = Color.WHITE,
            textPrimary = Color.parseColor("#1F2937"),   // Gris muy oscuro
            textSecondary = Color.parseColor("#6B7280"), // Gris medio
            accentColor = Color.parseColor("#4ADE80"),   // Verde PillMate
            cardBackground = Color.parseColor("#F3F4F6"), // Gris claro
            borderColor = Color.parseColor("#E5E7EB"),   // Gris borde suave

            titleTypeface = Typeface.SANS_SERIF,
            bodyTypeface = Typeface.SANS_SERIF,

            titleSize = 22f,
            subtitleSize = 14f,
            bodySize = 11f,
            smallSize = 9f,

            pageMargin = 36f,
            sectionSpacing = 20f,
            lineSpacing = 5f,

            showCardBorders = false, // Cards sin borde, solo fondo
            useColoredMedications = true, // Colores de cada medicamento
            showTableBorders = false
        )

        /**
         * Estilo Minimalista
         * - Fondo blanco puro, sin decoración
         * - Tipografía fina
         * - Espaciado generoso
         */
        private fun minimalStyle() = PdfStyle(
            backgroundColor = Color.WHITE,
            textPrimary = Color.BLACK,
            textSecondary = Color.parseColor("#6B7280"),
            accentColor = Color.parseColor("#374151"),   // Gris oscuro
            cardBackground = Color.WHITE,               // Sin cards visibles
            borderColor = Color.parseColor("#E5E7EB"),

            titleTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),
            bodyTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL),

            titleSize = 20f,
            subtitleSize = 13f,
            bodySize = 10f,
            smallSize = 8f,

            pageMargin = 48f,
            sectionSpacing = 28f,
            lineSpacing = 8f,

            showCardBorders = false,
            useColoredMedications = false,
            showTableBorders = false
        )
    }

    /**
     * Crea un Paint configurado para títulos
     */
    fun titlePaint(): Paint = Paint().apply {
        color = textPrimary
        textSize = titleSize
        typeface = Typeface.create(titleTypeface, Typeface.BOLD)
        isAntiAlias = true
    }

    /**
     * Crea un Paint configurado para subtítulos
     */
    fun subtitlePaint(): Paint = Paint().apply {
        color = textPrimary
        textSize = subtitleSize
        typeface = Typeface.create(titleTypeface, Typeface.BOLD)
        isAntiAlias = true
    }

    /**
     * Crea un Paint configurado para texto normal
     */
    fun bodyPaint(): Paint = Paint().apply {
        color = textPrimary
        textSize = bodySize
        typeface = bodyTypeface
        isAntiAlias = true
    }

    /**
     * Crea un Paint configurado para texto secundario
     */
    fun secondaryPaint(): Paint = Paint().apply {
        color = textSecondary
        textSize = bodySize
        typeface = bodyTypeface
        isAntiAlias = true
    }

    /**
     * Crea un Paint configurado para texto pequeño
     */
    fun smallPaint(): Paint = Paint().apply {
        color = textSecondary
        textSize = smallSize
        typeface = bodyTypeface
        isAntiAlias = true
    }

    /**
     * Crea un Paint configurado para acentos
     */
    fun accentPaint(): Paint = Paint().apply {
        color = accentColor
        textSize = bodySize
        typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
        isAntiAlias = true
    }
}

/**
 * Secciones disponibles para incluir en el PDF
 */
enum class PdfSection(val displayName: String, val defaultSelected: Boolean) {
    SUMMARY("Resumen general", true),
    MEDICATIONS("Lista de medicamentos", true),
    WEEKLY_CHART("Gráfica semanal", true),
    HISTORY("Historial de tomas", false),
    HOURLY_DISTRIBUTION("Distribución horaria", false),
    DOSE_BREAKDOWN("Desglose por dosis", false),
    MONTHLY_TRENDS("Tendencias mensuales", false),
    VOICE_NOTES("Notas de voz", false)
}

/**
 * Configuración de exportación seleccionada por el usuario
 */
data class PdfExportConfig(
    val startDate: java.time.LocalDate,
    val endDate: java.time.LocalDate,
    val sections: Set<PdfSection>,
    val style: PdfStyleType
) {
    companion object {
        fun default(
            startDate: java.time.LocalDate = java.time.LocalDate.now().minusDays(6),
            endDate: java.time.LocalDate = java.time.LocalDate.now()
        ) = PdfExportConfig(
            startDate = startDate,
            endDate = endDate,
            sections = PdfSection.entries.filter { it.defaultSelected }.toSet(),
            style = PdfStyleType.PILLMATE
        )
    }
}
