package com.d4vram.pillmate2.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY sortOrder ASC")
    fun observeMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications ORDER BY sortOrder ASC")
    suspend fun getAllMedications(): List<MedicationEntity>

    @Query("SELECT COUNT(*) FROM medications")
    suspend fun countMedications(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedication(entity: MedicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedications(entities: List<MedicationEntity>)

    @Update
    suspend fun updateMedications(entities: List<MedicationEntity>)

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun getMedicationById(id: String): MedicationEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM medications")
    suspend fun getMaxSortOrder(): Int

    @Query("UPDATE medications SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM medications")
    suspend fun clear()
}
