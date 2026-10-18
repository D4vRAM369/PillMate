package com.d4vram.pillmate2.ui.export

import com.d4vram.pillmate2.utils.formatQuantity
import androidx.annotation.StringRes
import com.d4vram.pillmate2.R
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.min

/**
 * Datos necesarios para generar el PDF.
 * Se obtienen del repositorio antes de iniciar la generación.
 */
data class PdfReportData(
    val medications: List<Medication>,
    val history: List<HistoryEntry>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    // Métricas calculadas
    val totalValue: Float,
    val displayUnit: String,
    val averageDaily: Float,
    val daysWithRecords: Int,
    val totalDays: Int,
    // Métricas de tomas (más útiles clínicamente)
    val totalTomas: Int,
    val averageDailyTomas: Float,
    // Datos por medicamento
    val medicationTotals: List<MedicationTotal>,
    // Datos para gráficas
    val dailyData: List<DailyDataPoint>,
    val hourlyPattern: Map<String, Int>
)

data class MedicationTotal(
    val medication: Medication,
    val totalValue: Float,
    val displayUnit: String,
    val percentage: Float
)

data class DailyDataPoint(
    val date: LocalDate,
    val value: Float
)

/**
 * Generador de reportes PDF profesional.
 *
 * Características:
 * - Paginación automática para contenido largo
 * - Layout basado en medición real de texto
 * - Todas las secciones implementadas
 * - Soporte para múltiples estilos
 */
class PdfGenerator(private val context: Context) {

    // El PDF se escribe en el idioma del móvil. Aquí no hay pantalla: los textos se piden al context.
    private fun t(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)
    private fun doses(count: Int): String = context.resources.getQuantityString(R.plurals.doses_count, count, count)

    /** Las unidades y las franjas viajan como claves en español porque la lógica las compara; aquí se traducen. */
    private fun unitText(unit: String): String = when (unit) {
        "tomas" -> t(R.string.clock_doses_label)
        "gotas" -> t(R.string.unit_drops)
        "units" -> t(R.string.unit_units)
        else -> unit
    }
    private fun slotLabel(key: String): String = when (key) {
        "Madrugada" -> t(R.string.pdf_slot_early)
        "Mañana" -> t(R.string.pdf_slot_morning)
        "Mediodía" -> t(R.string.pdf_slot_midday)
        "Tarde" -> t(R.string.pdf_slot_afternoon)
        "Noche" -> t(R.string.pdf_slot_evening)
        "Nocturno" -> t(R.string.pdf_slot_night)
        else -> key
    }

    // ===========================================
    // CONSTANTES DE PÁGINA
    // ===========================================

    // Dimensiones de página A4 en puntos (72 puntos = 1 pulgada)
    private val pageWidth = 595  // ~210mm
    private val pageHeight = 842 // ~297mm

    // Área útil de contenido (descontando márgenes)
    private fun contentWidth(style: PdfStyle) = pageWidth - (style.pageMargin * 2)
    private fun contentBottom(style: PdfStyle) = pageHeight - style.pageMargin

    // ===========================================
    // ESTADO DE PAGINACIÓN
    // ===========================================

    private var currentPageNumber = 1
    private lateinit var document: PdfDocument
    private lateinit var currentPage: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var yPosition = 0f
    private lateinit var style: PdfStyle

    // ===========================================
    // FORMATEADORES
    // ===========================================

