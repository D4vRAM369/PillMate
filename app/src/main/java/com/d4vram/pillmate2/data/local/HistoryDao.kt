package com.d4vram.pillmate2.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestampIso DESC")
    fun observeHistory(): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history ORDER BY timestampIso DESC")
    suspend fun getAllHistory(): List<HistoryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HistoryEntryEntity>)

    @Query("UPDATE history SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}
