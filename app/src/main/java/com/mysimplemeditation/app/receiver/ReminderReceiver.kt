package com.mysimplemeditation.app.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mysimplemeditation.app.R
import com.mysimplemeditation.app.data.AppDatabase
import com.mysimplemeditation.app.data.entities.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "mymeditation_reminders"
        private const val EXTRA_REMINDER_ID = "reminder_id"

        fun schedule(context: Context, reminder: ReminderEntity) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminder.id)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, reminder.timeHour)
                set(Calendar.MINUTE, reminder.timeMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If the time is in the past today, schedule for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        // Fallback to inexact alarm
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot schedule exact alarm", e)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            // Reschedule for next day (repeating daily)
            // We'll reschedule in onReceive after the alarm fires
        }

        fun cancel(context: Context, reminder: ReminderEntity) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        if (reminderId < 0) return

        scope.launch {
            val db = AppDatabase.getInstance(context)
            val reminder = db.reminderDao().getReminderById(reminderId) ?: return@launch

            // Check threshold: only show if meditation today < threshold
            val now = Calendar.getInstance()
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val totalSecondsToday = db.logDao().getTotalSecondsForDay(
                startOfDay.timeInMillis,
                now.timeInMillis
            ) ?: 0

            val totalMinutesToday = totalSecondsToday / 60

            if (totalMinutesToday < reminder.thresholdMinutes) {
                showNotification(context, reminder)
            }

            // Reschedule for tomorrow
            schedule(context, reminder)
        }
    }

    private fun showNotification(context: Context, reminder: ReminderEntity) {
        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(reminder.message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(reminder.id.toInt(), notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meditation Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
