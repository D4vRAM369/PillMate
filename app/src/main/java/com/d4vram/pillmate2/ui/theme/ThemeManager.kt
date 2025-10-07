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

enum class AppUiStyle(val storageValue: String, val title: String, val description: String) {
    Default("default", "Clásico", "Estilo actual de PillMate"),
    MaterialYou("material_you", "Material You", "Componentes nativos de Material Design 3");

    companion object {
        fun fromStorage(value: String?): AppUiStyle =
            entries.firstOrNull { it.storageValue == value } ?: Default
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
