package com.d4vram.pillmate2.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension para crear DataStore
private val Context.permissionsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "permissions_preferences"
)

/**
 * Gestor centralizado de permisos de la aplicación
 * Controla el estado de solicitud de permisos y si deben mostrarse
 */
class PermissionsManager(private val context: Context) {

    private val dataStore = context.permissionsDataStore

    companion object {
        private val PERMISSIONS_REQUESTED_KEY = booleanPreferencesKey("permissions_requested")
        private val PERMISSIONS_DIALOG_SHOWN_KEY = booleanPreferencesKey("permissions_dialog_shown")
    }

    /**
     * Flow que indica si se deben solicitar permisos
     * True = mostrar diálogo de permisos
     */
    val shouldShowPermissionsDialog: Flow<Boolean> = dataStore.data.map { preferences ->
        val dialogShown = preferences[PERMISSIONS_DIALOG_SHOWN_KEY] ?: false
        val hasPermissions = checkAllPermissionsGranted()

        // Mostrar diálogo si nunca se mostró Y no tiene los permisos
        !dialogShown && !hasPermissions
    }

    /**
     * Verifica si todos los permisos necesarios están concedidos
     */
    fun checkAllPermissionsGranted(): Boolean {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Obtiene la lista de permisos requeridos según la versión de Android
     */
    fun getRequiredPermissions(): List<String> {
        return listOf(Manifest.permission.CAMERA)
    }

    /**
     * Marca que el diálogo de permisos ya fue mostrado
     */
    suspend fun markPermissionsDialogShown() {
        dataStore.edit { preferences ->
            preferences[PERMISSIONS_DIALOG_SHOWN_KEY] = true
        }
    }

    /**
     * Marca que los permisos fueron solicitados
     */
    suspend fun markPermissionsRequested() {
        dataStore.edit { preferences ->
            preferences[PERMISSIONS_REQUESTED_KEY] = true
            preferences[PERMISSIONS_DIALOG_SHOWN_KEY] = true
        }
    }

    /**
     * Resetea el estado de permisos (útil para testing o configuración)
     */
    suspend fun resetPermissionsState() {
        dataStore.edit { preferences ->
            preferences.remove(PERMISSIONS_REQUESTED_KEY)
            preferences.remove(PERMISSIONS_DIALOG_SHOWN_KEY)
        }
    }

    /**
     * Verifica si el permiso de cámara específicamente está concedido
     */
    fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

}
