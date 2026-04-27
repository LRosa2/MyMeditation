package com.mysimplemeditation.app.data.dao

import androidx.room.*
import com.mysimplemeditation.app.data.entities.SessionEntity
import com.mysimplemeditation.app.data.entities.TriggerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions ORDER BY lastUsedTimestamp DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY lastUsedTimestamp DESC")
    suspend fun getAllSessionsList(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultSession(): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY lastUsedTimestamp DESC LIMIT 1")
    suspend fun getLastUsedSession(): SessionEntity?

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("UPDATE sessions SET isDefault = 0")
    suspend fun clearDefaults()

    @Query("UPDATE sessions SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Query("UPDATE sessions SET lastUsedTimestamp = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long)

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    // Triggers
    @Query("SELECT * FROM triggers WHERE sessionId = :sessionId ORDER BY startTimeSeconds")
    fun getTriggersForSession(sessionId: Long): Flow<List<TriggerEntity>>

    @Query("SELECT * FROM triggers WHERE sessionId = :sessionId ORDER BY startTimeSeconds")
    suspend fun getTriggersForSessionSync(sessionId: Long): List<TriggerEntity>

    @Insert
    suspend fun insertTrigger(trigger: TriggerEntity): Long

    @Update
    suspend fun updateTrigger(trigger: TriggerEntity)

    @Delete
    suspend fun deleteTrigger(trigger: TriggerEntity)

    @Query("DELETE FROM triggers WHERE sessionId = :sessionId")
    suspend fun deleteTriggersForSession(sessionId: Long)
}
