package com.mysimplemeditation.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.mysimplemeditation.app.data.AppDatabase
import com.mysimplemeditation.app.data.entities.LogEntryEntity
import com.mysimplemeditation.app.data.entities.TriggerEntity
import com.mysimplemeditation.app.service.TimerService
import com.mysimplemeditation.app.util.AudioHelper
import com.mysimplemeditation.app.util.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TriggerAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                handleAlarm(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling trigger alarm", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarm(context: Context, intent: Intent) {
        val timerPrefs = context.getSharedPreferences("timer_state", Context.MODE_PRIVATE)
        if (!timerPrefs.getBoolean("active", false)) {
            Log.d(TAG, "Timer not active, ignoring alarm")
            return
        }

        val sessionId = timerPrefs.getLong("session_id", -1)
        if (sessionId == -1L) return

        val triggerId = intent.getLongExtra(EXTRA_TRIGGER_ID, -1)
        val triggerTimeSeconds = intent.getIntExtra(EXTRA_TRIGGER_TIME_SECONDS, 0)

        val startTimeMs = timerPrefs.getLong("start_time_ms", 0)
        val totalPauseMs = timerPrefs.getLong("total_pause_ms", 0)
        val isPaused = timerPrefs.getBoolean("is_paused", false)
        val preparationSeconds = timerPrefs.getInt("preparation_seconds", 0)

        if (startTimeMs <= 0) return

        val now = System.currentTimeMillis()
        val elapsedSeconds = ((now - startTimeMs - totalPauseMs) / 1000).toInt().coerceAtLeast(0)
        val sittingElapsed = elapsedSeconds - preparationSeconds

        val firedStr = timerPrefs.getString("fired_triggers", "") ?: ""
        val firedTriggers = if (firedStr.isNotEmpty()) {
            firedStr.split(",").mapNotNull { it.toLongOrNull() }.toMutableSet()
        } else mutableSetOf<Long>()

        if (isPaused) {
            Log.d(TAG, "Timer is paused, rescheduling alarm for trigger $triggerId")
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = createPendingIntent(context, sessionId, triggerId, triggerTimeSeconds)
            val futureTime = now + 30_000
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, futureTime, pi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, futureTime, pi)
            }
            return
        }

        if (triggerId in firedTriggers) {
            Log.d(TAG, "Trigger $triggerId already fired, skipping")
            return
        }

        val isEndTrigger = triggerTimeSeconds == TriggerEntity.TIME_END
        val totalSittingSeconds = timerPrefs.getInt("total_sitting_seconds", -1)
        val isClosedSession = timerPrefs.getBoolean("is_closed_session", false)

        if (isEndTrigger) {
            if (!isClosedSession || totalSittingSeconds < 0) {
                Log.d(TAG, "Not a closed session or no sitting time, skipping end alarm")
                return
            }
            if (sittingElapsed < totalSittingSeconds - 2) {
                Log.d(TAG, "End alarm fired too early ($sittingElapsed < $totalSittingSeconds), rescheduling")
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = createPendingIntent(context, sessionId, triggerId, triggerTimeSeconds)
                val futureTime = startTimeMs + preparationSeconds * 1000L + totalSittingSeconds * 1000L + totalPauseMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, futureTime, pi)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, futureTime, pi)
                }
                return
            }
        } else {
            if (sittingElapsed < triggerTimeSeconds - 2) {
                Log.d(TAG, "Trigger alarm fired too early ($sittingElapsed < $triggerTimeSeconds), rescheduling")
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = createPendingIntent(context, sessionId, triggerId, triggerTimeSeconds)
                val futureTime = startTimeMs + preparationSeconds * 1000L + triggerTimeSeconds * 1000L + totalPauseMs
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, futureTime, pi)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, futureTime, pi)
                }
                return
            }
        }

        Log.d(TAG, "Executing trigger $triggerId (time=$triggerTimeSeconds, sittingElapsed=$sittingElapsed)")
        val db = AppDatabase.getInstance(context)
        val trigger = db.sessionDao().getTriggersForSessionSync(sessionId).find { it.id == triggerId }
        if (trigger == null) {
            Log.w(TAG, "Trigger $triggerId not found in DB")
            return
        }

        firedTriggers.add(triggerId)
        timerPrefs.edit { putString("fired_triggers", firedTriggers.joinToString(",")) }

        val globalMode = timerPrefs.getString("global_mode", "default") ?: "default"
        val useAlarm = timerPrefs.getBoolean("use_alarm", false)
        val volume = timerPrefs.getInt("volume", 80)

        executeTrigger(context, trigger, volume, useAlarm, globalMode)

        if (isEndTrigger) {
            logAndEndSession(context, sessionId, startTimeMs, preparationSeconds, totalSittingSeconds, totalPauseMs, timerPrefs)
        }

        if (!TimerService.isRunning) {
            Log.d(TAG, "TimerService not running, starting recovery")
            val serviceIntent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_START
                putExtra(TimerService.EXTRA_SESSION_ID, sessionId)
                putExtra(TimerService.EXTRA_VOLUME, volume)
                putExtra(TimerService.EXTRA_USE_ALARM, useAlarm)
                putExtra(TimerService.EXTRA_GLOBAL_MODE, globalMode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    private fun executeTrigger(context: Context, trigger: TriggerEntity, volume: Int, useAlarm: Boolean, globalMode: String) {
        val shouldVibrate = trigger.vibrate || globalMode == "vibration_only" || globalMode == "sound_vibration"
        val shouldSound = (trigger.type != "VIBRATE" && globalMode == "default") ||
                globalMode == "sound_only" || globalMode == "sound_vibration"

        if (shouldVibrate) {
            val vibDuration = if (trigger.vibrate) trigger.vibrationDuration else 500
            val vibExec = if (globalMode == "vibration_only" || globalMode == "sound_vibration") {
                SettingsManager(context).vibrationExecutions
            } else trigger.executions
            val vibGap = if (globalMode == "vibration_only" || globalMode == "sound_vibration") {
                SettingsManager(context).vibrationGapMs
            } else trigger.gapMs
            AudioHelper.playVibration(context, vibDuration, vibExec, vibGap)
        }

        if (shouldSound) {
            val useGlobalSound = globalMode == "sound_only" || globalMode == "sound_vibration"
            val soundType = if (useGlobalSound) SettingsManager(context).generalSoundType else trigger.type
            val soundVolume = if (useGlobalSound) SettingsManager(context).generalSoundVolume else volume
            val soundExec = if (useGlobalSound) SettingsManager(context).generalSoundExecutions else trigger.executions
            val soundGap = if (useGlobalSound) SettingsManager(context).generalSoundGapMs else trigger.gapMs

            if (soundType == "BELL") {
                AudioHelper.playBell(context, soundVolume, useAlarm, soundExec, soundGap)
            } else if (soundType == "MP3") {
                val path = if (useGlobalSound) SettingsManager(context).generalSoundMp3Path else (trigger.mp3Path ?: return)
                if (path.isBlank()) return
                AudioHelper.playMp3(context, path, soundVolume, useAlarm, soundExec, soundGap)
            }
        }
    }

    private suspend fun logAndEndSession(
        context: Context,
        sessionId: Long,
        startTimeMs: Long,
        preparationSeconds: Int,
        totalSittingSeconds: Int,
        totalPauseMs: Long,
        timerPrefs: android.content.SharedPreferences
    ) {
        Log.d(TAG, "Logging end-of-session from alarm receiver")
        val db = AppDatabase.getInstance(context)
        val session = db.sessionDao().getSessionById(sessionId) ?: return

        val elapsedSeconds = ((System.currentTimeMillis() - startTimeMs - totalPauseMs) / 1000).toInt().coerceAtLeast(0)
        val durationSeconds = if (elapsedSeconds <= preparationSeconds) {
            0
        } else {
            (elapsedSeconds - preparationSeconds).coerceAtMost(totalSittingSeconds)
        }

        if (durationSeconds > 0) {
            db.logDao().insertLog(
                LogEntryEntity(
                    sessionId = sessionId,
                    sessionName = session.name,
                    startTime = startTimeMs + preparationSeconds * 1000L,
                    durationSeconds = durationSeconds
                )
            )
        }

        timerPrefs.edit {
            putBoolean("active", false)
            putLong("session_id", -1L)
        }

        val endedIntent = Intent(TimerService.ACTION_ENDED).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(endedIntent)
    }

    companion object {
        private const val TAG = "TriggerAlarmReceiver"
        const val EXTRA_TRIGGER_ID = "trigger_id"
        const val EXTRA_TRIGGER_TIME_SECONDS = "trigger_time_seconds"

        fun scheduleAll(context: Context, sessionId: Long, triggers: List<TriggerEntity>, startTimeMs: Long, preparationSeconds: Int, totalSittingSeconds: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else true

            val timerPrefs = context.getSharedPreferences("timer_state", Context.MODE_PRIVATE)
            val totalPauseMs = timerPrefs.getLong("total_pause_ms", 0L)

            for (trigger in triggers) {
                val triggerTimeSeconds = trigger.startTimeSeconds
                val alarmTime = when (triggerTimeSeconds) {
                    TriggerEntity.TIME_START -> startTimeMs + preparationSeconds * 1000L + totalPauseMs
                    TriggerEntity.TIME_END -> {
                        if (totalSittingSeconds > 0 && totalSittingSeconds != Int.MAX_VALUE) {
                            startTimeMs + preparationSeconds * 1000L + totalSittingSeconds * 1000L + totalPauseMs
                        } else continue
                    }
                    else -> startTimeMs + preparationSeconds * 1000L + triggerTimeSeconds * 1000L + totalPauseMs
                }

                val pi = createPendingIntent(context, sessionId, trigger.id, triggerTimeSeconds)
                try {
                    if (canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pi)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pi)
                    }
                    Log.d(TAG, "Scheduled trigger ${trigger.id} at $alarmTime (offset=${triggerTimeSeconds}s)")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Cannot schedule exact alarm for trigger ${trigger.id}, falling back", e)
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pi)
                }
            }
        }

        fun cancelAll(context: Context, triggers: List<TriggerEntity>) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (trigger in triggers) {
                val pi = createPendingIntent(context, -1, trigger.id, trigger.startTimeSeconds)
                alarmManager.cancel(pi)
                pi.cancel()
            }
            Log.d(TAG, "Cancelled ${triggers.size} trigger alarms")
        }

        private fun createPendingIntent(context: Context, sessionId: Long, triggerId: Long, triggerTimeSeconds: Int): PendingIntent {
            val intent = Intent(context, TriggerAlarmReceiver::class.java).apply {
                action = "com.mysimplemeditation.app.TRIGGER_ALARM"
                putExtra(EXTRA_TRIGGER_ID, triggerId)
                putExtra(EXTRA_TRIGGER_TIME_SECONDS, triggerTimeSeconds)
                putExtra(TimerService.EXTRA_SESSION_ID, sessionId)
            }
            return PendingIntent.getBroadcast(
                context,
                triggerId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
