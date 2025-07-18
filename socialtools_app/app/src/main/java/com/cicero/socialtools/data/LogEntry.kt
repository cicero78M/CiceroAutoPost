package com.cicero.socialtools.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_entries")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val user: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
