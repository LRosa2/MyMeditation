package com.mymeditation.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val timeHour: Int = 7,
    val timeMinute: Int = 0,
    val thresholdMinutes: Int = 30,
    val enabled: Boolean = true
)
