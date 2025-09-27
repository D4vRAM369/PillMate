package com.d4vram.pillmate2.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntryEntity(
    @PrimaryKey val id: String,
    val medicationName: String,
    val time: String,
    val date: String,
    val colorArgb: Int,
    val emoji: String,
    val quantity: Int,
    val notes: String,
    val timestampIso: String
)
