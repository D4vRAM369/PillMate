package com.d4vram.pillmate2.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import com.d4vram.pillmate2.MainActivity
import com.d4vram.pillmate2.R
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
            color = QuickTakeWidgetColor.Dark,
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

@Composable
private fun QuickTakeWidgetContent(data: QuickTakeWidgetData) {
    // Grado 2 de personalización: color por defecto Oscuro; las celdas usan el
    // color real de cada medicamento. El header/acento usa el accent del tema.
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(data.color.backgroundArgb)))
            .padding(data.size.paddingDp.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = "Toma rápida",
            style = TextStyle(
                color = ColorProvider(Color(data.color.accentArgb)),
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.fillMaxWidth()
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetCell(data.slots.getOrNull(0), data.shape, data.color, data.size, GlanceModifier.defaultWeight())
            Spacer(modifier = GlanceModifier.width(6.dp))
            WidgetCell(data.slots.getOrNull(1), data.shape, data.color, data.size, GlanceModifier.defaultWeight())
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetCell(data.slots.getOrNull(2), data.shape, data.color, data.size, GlanceModifier.defaultWeight())
            Spacer(modifier = GlanceModifier.width(6.dp))
            WidgetCell(data.slots.getOrNull(3), data.shape, data.color, data.size, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun WidgetCell(
    medication: MedicationEntity?,
    shape: QuickTakeWidgetShape,
    color: QuickTakeWidgetColor,
    size: QuickTakeWidgetSize,
    modifier: GlanceModifier = GlanceModifier
) {
    val radius = when (shape) {
        QuickTakeWidgetShape.Circle -> 48.dp
        QuickTakeWidgetShape.Rounded -> 18.dp
        QuickTakeWidgetShape.Square -> 4.dp
    }
    val background = medication?.let { ColorProvider(Color(it.colorArgb)) }
        ?: ColorProvider(Color(color.emptyCellArgb))
    val clickAction = medication?.let {
        actionRunCallback<QuickTakeActionCallback>(
            parameters = actionParametersOf(
                MedicationIdKey to it.id,
                MedicationNameKey to it.name
            )
        )
    } ?: actionStartActivity<MainActivity>()

    Box(
        modifier = modifier
            .height(size.cellHeightDp.dp)
            .background(background)
            .cornerRadius(radius)
            .clickable(clickAction),
        contentAlignment = Alignment.Center
    ) {
        if (medication == null) {
            EmptyCell()
        } else {
            MedicationCell(medication)
        }
    }
}

@Composable
private fun MedicationCell(medication: MedicationEntity) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(6.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Emoji
        Text(
            text = medication.emoji.ifBlank { "✓" },
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
        // Nombre
        Text(
            text = medication.name.take(14),
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        // Dosis → refuerza la legibilidad y aprovecha el dato ya disponible.
        if (medication.dose.isNotBlank()) {
            Text(
                text = medication.dose.take(10),
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.8f)),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyCell() {
    Column(
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp)
        )
        Text(
            text = "Elegir",
            style = TextStyle(
                color = ColorProvider(Color(0xFF48675A)),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}