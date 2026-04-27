package com.mysimplemeditation.app.data.dao

import androidx.room.*
import com.mysimplemeditation.app.data.entities.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Insert
    suspend fun insertLog(logEntry: LogEntryEntity): Long

    @Query("SELECT * FROM log_entries ORDER BY startTime DESC")
    fun getAllLogs(): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries ORDER BY startTime DESC")
    suspend fun getAllLogsList(): List<LogEntryEntity>

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

    @Query("SELECT DATE(startTime / 1000, 'unixepoch', 'localtime') AS day, SUM(durationSeconds) AS totalSeconds FROM log_entries GROUP BY day ORDER BY day ASC")
    suspend fun getDailyTotals(): List<DailyTotal>

    @Delete
    suspend fun deleteLog(logEntry: LogEntryEntity)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteLogById(id: Long)
}

data class DailyTotal(
    val day: String,
    val totalSeconds: Int
)
