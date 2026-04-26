package com.mymeditation.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mymeditation.app.data.dao.LogDao
import com.mymeditation.app.data.dao.ReminderDao
import com.mymeditation.app.data.dao.SessionDao
import com.mymeditation.app.data.entities.LogEntryEntity
import com.mymeditation.app.data.entities.ReminderEntity
import com.mymeditation.app.data.entities.SessionEntity
import com.mymeditation.app.data.entities.TriggerEntity

@Database(
    entities = [
        SessionEntity::class,
        TriggerEntity::class,
        LogEntryEntity::class,
        ReminderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun logDao(): LogDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymeditation_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
