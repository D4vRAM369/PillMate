package com.d4vram.pillmate2.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MedicationEntity::class, HistoryEntryEntity::class],
    version = 2,
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
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
