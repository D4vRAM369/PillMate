package com.d4vram.pillmate2.widget

import com.d4vram.pillmate2.R
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.d4vram.pillmate2.MainActivity
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.data.local.MedicationEntity
import com.d4vram.pillmate2.data.local.PillMateDatabase
import com.d4vram.pillmate2.notifications.ToastNotifier
import com.d4vram.pillmate2.ui.theme.QuickTakeSettings
import com.d4vram.pillmate2.ui.theme.QuickTakeWidgetColor
import com.d4vram.pillmate2.ui.theme.QuickTakeWidgetShape
import com.d4vram.pillmate2.ui.theme.QuickTakeWidgetSize
import com.d4vram.pillmate2.ui.theme.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class QuickTakeWidget : GlanceAppWidget() {
    // Exact: Glance nos da el tamaño real del widget (LocalSize) cada vez que se
    // redimensiona, y con él decidimos la rejilla y qué cabe en cada celda.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Leer datos DENTRO de provideContent y de forma reactiva.
            // provideGlance solo corre 1 vez por sesión; un valor capturado aquí
            // quedaría congelado. collectAsState sobre el flow re-pinta el widget
            // cada vez que DataStore (color/forma/tamaño) o Room (medicación) cambian.
            val data by QuickTakeWidgetRepository.observe(context)
                .collectAsState(initial = QuickTakeWidgetData.EMPTY)
            QuickTakeWidgetContent(data = data)
        }
    }
}

class QuickTakeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickTakeWidget()
}

class QuickTakeActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val medicationId = parameters[MedicationIdKey] ?: return
        val medicationName = parameters[MedicationNameKey].orEmpty()
        QuickTakeWidgetRepository.takeMedication(context, medicationId)
        QuickTakeWidget().updateAll(context)
        // Confirmación visible (mismo Toast que la acción de la notificación).
        ToastNotifier.showTakeConfirmation(context, medicationName)
    }
}

private val MedicationIdKey = ActionParameters.Key<String>("medication_id")
private val MedicationNameKey = ActionParameters.Key<String>("medication_name")

private data class QuickTakeWidgetData(
    val slots: List<MedicationEntity?>,
    val shape: QuickTakeWidgetShape,
    val color: QuickTakeWidgetColor,
    val size: QuickTakeWidgetSize
) {
    companion object {
        val EMPTY = QuickTakeWidgetData(
            slots = List(QuickTakeSettings.SLOT_COUNT) { null },
            shape = QuickTakeWidgetShape.Rounded,
            color = QuickTakeWidgetColor.Green,
            size = QuickTakeWidgetSize.Normal
        )
    }
}

private object QuickTakeWidgetRepository {
    /**
     * Flow reactivo: combina las preferencias (DataStore) con las medicaciones (Room).
     * Emite un nuevo QuickTakeWidgetData cada vez que cambia cualquiera de las dos
     * fuentes, lo que dispara la recomposición del widget con datos frescos.
     */
    fun observe(context: Context): Flow<QuickTakeWidgetData> {
        val prefsFlow = context.dataStore.data
        val medsFlow = PillMateDatabase.getInstance(context)
            .medicationDao()
            .observeMedications()

        return combine(prefsFlow, medsFlow) { preferences, medications ->
            val slots = QuickTakeSettings.run {
                preferences[MEDICATION_IDS_KEY].decodeSlots()
            }
            val shape = QuickTakeWidgetShape.fromStorage(
                preferences[QuickTakeSettings.WIDGET_SHAPE_KEY]
            )
            val color = QuickTakeWidgetColor.fromStorage(
                preferences[QuickTakeSettings.WIDGET_COLOR_KEY]
            )
            val size = QuickTakeWidgetSize.fromStorage(
                preferences[QuickTakeSettings.WIDGET_SIZE_KEY]
            )
            val medicationsById = medications.associateBy { it.id }

            QuickTakeWidgetData(
                slots = slots.map { id -> id?.let { medicationsById[it] } },
                shape = shape,
                color = color,
                size = size
            )
        }.flowOn(Dispatchers.IO)
    }

    suspend fun takeMedication(context: Context, medicationId: String) = withContext(Dispatchers.IO) {
        val database = PillMateDatabase.getInstance(context)
        MedicationRepository(
            database = database,
            medicationDao = database.medicationDao(),
            historyDao = database.historyDao()
        ).recordMedicationTaken(medicationId, quantity = 1, customTimestamp = null)
    }
}