    private val dateFormatter = com.d4vram.pillmate2.utils.localizedDateFormatter("ddMMyyyy", java.util.Locale.getDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private fun formatDateTime(value: LocalDateTime): String = "${value.format(dateFormatter)} ${value.format(timeFormatter)}"

    /**
     * Genera el PDF con la configuración especificada.
     * @return File del PDF generado, o null si falla
     */
    fun generate(
        config: PdfExportConfig,
        data: PdfReportData
    ): File? {
        style = PdfStyle.fromType(config.style)
        document = PdfDocument()
        currentPageNumber = 1

        try {
            // Crear primera página
            startNewPage()

            // === PORTADA ===
            drawHeader(data)
            yPosition += style.sectionSpacing

            // === SECCIONES SELECCIONADAS ===

            // 1. Resumen general
            if (PdfSection.SUMMARY in config.sections) {
                ensureSpace(120f)
                drawSummarySection(data)
                yPosition += style.sectionSpacing
            }

            // 2. Lista de medicamentos
            if (PdfSection.MEDICATIONS in config.sections) {
                ensureSpace(100f)
                drawMedicationsSection(data)
                yPosition += style.sectionSpacing
            }

            // 3. Gráfica semanal
            if (PdfSection.WEEKLY_CHART in config.sections) {
                ensureSpace(180f)
                drawWeeklyChartSection(data)
                yPosition += style.sectionSpacing
            }

            // 4. Historial de tomas (con paginación)
            if (PdfSection.HISTORY in config.sections) {
                ensureSpace(100f)
                drawHistorySection(data)
                yPosition += style.sectionSpacing
            }

            // 5. Distribución horaria
            if (PdfSection.HOURLY_DISTRIBUTION in config.sections) {
                ensureSpace(180f)
                drawHourlyDistributionSection(data)
                yPosition += style.sectionSpacing
            }

            // 6. Desglose por dosis
            if (PdfSection.DOSE_BREAKDOWN in config.sections) {
                ensureSpace(150f)
                drawDoseBreakdownSection(data)
                yPosition += style.sectionSpacing
            }

            // 7. Tendencias mensuales
            if (PdfSection.MONTHLY_TRENDS in config.sections) {
                ensureSpace(150f)
                drawMonthlyTrendsSection(data)
                yPosition += style.sectionSpacing
            }

            // 8. Notas de voz
            if (PdfSection.VOICE_NOTES in config.sections) {
                ensureSpace(100f)
                drawVoiceNotesSection(data)
            }

            // Finalizar última página
            document.finishPage(currentPage)

            // Guardar archivo
            val fileName = generateFileName(data.startDate, data.endDate)
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { outputStream ->
                document.writeTo(outputStream)
            }

            return file

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            document.close()
        }
    }

    // ===========================================
    // GESTIÓN DE PÁGINAS
    // ===========================================

    /**
     * Inicia una nueva página en el documento
     */
    private fun startNewPage() {
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        currentPage = document.startPage(pageInfo)
        canvas = currentPage.canvas
        canvas.drawColor(style.backgroundColor)
        yPosition = style.pageMargin
        currentPageNumber++
    }

    /**
     * Verifica si hay espacio suficiente; si no, crea nueva página
     */
    private fun ensureSpace(neededHeight: Float) {
        if (yPosition + neededHeight > contentBottom(style)) {
            document.finishPage(currentPage)
            startNewPage()
        }
    }

    // ===========================================
    // PAINTS DEDICADOS (sin mutación)
    // ===========================================

    private fun titlePaint(): Paint {
        val s = style
        return Paint().apply {
            color = s.textPrimary
            textSize = s.titleSize
            typeface = Typeface.create(s.titleTypeface, Typeface.BOLD)
            isAntiAlias = true
        }
    }

    private fun subtitlePaint(): Paint {
        val s = style
        return Paint().apply {
            color = s.textPrimary
            textSize = s.subtitleSize
            typeface = Typeface.create(s.titleTypeface, Typeface.BOLD)
            isAntiAlias = true
        }
    }

    private fun bodyPaint(): Paint {
        val s = style
        return Paint().apply {
            color = s.textPrimary
            textSize = s.bodySize
            typeface = s.bodyTypeface
            isAntiAlias = true
        }
    }

    private fun bodyBoldPaint(): Paint {
        val s = style
        return Paint().apply {
            color = s.textPrimary
            textSize = s.bodySize
            typeface = Typeface.create(s.bodyTypeface, Typeface.BOLD)
            isAntiAlias = true
        }
    }

    private fun secondaryPaint(): Paint {
        val s = style
        return Paint().apply {
            color = s.textSecondary
            textSize = s.bodySize
            typeface = s.bodyTypeface
            isAntiAlias = true
        }
    }

    private fun smallPaint(): Paint {
        val s = style
        return Paint().apply {
            color = s.textSecondary
            textSize = s.smallSize
            typeface = s.bodyTypeface
            isAntiAlias = true
        }
    }

    private fun accentPaint(): Paint {
        val s = style
        return Paint().apply {
            color = s.accentColor
            textSize = s.bodySize
            typeface = Typeface.create(s.bodyTypeface, Typeface.BOLD)
            isAntiAlias = true
        }
    }

    // ===========================================
    // SECCIÓN: CABECERA
    // ===========================================

    private fun drawHeader(data: PdfReportData) {
        val s = style
        val centerX = pageWidth / 2f

        // Título principal
        val title = titlePaint().apply { textAlign = Paint.Align.CENTER }
        canvas.drawText("PillMate", centerX, yPosition + s.titleSize, title)
        yPosition += s.titleSize + s.lineSpacing

        // Subtítulo
        val subtitle = subtitlePaint().apply {
            textAlign = Paint.Align.CENTER
            color = s.textSecondary
        }
        canvas.drawText(t(R.string.pdf_doc_title), centerX, yPosition + s.subtitleSize, subtitle)
        yPosition += s.subtitleSize + s.sectionSpacing

        // Periodo
        val periodText = t(R.string.pdf_doc_period, data.startDate.format(dateFormatter), data.endDate.format(dateFormatter))
        val secondary = secondaryPaint().apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(periodText, centerX, yPosition + s.bodySize, secondary)
        yPosition += s.bodySize + s.lineSpacing

        // Fecha de generación
        val generatedText = t(R.string.pdf_doc_generated, formatDateTime(LocalDateTime.now()))
        val small = smallPaint().apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(generatedText, centerX, yPosition + s.smallSize, small)
        yPosition += s.smallSize + s.lineSpacing

        // Línea separadora
        yPosition += 10f
        val linePaint = Paint().apply {
            color = s.borderColor
            strokeWidth = 1f
        }
        canvas.drawLine(s.pageMargin, yPosition, pageWidth - s.pageMargin, yPosition, linePaint)
        yPosition += 10f
    }

    // ===========================================
    // SECCIÓN: RESUMEN
    // ===========================================

    private fun drawSummarySection(data: PdfReportData) {
        val leftMargin = style.pageMargin
        val rightMargin = pageWidth - style.pageMargin

        // Título
        canvas.drawText(t(R.string.pdf_doc_summary), leftMargin, yPosition + style.subtitleSize, subtitlePaint())
        yPosition += style.subtitleSize + style.lineSpacing * 2

        val cardTop = yPosition
        val cardPadding = 12f

        val items = listOf(
            t(R.string.pdf_doc_total_label) to doses(data.totalTomas),
            t(R.string.pdf_doc_avg_label) to t(R.string.pdf_doc_avg_value, formatValue(data.averageDailyTomas)),
            t(R.string.pdf_doc_days_label) to t(R.string.pdf_doc_days_value, data.daysWithRecords, data.totalDays),
            t(R.string.pdf_doc_active_label) to "${data.medications.size}"
        )

        yPosition += cardPadding
        val body = bodyPaint()
        val accent = accentPaint().apply { textAlign = Paint.Align.RIGHT }

        items.forEach { (label, value) ->
            canvas.drawText(label, leftMargin + cardPadding, yPosition + style.bodySize, body)
            canvas.drawText(value, rightMargin - cardPadding, yPosition + style.bodySize, accent)
            yPosition += style.bodySize + style.lineSpacing
        }
        yPosition += cardPadding

        // Borde del card
        if (style.showCardBorders) {
            val cardRect = RectF(leftMargin, cardTop, rightMargin, yPosition)
            val s = style
            val borderPaint = Paint().apply {
                color = s.borderColor
                this.style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(cardRect, 4f, 4f, borderPaint)
        }
    }

    // ===========================================
    // SECCIÓN: MEDICAMENTOS
    // ===========================================

    private fun drawMedicationsSection(data: PdfReportData) {
        val leftMargin = style.pageMargin
        val rightMargin = pageWidth - style.pageMargin

        // Título
        canvas.drawText(t(R.string.pdf_doc_medications), leftMargin, yPosition + style.subtitleSize, subtitlePaint())
        yPosition += style.subtitleSize + style.lineSpacing * 2

        val body = bodyPaint()
        val secondary = secondaryPaint().apply { textAlign = Paint.Align.RIGHT }

        // Ancho para nombre: dejamos 130pt para el valor a la derecha
        val nameStartX = if (style.useColoredMedications) leftMargin + 18f else leftMargin
        val maxNameWidth = rightMargin - nameStartX - 130f

        data.medicationTotals.forEach { medTotal ->
            ensureSpace(style.bodySize + style.lineSpacing)

            // Indicador de color
            if (style.useColoredMedications) {
                val colorPaint = Paint().apply {
                    color = medTotal.medication.color.toArgb()
                }
                canvas.drawCircle(leftMargin + 6f, yPosition + style.bodySize / 2, 4f, colorPaint)
            }

            // Nombre truncado
            val fullName = "${medTotal.medication.name} ${medTotal.medication.dose}"
            val truncatedName = truncateText(fullName, maxNameWidth, body)
            canvas.drawText(truncatedName, nameStartX, yPosition + style.bodySize, body)

            // Valor y porcentaje
            val valueText = "${formatValue(medTotal.totalValue)} ${unitText(medTotal.displayUnit)} (${(medTotal.percentage * 100).toInt()}%)"
            canvas.drawText(valueText, rightMargin, yPosition + style.bodySize, secondary)

            yPosition += style.bodySize + style.lineSpacing
        }
    }

    // ===========================================
    // SECCIÓN: GRÁFICA SEMANAL
    // ===========================================

    private fun drawWeeklyChartSection(data: PdfReportData) {
        val leftMargin = style.pageMargin
        val rightMargin = pageWidth - style.pageMargin

        // Título
        canvas.drawText(t(R.string.pdf_doc_weekly), leftMargin, yPosition + style.subtitleSize, subtitlePaint())
        yPosition += style.subtitleSize + style.lineSpacing * 2

        // Agrupar datos por semana
        val weeklyData = data.dailyData.groupBy { entry ->
            // Obtener el lunes de la semana
            entry.date.minusDays(entry.date.dayOfWeek.value.toLong() - 1)
        }.mapValues { (_, days) -> days.sumOf { it.value.toDouble() }.toFloat() }
            .toList()
            .sortedBy { it.first }
            .takeLast(8) // Últimas 8 semanas

        if (weeklyData.isEmpty()) {
            val noData = secondaryPaint()
            canvas.drawText(t(R.string.pdf_doc_weekly_empty), leftMargin, yPosition + style.bodySize, noData)
            yPosition += style.bodySize
            return
        }

        val maxValue = weeklyData.maxOfOrNull { it.second } ?: 1f
        val chartHeight = 120f
        val barWidth = (contentWidth(style) - 40f) / weeklyData.size
        val chartTop = yPosition
        val chartBottom = yPosition + chartHeight

        val s = style
        val barPaint = Paint().apply { color = s.accentColor }
        val labelPaint = smallPaint().apply { textAlign = Paint.Align.CENTER }

        weeklyData.forEachIndexed { index, (weekStart, value) ->
            val barX = leftMargin + 20f + (index * barWidth)
            val barHeight = if (maxValue > 0) (value / maxValue) * (chartHeight - 20f) else 0f

            // Barra
            if (barHeight > 0) {
                canvas.drawRoundRect(
                    RectF(barX + 4f, chartBottom - barHeight - 15f, barX + barWidth - 4f, chartBottom - 15f),
                    4f, 4f, barPaint
                )
            }

            // Etiqueta de semana (dd/MM)
            val label = weekStart.format(com.d4vram.pillmate2.utils.localizedDateFormatter("ddMM", java.util.Locale.getDefault()))
            canvas.drawText(label, barX + barWidth / 2, chartBottom, labelPaint)
        }

        yPosition = chartBottom + 10f
    }

    // ===========================================
    // SECCIÓN: HISTORIAL (con paginación)
    // ===========================================

    private fun drawHistorySection(data: PdfReportData) {
        val leftMargin = style.pageMargin
        val rightMargin = pageWidth - style.pageMargin

        // Título
        canvas.drawText(t(R.string.pdf_doc_history), leftMargin, yPosition + style.subtitleSize, subtitlePaint())
        yPosition += style.subtitleSize + style.lineSpacing * 2

        // === DEFINIR COLUMNAS CON ANCHOS FIJOS ===
        // Medimos el texto más largo esperado para cada columna
        val body = bodyPaint()
        val header = bodyBoldPaint()

        // Anchos calculados basados en contenido esperado
        val dateWidth = body.measureText("00/00/0000") + 10f      // ~72pt
        val timeWidth = body.measureText("00:00") + 10f           // ~35pt
        val doseWidth = body.measureText("10x 500mg") + 10f       // ~65pt
        val medWidth = rightMargin - leftMargin - dateWidth - timeWidth - doseWidth - 20f // resto

        // Posiciones de columnas
        val col1 = leftMargin                    // Fecha
        val col2 = col1 + dateWidth + 5f         // Hora
        val col3 = col2 + timeWidth + 5f         // Medicamento
        val col4 = rightMargin                   // Dosis (alineado derecha)

        // Cabecera de tabla
        if (style.showTableBorders) {
            drawTableLine(leftMargin, rightMargin)
        }
        yPosition += 4f

        canvas.drawText(t(R.string.entry_date), col1, yPosition + style.bodySize, header)
        canvas.drawText(t(R.string.entry_time), col2, yPosition + style.bodySize, header)
        canvas.drawText(t(R.string.stats_medication_fallback), col3, yPosition + style.bodySize, header)
        canvas.drawText(t(R.string.med_form_dose), col4, yPosition + style.bodySize, header.apply { textAlign = Paint.Align.RIGHT })
        yPosition += style.bodySize + style.lineSpacing

        if (style.showTableBorders) {
            drawTableLine(leftMargin, rightMargin)
        }
        yPosition += 4f

        // Todas las filas con paginación
        val rowHeight = style.bodySize + style.lineSpacing

        data.history.forEach { entry ->
            // Verificar espacio y paginar si es necesario
            if (yPosition + rowHeight > contentBottom(style) - 20f) {
                document.finishPage(currentPage)
                startNewPage()

                // Repetir cabecera en nueva página
                canvas.drawText(t(R.string.pdf_doc_history_cont), leftMargin, yPosition + style.subtitleSize, subtitlePaint())
                yPosition += style.subtitleSize + style.lineSpacing

                if (style.showTableBorders) {
                    drawTableLine(leftMargin, rightMargin)
                }
                yPosition += 4f

                canvas.drawText(t(R.string.entry_date), col1, yPosition + style.bodySize, bodyBoldPaint())
                canvas.drawText(t(R.string.entry_time), col2, yPosition + style.bodySize, bodyBoldPaint())
                canvas.drawText(t(R.string.stats_medication_fallback), col3, yPosition + style.bodySize, bodyBoldPaint())
                val headerRight = bodyBoldPaint().apply { textAlign = Paint.Align.RIGHT }
                canvas.drawText(t(R.string.med_form_dose), col4, yPosition + style.bodySize, headerRight)
                yPosition += style.bodySize + style.lineSpacing

                if (style.showTableBorders) {
                    drawTableLine(leftMargin, rightMargin)
                }
                yPosition += 4f
            }

            // Dibujar fila
            val formattedDate = entry.timestamp.format(dateFormatter)
            val formattedTime = entry.timestamp.format(timeFormatter)
            val truncatedMed = truncateText(entry.medicationName, medWidth - 10f, body)

            val med = data.medications.find { it.id == entry.medicationId }
            val doseText = if (med != null) {
                "${formatQuantity(entry.quantity)}x ${med.dose}"
            } else {
                "${formatQuantity(entry.quantity)}x"
            }

            val bodyLeft = bodyPaint()
            val bodyRight = bodyPaint().apply { textAlign = Paint.Align.RIGHT }

            canvas.drawText(formattedDate, col1, yPosition + style.bodySize, bodyLeft)
            canvas.drawText(formattedTime, col2, yPosition + style.bodySize, bodyLeft)
            canvas.drawText(truncatedMed, col3, yPosition + style.bodySize, bodyLeft)
            canvas.drawText(doseText, col4, yPosition + style.bodySize, bodyRight)

            yPosition += rowHeight
        }
    }

    private fun drawTableLine(left: Float, right: Float) {
        val s = style
        val linePaint = Paint().apply {
            color = s.borderColor
            strokeWidth = 0.5f
        }
        canvas.drawLine(left, yPosition, right, yPosition, linePaint)
    }

    // ===========================================
    // SECCIÓN: DISTRIBUCIÓN HORARIA
    // ===========================================

    private fun drawHourlyDistributionSection(data: PdfReportData) {
        val leftMargin = style.pageMargin
        val rightMargin = pageWidth - style.pageMargin

        // Título
        canvas.drawText(t(R.string.pdf_doc_hourly), leftMargin, yPosition + style.subtitleSize, subtitlePaint())
        yPosition += style.subtitleSize + style.lineSpacing * 2

        val slots = listOf(
            "Madrugada" to "00:00 - 06:00",
            "Mañana" to "06:00 - 12:00",
            "Mediodía" to "12:00 - 15:00",
            "Tarde" to "15:00 - 19:00",
            "Noche" to "19:00 - 23:00",
            "Nocturno" to "23:00 - 00:00"
        )

        val maxValue = data.hourlyPattern.values.maxOrNull()?.toFloat() ?: 1f
        val barMaxWidth = contentWidth(style) - 160f

        val body = bodyPaint()
        val small = smallPaint()
        val s = style
        val barPaint = Paint().apply { color = s.accentColor }
        val valueRight = secondaryPaint().apply { textAlign = Paint.Align.RIGHT }

        slots.forEach { (name, range) ->
            val value = data.hourlyPattern[name] ?: 0
            val barWidth = if (maxValue > 0) (value / maxValue) * barMaxWidth else 0f

            // Nombre del slot
            canvas.drawText(slotLabel(name), leftMargin, yPosition + style.bodySize, body)

            // Rango horario
            canvas.drawText(range, leftMargin + 80f, yPosition + style.bodySize, small)

            // Barra
            val barLeft = leftMargin + 150f
            val barTop = yPosition + 2f
            val barBottom = yPosition + style.bodySize - 2f
            if (barWidth > 0) {
                canvas.drawRoundRect(
                    RectF(barLeft, barTop, barLeft + barWidth, barBottom),
                    3f, 3f, barPaint
                )
            }

            // Valor
            canvas.drawText(value.toString(), rightMargin, yPosition + style.bodySize, valueRight)

            yPosition += style.bodySize + style.lineSpacing + 4f
        }
    }

    // ===========================================
    // SECCIÓN: DESGLOSE POR DOSIS
    // ===========================================

    private fun drawDoseBreakdownSection(data: PdfReportData) {
        val leftMargin = style.pageMargin
        val rightMargin = pageWidth - style.pageMargin

        // Título
        canvas.drawText(t(R.string.pdf_doc_breakdown), leftMargin, yPosition + style.subtitleSize, subtitlePaint())
        yPosition += style.subtitleSize + style.lineSpacing * 2

        // Agrupar por medicamento y dosis
        val doseBreakdown = data.history
            .groupBy { "${it.medicationName}|${it.dose ?: "N/A"}" }
            .map { (key, entries) ->
                val parts = key.split("|")
                Triple(parts[0], parts[1], entries.sumOf { it.quantity })
            }
            .sortedByDescending { it.third }

        if (doseBreakdown.isEmpty()) {
            val noData = secondaryPaint()
            canvas.drawText(t(R.string.pdf_doc_breakdown_empty), leftMargin, yPosition + style.bodySize, noData)
            yPosition += style.bodySize
            return
        }

        val body = bodyPaint()
        val valueRight = secondaryPaint().apply { textAlign = Paint.Align.RIGHT }
        val maxNameWidth = contentWidth(style) - 100f

        doseBreakdown.take(20).forEach { (medName, dose, count) ->
            ensureSpace(style.bodySize + style.lineSpacing)

            val displayName = truncateText("$medName ($dose)", maxNameWidth, body)
            canvas.drawText(displayName, leftMargin, yPosition + style.bodySize, body)
            canvas.drawText(context.resources.getQuantityString(R.plurals.clock_units, if (count == 1.0) 1 else 2, formatQuantity(count)), rightMargin, yPosition + style.bodySize, valueRight)

            yPosition += style.bodySize + style.lineSpacing
        }

        if (doseBreakdown.size > 20) {
            val more = smallPaint()
            canvas.drawText(t(R.string.pdf_doc_and_more, doseBreakdown.size - 20), leftMargin, yPosition + style.smallSize, more)
            yPosition += style.smallSize
        }
    }

    // ===========================================
    // SECCIÓN: TENDENCIAS MENSUALES
    // ===========================================

    private fun drawMonthlyTrendsSection(data: PdfReportData) {
        val leftMargin = style.pageMargin
        val rightMargin = pageWidth - style.pageMargin

        // Título
        canvas.drawText(t(R.string.pdf_doc_monthly), leftMargin, yPosition + style.subtitleSize, subtitlePaint())
        yPosition += style.subtitleSize + style.lineSpacing * 2

        // Agrupar por mes
        val monthlyData = data.dailyData
            .groupBy { it.date.withDayOfMonth(1) }
            .mapValues { (_, days) -> days.sumOf { it.value.toDouble() }.toFloat() }
            .toList()
            .sortedBy { it.first }

        if (monthlyData.isEmpty()) {
            val noData = secondaryPaint()
            canvas.drawText(t(R.string.pdf_doc_monthly_empty), leftMargin, yPosition + style.bodySize, noData)
            yPosition += style.bodySize
            return
        }

        val maxValue = monthlyData.maxOfOrNull { it.second } ?: 1f
        val barMaxWidth = contentWidth(style) - 120f

        val body = bodyPaint()
        val s = style
        val barPaint = Paint().apply { color = s.accentColor }
        val valueRight = secondaryPaint().apply { textAlign = Paint.Align.RIGHT }

        monthlyData.takeLast(12).forEach { (month, value) ->
            ensureSpace(style.bodySize + style.lineSpacing + 4f)

            val barWidth = if (maxValue > 0) (value / maxValue) * barMaxWidth else 0f

            // Mes
            val monthLabel = month.format(DateTimeFormatter.ofPattern("MMM yyyy"))
            canvas.drawText(monthLabel, leftMargin, yPosition + style.bodySize, body)

            // Barra
            val barLeft = leftMargin + 80f
            val barTop = yPosition + 2f
            val barBottom = yPosition + style.bodySize - 2f
            if (barWidth > 0) {
                canvas.drawRoundRect(
                    RectF(barLeft, barTop, barLeft + barWidth, barBottom),
                    3f, 3f, barPaint
                )
            }

            // Valor
            canvas.drawText(formatValue(value), rightMargin, yPosition + style.bodySize, valueRight)

            yPosition += style.bodySize + style.lineSpacing + 4f
        }
    }

    // ===========================================
    // SECCIÓN: NOTAS DE VOZ
    // ===========================================

    private fun drawVoiceNotesSection(data: PdfReportData) {
        val leftMargin = style.pageMargin

        // Título
        canvas.drawText(t(R.string.pdf_doc_voice), leftMargin, yPosition + style.subtitleSize, subtitlePaint())
        yPosition += style.subtitleSize + style.lineSpacing * 2

        // Filtrar entradas con notas de voz
        val entriesWithVoice = data.history.filter { !it.audioNoteUri.isNullOrBlank() }

        if (entriesWithVoice.isEmpty()) {
            val noData = secondaryPaint()
            canvas.drawText(t(R.string.pdf_doc_voice_empty), leftMargin, yPosition + style.bodySize, noData)
            yPosition += style.bodySize
            return
        }

        val body = bodyPaint()
        val small = smallPaint()

        entriesWithVoice.take(20).forEach { entry ->
            ensureSpace(style.bodySize * 2 + style.lineSpacing)

            // Fecha y medicamento
            val header = "${formatDateTime(entry.timestamp)} - ${entry.medicationName}"
            canvas.drawText(header, leftMargin, yPosition + style.bodySize, body)
            yPosition += style.bodySize + style.lineSpacing / 2

            // Indicador de audio (no podemos mostrar la transcripción sin procesarla)
            canvas.drawText("  " + t(R.string.pdf_doc_audio_attached), leftMargin, yPosition + style.smallSize, small)
            yPosition += style.smallSize + style.lineSpacing
        }

        if (entriesWithVoice.size > 20) {
            val more = smallPaint()
            canvas.drawText(t(R.string.pdf_doc_more_notes, entriesWithVoice.size - 20), leftMargin, yPosition + style.smallSize, more)
            yPosition += style.smallSize
        }
    }

    // ===========================================
    // UTILIDADES
    // ===========================================

    /**
     * Trunca el texto para que quepa en el ancho máximo especificado
     */
    private fun truncateText(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text

        val ellipsis = "..."
        val ellipsisWidth = paint.measureText(ellipsis)
        val availableWidth = maxWidth - ellipsisWidth

        if (availableWidth <= 0) return ellipsis

        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText(truncated) > availableWidth) {
            truncated = truncated.dropLast(1)
        }

        return if (truncated.isEmpty()) ellipsis else truncated + ellipsis
    }

    /**
     * Genera un nombre de archivo descriptivo
     */
    private fun generateFileName(startDate: LocalDate, endDate: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("dd-MM")
        return "PillMate_Reporte_${startDate.format(formatter)}_${endDate.format(formatter)}.pdf"
    }

    /**
     * Formatea un valor numérico
     */
    private fun formatValue(value: Float): String {
        return if (value == value.toInt().toFloat()) {
            value.toInt().toString()
        } else {
            String.format("%.1f", value)
        }
    }

    /**
     * Convierte Color de Compose a Int de Android
     */
    private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}
