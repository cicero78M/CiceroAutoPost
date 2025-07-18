package com.cicero.socialtools.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntry)

    @Query("SELECT * FROM log_entries WHERE user = :user ORDER BY id ASC")
    suspend fun logsForUser(user: String): List<LogEntry>

    @Query("SELECT * FROM log_entries ORDER BY id ASC")
    suspend fun allLogs(): List<LogEntry>

    @Query("DELETE FROM log_entries WHERE user = :user")
    suspend fun clearForUser(user: String)
}
