package com.d4vram.pillmate2.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 📝 Modelo de datos para un borrador de nota
 *
 * @property entryId ID de la entrada del historial que se está editando
 * @property notesText Texto de la nota (puede estar vacío)
 * @property dateTimeIso Fecha y hora en formato ISO-8601 (ej: "2024-01-23T14:30:00")
 * @property audioNoteUri URI del audio si existe
 * @property lastModified Timestamp de última modificación (para saber si es reciente)
 */
@Serializable
data class DraftNote(
    val entryId: String,
    val notesText: String,
    val dateTimeIso: String,
    val audioNoteUri: String? = null,
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * 🗄️ Manager para persistir borradores de notas usando DataStore
 *
 * ## ¿Qué problema resuelve?
 * Cuando el usuario está editando una nota y:
 * - La app pierde el foco (cambia a otra app)
 * - Se activa el bloqueo con PIN
 * - Android mata la app en background
 *
 * El borrador se guarda automáticamente y se recupera al volver.
 *
 * ## ¿Cómo funciona?
 * 1. Cada borrador se guarda con clave "draft_{entryId}"
 * 2. Se serializa a JSON usando kotlinx.serialization
 * 3. DataStore persiste en disco de forma asíncrona y segura
 *
 * ## Analogía:
 * Es como un autoguardado de Word. Mientras escribes, se va guardando
 * en segundo plano. Si se va la luz, al volver abres el archivo y
 * está tu trabajo (o casi todo).
 */
class DraftNoteManager(private val context: Context) {

    // Companion object para crear el DataStore una sola vez (singleton por proceso)
    companion object {
        // Extension property que crea el DataStore
        // "note_drafts" es el nombre del archivo en disco
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
            name = "note_drafts"
        )

        // JSON configurado para ser tolerante a cambios en el modelo
        private val json = Json {
            ignoreUnknownKeys = true  // Si agregamos campos nuevos, no crashea
            encodeDefaults = true     // Incluye valores por defecto al serializar
        }
    }

    /**
     * 💾 Guarda un borrador de nota
     *
     * @param draft El borrador a guardar
     *
     * ## Flujo:
     * 1. Serializa el objeto DraftNote a JSON string
     * 2. Crea una clave única "draft_{entryId}"
     * 3. Guarda en DataStore (escribe en disco de forma asíncrona)
     */
    suspend fun saveDraft(draft: DraftNote) {
        val key = stringPreferencesKey("draft_${draft.entryId}")
        val jsonString = json.encodeToString(draft)

        context.dataStore.edit { preferences ->
            preferences[key] = jsonString
        }
    }

    /**
     * 📖 Recupera un borrador si existe
     *
     * @param entryId ID de la entrada del historial
     * @return El borrador guardado, o null si no existe
     *
     * ## Flujo:
     * 1. Busca la clave "draft_{entryId}" en DataStore
     * 2. Si existe, deserializa el JSON a DraftNote
     * 3. Si no existe o hay error, retorna null
     */
    suspend fun getDraft(entryId: String): DraftNote? {
        val key = stringPreferencesKey("draft_$entryId")

        return try {
            val jsonString = context.dataStore.data.first()[key]
            jsonString?.let { json.decodeFromString<DraftNote>(it) }
        } catch (e: Exception) {
            // Si hay error de deserialización (modelo cambió), ignorar
            null
        }
    }

    /**
     * 🗑️ Elimina un borrador (cuando se guarda definitivamente o se cancela)
     *
     * @param entryId ID de la entrada del historial
     */
    suspend fun deleteDraft(entryId: String) {
        val key = stringPreferencesKey("draft_$entryId")

        context.dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    /**
     * 🔍 Observa cambios en un borrador específico (reactivo con Flow)
     *
     * @param entryId ID de la entrada del historial
     * @return Flow que emite el borrador actual (o null si no existe)
     *
     * Útil si quieres mostrar un indicador de "borrador guardado" en tiempo real
     */
    fun observeDraft(entryId: String): Flow<DraftNote?> {
        val key = stringPreferencesKey("draft_$entryId")

        return context.dataStore.data.map { preferences ->
            try {
                preferences[key]?.let { json.decodeFromString<DraftNote>(it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 🧹 Limpia borradores antiguos (más de 24 horas)
     *
     * Llamar periódicamente para no acumular basura en disco
     */
    suspend fun cleanOldDrafts(maxAgeMillis: Long = 24 * 60 * 60 * 1000) {
        val now = System.currentTimeMillis()

        context.dataStore.edit { preferences ->
            val keysToRemove = mutableListOf<Preferences.Key<String>>()

            preferences.asMap().forEach { (key, value) ->
                if (key.name.startsWith("draft_") && value is String) {
                    try {
                        val draft = json.decodeFromString<DraftNote>(value)
                        if (now - draft.lastModified > maxAgeMillis) {
                            @Suppress("UNCHECKED_CAST")
                            keysToRemove.add(key as Preferences.Key<String>)
                        }
                    } catch (e: Exception) {
                        // Borrador corrupto, eliminar
                        @Suppress("UNCHECKED_CAST")
                        keysToRemove.add(key as Preferences.Key<String>)
                    }
                }
            }

            keysToRemove.forEach { preferences.remove(it) }
        }
    }

    /**
     * ❓ Verifica si existe un borrador para una entrada
     */
    suspend fun hasDraft(entryId: String): Boolean {
        return getDraft(entryId) != null
    }
}
