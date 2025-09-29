package com.d4vram.pillmate2.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MedicationEntity::class, HistoryEntryEntity::class],
    version = 4,
    exportSchema = false
)
abstract class PillMateDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: PillMateDatabase? = null

        fun getInstance(context: Context): PillMateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PillMateDatabase::class.java,
                    "pillmate.db"
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
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
    }
}
