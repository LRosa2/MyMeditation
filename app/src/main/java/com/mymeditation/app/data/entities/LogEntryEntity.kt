package com.mymeditation.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "log_entries",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("sessionId"), Index("startTime")]
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long?,
    val sessionName: String,
    val startTime: Long,
    val durationSeconds: Int
)
