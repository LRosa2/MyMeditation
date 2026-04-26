package com.mymeditation.app.data.dao

import androidx.room.*
import com.mymeditation.app.data.entities.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Insert
    suspend fun insertLog(logEntry: LogEntryEntity): Long

    @Query("SELECT * FROM log_entries ORDER BY startTime DESC")
    fun getAllLogs(): Flow<List<LogEntryEntity>>

    @Query("SELECT SUM(durationSeconds) FROM log_entries WHERE startTime >= :startOfDay AND startTime < :endOfDay")
    suspend fun getTotalSecondsForDay(startOfDay: Long, endOfDay: Long): Int?

    @Query("SELECT SUM(durationSeconds) FROM log_entries WHERE startTime >= :startOfWeek")
    suspend fun getTotalSecondsSince(startOfWeek: Long): Int?

    @Query("SELECT SUM(durationSeconds) FROM log_entries")
    suspend fun getTotalSecondsAllTime(): Int?

    @Query("SELECT COUNT(*) FROM log_entries")
    suspend fun getSessionCountAllTime(): Int?

    @Query("SELECT COUNT(*) FROM log_entries WHERE startTime >= :since")
    suspend fun getSessionCountSince(since: Long): Int?
}
