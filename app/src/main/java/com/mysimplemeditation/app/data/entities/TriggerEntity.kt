package com.mysimplemeditation.app.data.entities

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
    val startTimeSeconds: Int = 0, // -1 = END (execute when meditation ends), 0 = START
    val type: String = "BELL", // "BELL" or "MP3"
    val mp3Path: String? = null,
    val volume: Int = 80, // 0-100
    val repeating: Boolean = false,
    val repeatIntervalMinutes: Int = 5,
    val executions: Int = 1,
    val gapMs: Int = 500,
    val vibrate: Boolean = false,
    val vibrationDuration: Int = 500 // ms
) {
    companion object {
        const val TIME_START = 0
        const val TIME_END = -1

        fun isSpecialTime(seconds: Int): Boolean = seconds == TIME_START || seconds == TIME_END

        fun formatTimeLabel(seconds: Int): String = when (seconds) {
            TIME_START -> "Start"
            TIME_END -> "End"
            else -> {
                val h = seconds / 3600
                val m = (seconds % 3600) / 60
                val s = seconds % 60
                if (h > 0) String.format("%dH%02dM%02dS", h, m, s)
                else if (m > 0) String.format("%dM%02dS", m, s)
                else String.format("%dS", s)
            }
        }
    }
}