/** Columnas de la rejilla según la proporción del widget: 4x1, 2x2 o 1x4. */
internal fun quickTakeColumns(widthDp: Float, heightDp: Float): Int = when {
    widthDp >= heightDp * 2.2f -> 4
    heightDp >= widthDp * 2.2f -> 1
    else -> 2
}

/** Texto legible sobre un color de fondo cualquiera: oscuro sobre claro, blanco sobre oscuro. */
internal fun readableOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White

// Glance no sabe medir texto ni tiene "autoajuste": el ancho se estima. Una letra normal ocupa
// ~0,58 veces el tamaño de la fuente (medido en un Pixel con letra al 130 %); se usa 0,60 para dejar
// margen, porque quedarse corto corta el nombre y pasarse solo lo encoge un punto. Las letras anchas
// y estrechas se ponderan en charUnits. Subir si algún nombre aún se corta.
private const val CharWidthFactor = 0.60f
private const val MinNameScale = 0.7f // "algo más pequeña", no ilegible: como mucho un 30 % menos

/** Ancho de una letra en "letras normales": la m ocupa como letra y media, la i menos de media. */
private fun charUnits(c: Char): Float = when {
    c in "iljI.,' " -> 0.4f
    c in "trf" -> 0.6f
    c in "mwMW" -> 1.5f
    c.isUpperCase() -> 1.3f
    else -> 1f
}

/**
 * Tamaño de letra con el que [name] cabe en [availableDp] sin partir una palabra. Con una línea
 * cuenta el nombre entero; con dos, la palabra más ancha (las demás saltan de línea solas).
 * [fontScale] es el "tamaño de fuente" de los ajustes del sistema, que agranda los sp.
 */
internal fun fittedNameSp(name: String, baseSp: Int, availableDp: Float, maxLines: Int, fontScale: Float): Int {
    val pieces = if (maxLines > 1) name.split(' ') else listOf(name)
    val units = pieces.maxOf { piece -> piece.sumOf { charUnits(it).toDouble() } }.toFloat()
    val neededDp = units * baseSp * CharWidthFactor * fontScale
    if (neededDp <= availableDp) return baseSp
    return (baseSp * availableDp / neededDp).toInt().coerceAtLeast((baseSp * MinNameScale).toInt())
}

private val HeaderHeight = 22.dp

