package com.d4vram.pillmate2.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {
    @Query("SELECT * FROM voice_notes ORDER BY createdAtEpochMillis DESC")
    fun observeVoiceNotes(): Flow<List<VoiceNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(voiceNote: VoiceNoteEntity)

    @Update
    suspend fun update(voiceNote: VoiceNoteEntity)

    @Delete
    suspend fun delete(voiceNote: VoiceNoteEntity)

    @Query("SELECT * FROM voice_notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VoiceNoteEntity?
}
