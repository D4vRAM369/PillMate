package com.d4vram.pillmate2.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.content.Context

// Extensión para crear el DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

/**
 * 🎨 Gestor del tema de la aplicación
 * Controla el modo oscuro/claro y persiste la preferencia
 */
class ThemeManager(private val context: Context) {
    companion object {
        private val IS_DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")
    }
    
    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_DARK_MODE_KEY] ?: false // Por defecto modo claro
        }
    
    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DARK_MODE_KEY] = isDark
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