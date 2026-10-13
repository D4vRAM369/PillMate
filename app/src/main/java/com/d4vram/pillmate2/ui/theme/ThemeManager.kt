package com.d4vram.pillmate2.ui.theme

import androidx.annotation.StringRes
import com.d4vram.pillmate2.R
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import android.content.Context
import android.util.Log
import com.d4vram.pillmate2.model.SortMode

enum class QuickTakeWidgetShape(val storageValue: String, @StringRes val titleRes: Int) {
    Rounded("rounded", R.string.widget_shape_rounded),
    Circle("circle", R.string.widget_shape_circle),
    Square("square", R.string.widget_shape_square);

    companion object {
        fun fromStorage(value: String?): QuickTakeWidgetShape =
            entries.firstOrNull { it.storageValue == value } ?: Rounded
    }
}

enum class QuickTakeWidgetColor(
    val storageValue: String,
    val title: String,
    val backgroundArgb: Long,
    val emptyCellArgb: Long,
    val accentArgb: Long
) {
    Green("green",   "Verde",    0xFFC8EAD6L, 0xFFAEDCC2L, 0xFF2E7D55L),
    Blue("blue",     "Azul",     0xFFC4DBFFL, 0xFFA9C8FFL, 0xFF2F62B0L),
    Purple("purple", "Morado",   0xFFDDCBFFL, 0xFFC9B0FFL, 0xFF6E45B0L),
    Orange("orange", "Naranja",  0xFFFFD7C4L, 0xFFFFC0A6L, 0xFFC25A30L),
    Pink("pink",     "Rosa",     0xFFFFCFE2L, 0xFFFFB3D0L, 0xFFB04D78L),
    Yellow("yellow", "Amarillo", 0xFFFFEEA8L, 0xFFFFE27AL, 0xFF9A7400L),
    Gray("gray",     "Gris",     0xFFDCDCDCL, 0xFFC4C4C4L, 0xFF5A5A5AL),
    Dark("dark",     "Oscuro",   0xFF1A1A2EL, 0xFF2D2D44L, 0xFF7FE0B0L);

    companion object {
        fun fromStorage(value: String?): QuickTakeWidgetColor =
            entries.firstOrNull { it.storageValue == value } ?: Green
    }
}

enum class QuickTakeWidgetSize(
    val storageValue: String,
    @StringRes val titleRes: Int,
    val paddingDp: Int,
    val gapDp: Int,
    val textSp: Int,
    val showHeader: Boolean
) {
    // Las celdas ya no tienen alto fijo: se reparten el espacio del widget.
    // El preset solo decide la densidad (márgenes, texto y si hay título).
    Compact("compact", R.string.widget_size_compact, 6, 4, 11, false),
    Normal("normal",   R.string.widget_size_normal,   10, 6, 13, true),
    Large("large",     R.string.widget_size_large,   12, 8, 15, true);

    companion object {
        fun fromStorage(value: String?): QuickTakeWidgetSize =
            entries.firstOrNull { it.storageValue == value } ?: Normal
    }
}

object QuickTakeSettings {
    const val SLOT_COUNT = 4
    val MEDICATION_IDS_KEY = stringPreferencesKey("quick_take_medication_ids")
    val WIDGET_SHAPE_KEY = stringPreferencesKey("quick_take_widget_shape")
    val WIDGET_COLOR_KEY = stringPreferencesKey("quick_take_widget_color")
    val WIDGET_SIZE_KEY  = stringPreferencesKey("quick_take_widget_size")

    fun String?.decodeSlots(): List<String?> {
        val parts = this
            ?.split("|")
            ?.take(SLOT_COUNT)
            ?: emptyList()

        return (0 until SLOT_COUNT).map { index ->
            parts.getOrNull(index)?.takeIf { it.isNotBlank() }
        }
    }

    fun List<String?>.encodeSlots(): String {
        return (0 until SLOT_COUNT)
            .joinToString("|") { index -> getOrNull(index).orEmpty() }
    }
}

