package com.mymeditation.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "CLOSED" or "OPEN"
    val preparationMinutes: Int = 0,
    val preparationSeconds: Int = 0,
    val sittingMinutes: Int = 0,
    val sittingSeconds: Int = 0,
    val isDefault: Boolean = false,
    val lastUsedTimestamp: Long = 0L
)