@Composable
private fun QuickTakeWidgetContent(data: QuickTakeWidgetData) {
    val widgetSize = LocalSize.current
    val preset = data.size
    val padding = preset.paddingDp.dp
    val gap = preset.gapDp.dp

    // El título solo aparece si sobra altura: en un widget bajo se lo comería todo.
    val innerWidth = widgetSize.width - padding * 2
    val showHeader = preset.showHeader && widgetSize.height >= 150.dp
    val innerHeight = widgetSize.height - padding * 2 - (if (showHeader) HeaderHeight else 0.dp)

    val columns = quickTakeColumns(innerWidth.value, innerHeight.value)
    val rows = QuickTakeSettings.SLOT_COUNT / columns
    val cellWidth = (innerWidth - gap * (columns - 1)) / columns
    val cellHeight = (innerHeight - gap * (rows - 1)) / rows

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(data.color.backgroundArgb)))
            .cornerRadius(24.dp)
            .padding(padding)
    ) {
        if (showHeader) {
            Text(
                text = LocalContext.current.getString(R.string.quick_take_title),
                style = TextStyle(
                    color = ColorProvider(Color(data.color.accentArgb)),
                    fontWeight = FontWeight.Bold,
                    fontSize = preset.textSp.sp
                ),
                maxLines = 1,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(HeaderHeight)
                    .clickable(actionStartActivity<MainActivity>())
            )
        }
        for (row in 0 until rows) {
            if (row > 0) Spacer(modifier = GlanceModifier.height(gap))
            // defaultWeight: las filas se reparten TODO el alto disponible.
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (column in 0 until columns) {
                    if (column > 0) Spacer(modifier = GlanceModifier.width(gap))
                    WidgetCell(
                        medication = data.slots.getOrNull(row * columns + column),
                        data = data,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetCell(
    medication: MedicationEntity?,
    data: QuickTakeWidgetData,
    cellWidth: Dp,
    cellHeight: Dp,
    modifier: GlanceModifier
) {
    val context = LocalContext.current
    val textSp = data.size.textSp
    val fontScale = context.resources.configuration.fontScale
    val fill = medication?.let { Color(it.colorArgb) } ?: Color(data.color.emptyCellArgb)
    val clickAction = medication?.let {
        actionRunCallback<QuickTakeActionCallback>(
            parameters = actionParametersOf(
                MedicationIdKey to it.id,
                MedicationNameKey to it.name
            )
        )
    } ?: actionStartActivity<MainActivity>()
    val contentDescription = medication?.let {
        context.getString(R.string.quick_take_cell_log, it.name)
    } ?: context.getString(R.string.quick_take_cell_configure)
    val accessibleModifier = modifier
        .semantics { this.contentDescription = contentDescription }
        .clickable(clickAction)

    if (data.shape == QuickTakeWidgetShape.Circle) {
        // Un círculo de verdad necesita una caja cuadrada: su lado es lo que quepa
        // en la celda. El nombre va debajo, si queda sitio.
        val labelHeight = (textSp * 1.6f).dp
        val showLabel = medication != null && cellHeight - labelHeight >= 36.dp
        val available = if (showLabel) cellHeight - labelHeight else cellHeight
        val diameter = (if (cellWidth < available) cellWidth else available) - 2.dp

        Column(
            modifier = accessibleModifier,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(diameter)
                    .background(ColorProvider(fill))
                    .cornerRadius(diameter / 2),
                contentAlignment = Alignment.Center
            ) {
                CellGlyph(medication, data, fill, (diameter.value * 0.4f).toInt())
            }
            if (showLabel) {
                CellName(
                    name = medication!!.name,
                    color = readableOn(Color(data.color.backgroundArgb)),
                    textSp = fittedNameSp(medication.name, textSp - 1, cellWidth.value, 1, fontScale),
                    maxLines = 1
                )
            }
        }
        return
    }

    val radius = if (data.shape == QuickTakeWidgetShape.Rounded) 16.dp else 4.dp
    Box(
        modifier = accessibleModifier
            .background(ColorProvider(fill))
            .cornerRadius(radius),
        contentAlignment = Alignment.Center
    ) {
        if (medication == null) {
            CellGlyph(null, data, fill, textSp + 9)
        } else {
            // El emoji solo si la celda es alta; el nombre usa 2 líneas si caben.
            val showEmoji = cellHeight >= 50.dp
            // Las alturas de texto también crecen con el tamaño de letra del sistema
            val textRoom = cellHeight.value - 8 - (if (showEmoji) (textSp + 12) * fontScale else 0f)
            val nameLines = if (textRoom >= textSp * 2.7f * fontScale) 2 else 1
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(4.dp),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                if (showEmoji) CellGlyph(medication, data, fill, textSp + 6)
                CellName(
                    name = medication.name,
                    color = readableOn(fill),
                    // 8 = el padding de 4 dp a cada lado de la celda
                    textSp = fittedNameSp(medication.name, textSp, cellWidth.value - 8, nameLines, fontScale),
                    maxLines = nameLines
                )
            }
        }
    }
}

/** Lo que identifica la celda de un vistazo: emoji, inicial del nombre o "+" si está vacía. */
@Composable
private fun CellGlyph(
    medication: MedicationEntity?,
    data: QuickTakeWidgetData,
    fill: Color,
    textSp: Int
) {
    Text(
        text = when {
            medication == null -> "+"
            medication.emoji.isNotBlank() -> medication.emoji
            else -> medication.name.take(1).uppercase()
        },
        style = TextStyle(
            color = ColorProvider(
                if (medication == null) Color(data.color.accentArgb) else readableOn(fill)
            ),
            fontWeight = FontWeight.Bold,
            fontSize = textSp.sp,
            textAlign = TextAlign.Center
        ),
        maxLines = 1
    )
}

@Composable
private fun CellName(name: String, color: Color, textSp: Int, maxLines: Int) {
    Text(
        text = name,
        style = TextStyle(
            color = ColorProvider(color),
            fontWeight = FontWeight.Medium,
            fontSize = textSp.sp,
            textAlign = TextAlign.Center
        ),
        maxLines = maxLines
    )
}
