package com.d4vram.pillmate2.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MedicationEntity::class, HistoryEntryEntity::class, VoiceNoteEntity::class],
    version = 8,
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
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { INSTANCE = it }
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
                database.execSQL(
                    "UPDATE history " +
                        "SET medicationId = COALESCE(" +
                        "(SELECT id FROM medications WHERE name = history.medicationName LIMIT 1), " +
                        "medicationId)"
                )
            }
        }
    }
}
