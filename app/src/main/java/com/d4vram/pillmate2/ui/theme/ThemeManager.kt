package com.d4vram.pillmate2.ui.theme

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

enum class AppUiStyle(val storageValue: String, val title: String, val description: String) {
    Default("default", "Clásico", "Estilo actual de PillMate"),
    MaterialYou("material_you", "Material You", "Componentes nativos de Material Design 3");

    companion object {
        fun fromStorage(value: String?): AppUiStyle =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

enum class ChipCountPeriod(val storageValue: String, val title: String, val description: String) {
    Daily("daily", "Diario", "Mostrar tomas de hoy"),
    Weekly("weekly", "Semanal", "Mostrar tomas de esta semana"),
    Monthly("monthly", "Mensual", "Mostrar tomas de este mes"),
    Total("total", "Total", "Mostrar todas las tomas registradas"),
    Hidden("hidden", "No mostrar", "Ocultar el chip de recuento");

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
        private const val QUICK_TAKE_SLOT_COUNT = 4
        private val IS_DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")
        private val UI_STYLE_KEY = stringPreferencesKey("ui_style")
        private val CHIP_COUNT_PERIOD_KEY = stringPreferencesKey("chip_count_period")
        private val SORT_MODE_KEY = stringPreferencesKey("sort_mode")
        private val IS_LIST_VIEW_KEY = booleanPreferencesKey("is_list_view")
        private val QUICK_TAKE_MEDICATION_IDS_KEY = stringPreferencesKey("quick_take_medication_ids")
    }
    
    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading theme preferences", throwable)
            emit(emptyPreferences())
            clearCorruptedPreferences()
        }
        .map { preferences ->
            preferences[IS_DARK_MODE_KEY] ?: false // Por defecto modo claro
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

    val quickTakeMedicationIds: Flow<List<String?>> = context.dataStore.data
        .catch { throwable ->
            Log.e("ThemeManager", "Error reading quick take preferences", throwable)
            emit(emptyPreferences())
        }
        .map { preferences ->
            preferences[QUICK_TAKE_MEDICATION_IDS_KEY]
                .decodeQuickTakeSlots()
        }

    suspend fun setQuickTakeMedication(slotIndex: Int, medicationId: String?) {
        if (slotIndex !in 0 until QUICK_TAKE_SLOT_COUNT) return

        context.dataStore.edit { preferences ->
            val slots = preferences[QUICK_TAKE_MEDICATION_IDS_KEY]
                .decodeQuickTakeSlots()
                .toMutableList()
            slots[slotIndex] = medicationId
            preferences[QUICK_TAKE_MEDICATION_IDS_KEY] = slots.encodeQuickTakeSlots()
        }
    }

    private suspend fun clearCorruptedPreferences() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private fun String?.decodeQuickTakeSlots(): List<String?> {
        val parts = this
            ?.split("|")
            ?.take(QUICK_TAKE_SLOT_COUNT)
            ?: emptyList()

        return (0 until QUICK_TAKE_SLOT_COUNT).map { index ->
            parts.getOrNull(index)?.takeIf { it.isNotBlank() }
        }
    }

    private fun List<String?>.encodeQuickTakeSlots(): String {
        return (0 until QUICK_TAKE_SLOT_COUNT)
            .joinToString("|") { index -> getOrNull(index).orEmpty() }
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
