package com.d4vram.pillmate2.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File


/**
 * 📦 Gestor de backups usando Storage Access Framework (SAF)
 * 
 * Permite al usuario elegir dónde guardar los backups:
 * - Google Drive
 * - Carpeta Downloads
 * - SD externa
 * - Cualquier ubicación accesible por el sistema
 * 
 * Ventajas sobre el sistema anterior:
 * - Usuario controla dónde se guardan sus datos
 * - Backups NO se borran al desinstalar la app
 * - Compatible con Android 10+ (Scoped Storage)
 * - No requiere permisos de almacenamiento peligrosos
 */
class BackupSafManager(
    private val context: Context,
    private val repository: MedicationRepository
) {
    companion object {
        private val AUTO_BACKUP_URI_KEY = stringPreferencesKey("auto_backup_folder_uri")
    }

    /**
     * Obtiene el URI de la carpeta configurada para auto-backups
     * @return Flow con el URI como String, o null si no está configurado
     */
    val autoBackupFolderUri: Flow<String?> = context.backupDataStore.data
        .map { preferences -> preferences[AUTO_BACKUP_URI_KEY] }

    /**
     * Crea un backup manual en el URI especificado por el usuario
     * 
     * @param targetUri URI donde guardar el backup (obtenido de ACTION_CREATE_DOCUMENT)
     * @return true si el backup se creó correctamente, false en caso de error
     */
    suspend fun createManualBackupToUri(targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener datos del repositorio
            val medications = repository.getMedicationsSnapshot()
            val history = repository.getHistorySnapshot()

            // 2. Crear backup ZIP en archivo temporal
            val tempZipFile = File(context.cacheDir, "temp_backup_${System.currentTimeMillis()}.zip")
            BackupZipManager.createBackupZip(context, medications, history, tempZipFile)

            // 3. Copiar el ZIP al URI de destino elegido por el usuario
            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                tempZipFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // 4. Limpiar archivo temporal
            tempZipFile.delete()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Guarda el URI de la carpeta para auto-backups
     * 
     * @param folderUri URI de la carpeta (obtenido de ACTION_OPEN_DOCUMENT_TREE)
     */
    suspend fun saveAutoBackupFolderUri(folderUri: Uri) {
        // Tomar permisos persistentes para poder acceder después de reiniciar
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        
        try {
            context.contentResolver.takePersistableUriPermission(folderUri, flags)
        } catch (e: SecurityException) {
            // El URI no soporta permisos persistentes, pero intentaremos usarlo de todos modos
            e.printStackTrace()
        }

        // Guardar en DataStore
        context.backupDataStore.edit { preferences ->
            preferences[AUTO_BACKUP_URI_KEY] = folderUri.toString()
        }
    }

    /**
     * Crea un auto-backup en la carpeta previamente configurada
     * 
     * @return true si el backup se creó correctamente, false si no hay carpeta configurada o hubo error
     */
    suspend fun createAutoBackupToFolder(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener URI de la carpeta configurada
            val folderUriString = autoBackupFolderUri.first() ?: return@withContext false
            val folderUri = Uri.parse(folderUriString)

            // 2. Verificar que la carpeta existe y es accesible
            val documentFolder = DocumentFile.fromTreeUri(context, folderUri) 
                ?: return@withContext false

            if (!documentFolder.exists() || !documentFolder.canWrite()) {
                return@withContext false
            }

            // 3. Obtener datos del repositorio
            val medications = repository.getMedicationsSnapshot()
            val history = repository.getHistorySnapshot()

            // 4. Crear backup ZIP en archivo temporal
            val tempZipFile = File(context.cacheDir, "temp_auto_backup_${System.currentTimeMillis()}.zip")
            BackupZipManager.createBackupZip(context, medications, history, tempZipFile)

            // 5. Crear archivo en la carpeta del usuario
            val fileName = "pillmate_auto_${System.currentTimeMillis()}.zip"
            val newFile = documentFolder.createFile("application/zip", fileName)
                ?: return@withContext false

            // 6. Copiar contenido del ZIP temporal al archivo final
            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                tempZipFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // 7. Limpiar archivo temporal
            tempZipFile.delete()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Obtiene el nombre legible de la carpeta de auto-backups
     * 
     * @return Nombre de la carpeta o null si no está configurada
     */
    suspend fun getAutoBackupFolderName(): String? = withContext(Dispatchers.IO) {
        try {
            val folderUriString = autoBackupFolderUri.first() ?: return@withContext null
            val folderUri = Uri.parse(folderUriString)
            val documentFolder = DocumentFile.fromTreeUri(context, folderUri)
            documentFolder?.name
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Elimina la configuración de carpeta de auto-backups
     */
    suspend fun clearAutoBackupFolder() {
        context.backupDataStore.edit { preferences ->
            preferences.remove(AUTO_BACKUP_URI_KEY)
        }
    }
}
