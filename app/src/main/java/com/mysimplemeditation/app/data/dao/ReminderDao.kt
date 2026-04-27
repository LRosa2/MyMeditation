package com.mysimplemeditation.app.data.dao

import androidx.room.*
import com.mysimplemeditation.app.data.entities.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY timeHour, timeMinute")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY timeHour, timeMinute")
    suspend fun getAllRemindersList(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getEnabledReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Insert
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)
}
