package com.d4vram.pillmate2.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime

// DataStore dedicado a medicamentos e historial (persistimos JSON compactos)
val Context.medicationDataStore: DataStore<Preferences> by preferencesDataStore(name = "medication_storage")

private val MEDICATIONS_KEY = stringPreferencesKey("medications_json")
private val HISTORY_KEY = stringPreferencesKey("history_json")

class MedicationStorage(private val context: Context) {

    // Cargamos lista de medicamentos guardada; si hay problemas devolvemos lista vacía
    suspend fun loadMedications(): List<Medication> = try {
        val preferences = context.medicationDataStore.data.first()
        val json = preferences[MEDICATIONS_KEY] ?: return emptyList()
        parseMedications(json)
    } catch (ioe: IOException) {
        emptyList()
    } catch (jsonError: JSONException) {
        emptyList()
    }

    suspend fun loadHistory(): List<HistoryEntry> = try {
        val preferences = context.medicationDataStore.data.first()
        val json = preferences[HISTORY_KEY] ?: return emptyList()
        parseHistory(json)
    } catch (ioe: IOException) {
        emptyList()
    } catch (jsonError: JSONException) {
        emptyList()
    }

    suspend fun saveMedications(medications: List<Medication>) {
        context.medicationDataStore.edit { prefs ->
            prefs[MEDICATIONS_KEY] = medicationsToJson(medications)
        }
    }

    suspend fun saveHistory(entries: List<HistoryEntry>) {
        context.medicationDataStore.edit { prefs ->
            prefs[HISTORY_KEY] = historyToJson(entries)
        }
    }

    private fun medicationsToJson(medications: List<Medication>): String {
        val array = JSONArray()
        medications.forEach { medication ->
            val obj = JSONObject().apply {
                put("id", medication.id)
                put("name", medication.name)
                put("dose", medication.dose)
                put("type", medication.type)
                put("color", medication.color.toArgb())
                put("emoji", medication.emoji)
                put("takenToday", medication.takenToday)
                put("dailyGoal", medication.dailyGoal)
                put("lastTaken", medication.lastTaken)
                put("isPrn", medication.isPrn)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun historyToJson(entries: List<HistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("medicationName", entry.medicationName)
                put("time", entry.time)
                put("date", entry.date)
                put("color", entry.color.toArgb())
                put("emoji", entry.emoji)
                put("notes", entry.notes)
                put("timestamp", entry.timestamp.toString())
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseMedications(json: String): List<Medication> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    Medication(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        dose = obj.getString("dose"),
                        type = obj.getString("type"),
                        color = Color(obj.getInt("color")),
                        emoji = obj.getString("emoji"),
                        takenToday = obj.getInt("takenToday"),
                        dailyGoal = obj.getInt("dailyGoal"),
                        lastTaken = obj.optString("lastTaken").takeIf { it.isNotEmpty() },
                        isPrn = obj.getBoolean("isPrn")
                    )
                )
            }
        }
    }

    private fun parseHistory(json: String): List<HistoryEntry> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                add(
                    HistoryEntry(
                        id = obj.getString("id"),
                        medicationName = obj.getString("medicationName"),
                        time = obj.getString("time"),
                        date = obj.getString("date"),
                        color = Color(obj.getInt("color")),
                        emoji = obj.getString("emoji"),
                        notes = obj.optString("notes"),
                        timestamp = runCatching { LocalDateTime.parse(obj.getString("timestamp")) }
                            .getOrElse { LocalDateTime.now() }
                    )
                )
            }
        }
    }
}
