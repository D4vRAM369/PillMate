package com.d4vram.pillmate2.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

@Database(
    entities = [MedicationEntity::class, HistoryEntryEntity::class, VoiceNoteEntity::class],
    version = 13,
    exportSchema = false
)
abstract class PillMateDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun historyDao(): HistoryDao
    abstract fun voiceNoteDao(): VoiceNoteDao

    companion object {
        @Volatile
        private var INSTANCE: PillMateDatabase? = null

        fun getInstance(context: Context): PillMateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PillMateDatabase::class.java,
                    "pillmate.db"
                ).addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_11_12,
                    MIGRATION_12_13
                ).build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE medications ADD COLUMN scheduleTime TEXT")
                database.execSQL("ALTER TABLE medications ADD COLUMN scheduleDays TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE history ADD COLUMN dose TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS voice_notes (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "title TEXT NOT NULL, " +
                        "createdAtEpochMillis INTEGER NOT NULL, " +
                        "updatedAtEpochMillis INTEGER NOT NULL, " +
                        "durationMillis INTEGER NOT NULL, " +
                        "encryptedFileName TEXT NOT NULL, " +
                        "noteSaltBase64 TEXT NOT NULL, " +
                        "noteIvBase64 TEXT NOT NULL, " +
                        "fileSizeBytes INTEGER NOT NULL" +
                        ")"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE voice_notes ADD COLUMN category TEXT NOT NULL DEFAULT 'DEFAULT'")
                database.execSQL("ALTER TABLE voice_notes ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE history ADD COLUMN medicationId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                updateHistoryMedicationIds(database)
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                updateHistoryMedicationIds(database)
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Añadir campo audioNoteUri para notas de voz en historial
                database.execSQL("ALTER TABLE history ADD COLUMN audioNoteUri TEXT")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Añadir timestamp de creación para permitir ordenamiento por fecha
                // Usamos System.currentTimeMillis() como valor por defecto para medicamentos existentes
                database.execSQL("ALTER TABLE medications ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 📸 Añadir campos para fotografías de medicamentos
                database.execSQL("ALTER TABLE medications ADD COLUMN photoPath TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE medications ADD COLUMN usePhotoInstead INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 🔗 Añadir campo para vincular medicamentos (padre/hijo)
                database.execSQL("ALTER TABLE medications ADD COLUMN parentMedicationId TEXT DEFAULT NULL")
            }
        }

        private fun updateHistoryMedicationIds(database: SupportSQLiteDatabase) {
            data class MedicationRow(
                val id: String,
                val nameKey: String,
                val dose: String?,
                val emoji: String,
                val colorArgb: Int
            )

            val medicationsByName = mutableMapOf<String, MutableList<MedicationRow>>()
            database.query(
                "SELECT id, name, dose, emoji, colorArgb FROM medications"
            ).use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("id")
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val doseIndex = cursor.getColumnIndexOrThrow("dose")
                val emojiIndex = cursor.getColumnIndexOrThrow("emoji")
                val colorIndex = cursor.getColumnIndexOrThrow("colorArgb")

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex)
                    val dose = if (cursor.isNull(doseIndex)) null else cursor.getString(doseIndex)
                    val emoji = cursor.getString(emojiIndex)
                    val colorArgb = cursor.getInt(colorIndex)
                    val key = name.normalizeKey()
                    val row = MedicationRow(id, key, dose?.takeIf { it.isNotBlank() }, emoji, colorArgb)
                    medicationsByName.getOrPut(key) { mutableListOf() }.add(row)
                }
            }

            if (medicationsByName.isEmpty()) {
                return
            }

            database.compileStatement(
                "UPDATE history SET medicationId = ? WHERE id = ?"
            ).use { updateStatement ->
                database.query(
                    "SELECT id, medicationId, medicationName, dose, emoji, colorArgb FROM history"
                ).use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val medicationIdIndex = cursor.getColumnIndexOrThrow("medicationId")
                    val nameIndex = cursor.getColumnIndexOrThrow("medicationName")
                    val doseIndex = cursor.getColumnIndexOrThrow("dose")
                    val emojiIndex = cursor.getColumnIndexOrThrow("emoji")
                    val colorIndex = cursor.getColumnIndexOrThrow("colorArgb")

                    while (cursor.moveToNext()) {
                        val historyId = cursor.getString(idIndex)
                        val currentMedicationId = cursor.getString(medicationIdIndex) ?: ""
                        val medicationName = cursor.getString(nameIndex)
                        val historyDose = if (cursor.isNull(doseIndex)) null else cursor.getString(doseIndex)
                        val historyEmoji = cursor.getString(emojiIndex)
                        val historyColor = cursor.getInt(colorIndex)
                        val key = medicationName.normalizeKey()

                        val candidates = medicationsByName[key] ?: continue

                        val trimmedDose = historyDose?.takeIf { it.isNotBlank() }
                        val exactDoseMatch = trimmedDose?.let { doseValue ->
                            candidates.firstOrNull { it.dose.equals(doseValue, ignoreCase = false) }
                        }
                        val emojiMatch = candidates.firstOrNull { it.emoji == historyEmoji }
                        val colorMatch = candidates.firstOrNull { it.colorArgb == historyColor }
                        val fallbackMatch = candidates.firstOrNull()

                        val matchedMedicationId = exactDoseMatch?.id
                            ?: emojiMatch?.id
                            ?: colorMatch?.id
                            ?: fallbackMatch?.id

                        if (!matchedMedicationId.isNullOrEmpty() && matchedMedicationId != currentMedicationId) {
                            updateStatement.bindString(1, matchedMedicationId)
                            updateStatement.bindString(2, historyId)
                            updateStatement.executeUpdateDelete()
                            updateStatement.clearBindings()
                        }
                    }
                }
            }
        }

        private fun String.normalizeKey(): String =
            trim().lowercase(Locale.ROOT)
    }
}
