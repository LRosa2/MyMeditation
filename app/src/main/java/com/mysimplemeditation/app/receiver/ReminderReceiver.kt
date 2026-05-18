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

        fun hasExactAlarmPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        }

        fun schedule(context: Context, reminder: ReminderEntity) {
            if (!reminder.enabled) {
                Log.d(TAG, "Reminder ${reminder.id} is disabled, cancelling instead of scheduling")
                cancel(context, reminder)
                return
            }

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

            val canExact = hasExactAlarmPermission(context)

            try {
                if (canExact) {
                    // Use setAlarmClock for maximum reliability - shows alarm icon in status bar
                    // This is the most reliable way to wake the device from Doze
                    val alarmTime = calendar.timeInMillis
                    val info = AlarmManager.AlarmClockInfo(alarmTime, pendingIntent)
                    alarmManager.setAlarmClock(info, pendingIntent)
                    Log.d(TAG, "Scheduled AlarmClock for reminder ${reminder.id} at ${calendar.time} (reliable)")
                } else {
                    // Fallback to inexact alarm - less reliable
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    Log.w(TAG, "Scheduled inexact alarm for reminder ${reminder.id} at ${calendar.time} (NO EXACT PERMISSION - unreliable!)")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException scheduling reminder ${reminder.id}, falling back to inexact", e)
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        }

        suspend fun rescheduleAll(context: Context) {
            val db = AppDatabase.getInstance(context)
            val reminders = db.reminderDao().getEnabledReminders()
            Log.d(TAG, "Rescheduling ${reminders.size} enabled reminders")
            for (reminder in reminders) {
                schedule(context, reminder)
            }
        }

        fun testNotification(context: Context, message: String) {
            val receiver = ReminderReceiver()
            receiver.createNotificationChannel(context)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.reminder_notification_title))
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(Int.MAX_VALUE, notification)
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
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for reminder ${reminder.id}")
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        Log.d(TAG, "onReceive triggered for reminderId=$reminderId")
        if (reminderId < 0) {
            Log.w(TAG, "Invalid reminderId, ignoring")
            return
        }

        scope.launch {
            val db = AppDatabase.getInstance(context)
            val reminder = db.reminderDao().getReminderById(reminderId)
            if (reminder == null) {
                Log.w(TAG, "Reminder $reminderId not found in database")
                return@launch
            }
            Log.d(TAG, "Found reminder: id=${reminder.id}, msg='${reminder.message}', threshold=${reminder.thresholdMinutes}min, enabled=${reminder.enabled}")

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

            Log.d(TAG, "Threshold check: totalMinutesToday=$totalMinutesToday, threshold=${reminder.thresholdMinutes}, shouldNotify=${totalMinutesToday < reminder.thresholdMinutes}")

            if (totalMinutesToday < reminder.thresholdMinutes) {
                Log.d(TAG, "Threshold not met, showing notification for reminder ${reminder.id}")
                showNotification(context, reminder)
            } else {
                Log.d(TAG, "Threshold met (${totalMinutesToday} >= ${reminder.thresholdMinutes}), skipping notification")
            }

            // Reschedule for tomorrow
            Log.d(TAG, "Rescheduling reminder ${reminder.id} for tomorrow")
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
