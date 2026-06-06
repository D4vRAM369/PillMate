package com.d4vram.pillmate2.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.d4vram.pillmate2.ui.theme.QuickTakeSettings
import com.d4vram.pillmate2.ui.theme.QuickTakeWidgetShape
import com.d4vram.pillmate2.ui.theme.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class QuickTakeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = QuickTakeWidgetRepository.load(context)

        provideContent {
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
        QuickTakeWidgetRepository.takeMedication(context, medicationId)
        QuickTakeWidget().updateAll(context)
    }
}

private val MedicationIdKey = ActionParameters.Key<String>("medication_id")

private data class QuickTakeWidgetData(
    val slots: List<MedicationEntity?>,
    val shape: QuickTakeWidgetShape
)

private object QuickTakeWidgetRepository {
    suspend fun load(context: Context): QuickTakeWidgetData = withContext(Dispatchers.IO) {
        val preferences = context.dataStore.data.first()
        val slots = QuickTakeSettings.run {
            preferences[MEDICATION_IDS_KEY].decodeSlots()
        }
        val shape = QuickTakeWidgetShape.fromStorage(
            preferences[QuickTakeSettings.WIDGET_SHAPE_KEY]
        )
        val medicationsById = PillMateDatabase.getInstance(context)
            .medicationDao()
            .getAllMedications()
            .associateBy { it.id }

        QuickTakeWidgetData(
            slots = slots.map { id -> id?.let { medicationsById[it] } },
            shape = shape
        )
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
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFF7FBF7)))
            .padding(10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = "Toma rápida",
            style = TextStyle(
                color = ColorProvider(Color(0xFF234236)),
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.fillMaxWidth()
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetCell(data.slots.getOrNull(0), data.shape, GlanceModifier.defaultWeight())
            Spacer(modifier = GlanceModifier.width(8.dp))
            WidgetCell(data.slots.getOrNull(1), data.shape, GlanceModifier.defaultWeight())
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetCell(data.slots.getOrNull(2), data.shape, GlanceModifier.defaultWeight())
            Spacer(modifier = GlanceModifier.width(8.dp))
            WidgetCell(data.slots.getOrNull(3), data.shape, GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun WidgetCell(
    medication: MedicationEntity?,
    shape: QuickTakeWidgetShape,
    modifier: GlanceModifier = GlanceModifier
) {
    val radius = when (shape) {
        QuickTakeWidgetShape.Circle -> 48.dp
        QuickTakeWidgetShape.Rounded -> 18.dp
        QuickTakeWidgetShape.Square -> 4.dp
    }
    val background = medication?.let { ColorProvider(Color(it.colorArgb)) }
        ?: ColorProvider(Color(0xFFE4EEE8))
    val clickAction = medication?.let {
        actionRunCallback<QuickTakeActionCallback>(
            parameters = actionParametersOf(MedicationIdKey to it.id)
        )
    } ?: actionStartActivity<MainActivity>()

    Box(
        modifier = modifier
            .height(72.dp)
            .background(background)
            .cornerRadius(radius)
            .clickable(clickAction)
            .padding(6.dp),
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
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = medication.emoji.ifBlank { "✓" },
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
        Text(
            text = medication.name.take(14),
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
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