enum class AppUiStyle(val storageValue: String, @StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    Default("default", R.string.ui_style_classic, R.string.ui_style_classic_desc),
    MaterialYou("material_you", R.string.ui_style_material_you, R.string.ui_style_material_you_desc);

    companion object {
        fun fromStorage(value: String?): AppUiStyle =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

enum class ChipCountPeriod(val storageValue: String, @StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    Daily("daily", R.string.chip_period_daily, R.string.chip_period_daily_desc),
    Weekly("weekly", R.string.chip_period_weekly, R.string.chip_period_weekly_desc),
    Monthly("monthly", R.string.chip_period_monthly, R.string.chip_period_monthly_desc),
    Total("total", R.string.chip_period_total, R.string.chip_period_total_desc),
    Hidden("hidden", R.string.chip_period_hidden, R.string.chip_period_hidden_desc);

    companion object {
        fun fromStorage(value: String?): ChipCountPeriod =
            entries.firstOrNull { it.storageValue == value } ?: Total
    }
}

// Extensión para crear el DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

/**
 * 🎨 Gestor del tema de la aplicación
 * Controla el modo oscuro/claro y persiste la preferencia
 */
class ThemeManager(private val context: Context) {
    companion object {
        private val IS_DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")
        private val UI_STYLE_KEY = stringPreferencesKey("ui_style")
        private val APP_DESIGN_KEY = stringPreferencesKey("app_design")
        private val CHIP_COUNT_PERIOD_KEY = stringPreferencesKey("chip_count_period")
        private val SORT_MODE_KEY = stringPreferencesKey("sort_mode")
        private val IS_LIST_VIEW_KEY = booleanPreferencesKey("is_list_view")
        private val QUICK_TAKE_NOTIFICATION_KEY = booleanPreferencesKey("quick_take_notification_enabled")
    }
    
    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading theme preferences", throwable)
            emit(emptyPreferences())
            clearCorruptedPreferences()
        }
        .map { preferences ->
            preferences[IS_DARK_MODE_KEY] ?: true // Por defecto modo oscuro
        }

    val uiStyle: Flow<AppUiStyle> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading theme preferences", throwable)
            emit(emptyPreferences())
            clearCorruptedPreferences()
        }
        .map { preferences ->
            val stored = preferences[UI_STYLE_KEY]
            AppUiStyle.fromStorage(stored)
        }

    val appDesign: Flow<AppDesign> = context.dataStore.data
        .catch { throwable ->
            if (throwable is java.io.IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { AppDesign.fromStorage(it[APP_DESIGN_KEY]) }

    suspend fun setAppDesign(design: AppDesign) {
        context.dataStore.edit { it[APP_DESIGN_KEY] = design.storageValue }
    }

    val chipCountPeriod: Flow<ChipCountPeriod> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading chip count period preferences", throwable)
            emit(emptyPreferences())
        }
        .map { preferences ->
            val stored = preferences[CHIP_COUNT_PERIOD_KEY]
            ChipCountPeriod.fromStorage(stored)
        }

    val sortMode: Flow<SortMode> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading sort mode preferences", throwable)
            emit(emptyPreferences())
        }
        .map { preferences ->
            val stored = preferences[SORT_MODE_KEY]
            SortMode.entries.firstOrNull { it.name == stored } ?: SortMode.MANUAL
        }

    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DARK_MODE_KEY] = isDark
        }
    }

    suspend fun setUiStyle(style: AppUiStyle) {
        context.dataStore.edit { preferences ->
            preferences[UI_STYLE_KEY] = style.storageValue
        }
    }

    suspend fun setChipCountPeriod(period: ChipCountPeriod) {
        context.dataStore.edit { preferences ->
            preferences[CHIP_COUNT_PERIOD_KEY] = period.storageValue
        }
    }

    suspend fun setSortMode(mode: SortMode) {
        context.dataStore.edit { preferences ->
            preferences[SORT_MODE_KEY] = mode.name
        }
    }

    val isListView: Flow<Boolean> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading list view preference", throwable)
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[IS_LIST_VIEW_KEY] ?: false
        }

    suspend fun setListView(isList: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LIST_VIEW_KEY] = isList
        }
    }

    /**
     * Toggle de la notificación persistente de "toma rápida". Default OFF: es
     * una notificación siempre presente, debe ser opt-in explícito del usuario.
     */
    val quickTakeNotificationEnabled: Flow<Boolean> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading quick take notification preference", throwable)
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[QUICK_TAKE_NOTIFICATION_KEY] ?: false
        }

    suspend fun setQuickTakeNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[QUICK_TAKE_NOTIFICATION_KEY] = enabled
        }
    }

    val quickTakeMedicationIds: Flow<List<String?>> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading quick take preferences", throwable)
            emit(emptyPreferences())
        }
        .map { preferences ->
            QuickTakeSettings.run {
                preferences[MEDICATION_IDS_KEY].decodeSlots()
            }
        }

    val quickTakeWidgetShape: Flow<QuickTakeWidgetShape> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading quick take widget shape", throwable)
            emit(emptyPreferences())
        }
        .map { preferences ->
            QuickTakeWidgetShape.fromStorage(preferences[QuickTakeSettings.WIDGET_SHAPE_KEY])
        }

    val quickTakeWidgetColor: Flow<QuickTakeWidgetColor> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading widget color", throwable)
            emit(emptyPreferences())
        }
        .map { preferences ->
            QuickTakeWidgetColor.fromStorage(preferences[QuickTakeSettings.WIDGET_COLOR_KEY])
        }

    val quickTakeWidgetSize: Flow<QuickTakeWidgetSize> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading widget size", throwable)
            emit(emptyPreferences())
        }
        .map { preferences ->
            QuickTakeWidgetSize.fromStorage(preferences[QuickTakeSettings.WIDGET_SIZE_KEY])
        }

    suspend fun setQuickTakeMedication(slotIndex: Int, medicationId: String?) {
        if (slotIndex !in 0 until QuickTakeSettings.SLOT_COUNT) return

        context.dataStore.edit { preferences ->
            val slots = QuickTakeSettings.run {
                preferences[MEDICATION_IDS_KEY].decodeSlots()
            }
                .toMutableList()
            slots[slotIndex] = medicationId
            preferences[QuickTakeSettings.MEDICATION_IDS_KEY] = QuickTakeSettings.run {
                slots.encodeSlots()
            }
        }
    }

    suspend fun setQuickTakeWidgetShape(shape: QuickTakeWidgetShape) {
        context.dataStore.edit { preferences ->
            preferences[QuickTakeSettings.WIDGET_SHAPE_KEY] = shape.storageValue
        }
    }

    suspend fun setQuickTakeWidgetColor(color: QuickTakeWidgetColor) {
        context.dataStore.edit { preferences ->
            preferences[QuickTakeSettings.WIDGET_COLOR_KEY] = color.storageValue
        }
    }

    suspend fun setQuickTakeWidgetSize(size: QuickTakeWidgetSize) {
        context.dataStore.edit { preferences ->
            preferences[QuickTakeSettings.WIDGET_SIZE_KEY] = size.storageValue
        }
    }

    private suspend fun clearCorruptedPreferences() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

}

/**
 * 🎨 Composable para proporcionar el ThemeManager
 */
@Composable
fun rememberThemeManager(): ThemeManager {
    val context = LocalContext.current
    return remember { ThemeManager(context) }
}
