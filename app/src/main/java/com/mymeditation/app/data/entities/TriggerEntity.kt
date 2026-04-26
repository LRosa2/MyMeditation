package com.mymeditation.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "triggers",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class TriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startTimeSeconds: Int = 0,
    val type: String = "BELL", // "BELL" or "MP3"
    val mp3Path: String? = null,
    val volume: Int = 80, // 0-100
    val repeating: Boolean = false,
    val repeatIntervalMinutes: Int = 5,
    val executions: Int = 1,
    val gapMs: Int = 500
)
