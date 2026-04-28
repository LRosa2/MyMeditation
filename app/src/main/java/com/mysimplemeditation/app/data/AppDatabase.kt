package com.mysimplemeditation.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mysimplemeditation.app.data.dao.LogDao
import com.mysimplemeditation.app.data.dao.ReminderDao
import com.mysimplemeditation.app.data.dao.SessionDao
import com.mysimplemeditation.app.data.entities.LogEntryEntity
import com.mysimplemeditation.app.data.entities.ReminderEntity
import com.mysimplemeditation.app.data.entities.SessionEntity
import com.mysimplemeditation.app.data.entities.TriggerEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE triggers ADD COLUMN vibrate INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE triggers ADD COLUMN vibrationDuration INTEGER NOT NULL DEFAULT 500")
    }
}

@Database(
    entities = [
        SessionEntity::class,
        TriggerEntity::class,
        LogEntryEntity::class,
        ReminderEntity::class
    ],
    version = 2,
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
