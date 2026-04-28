package com.mysimplemeditation.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mysimplemeditation.app.R
import com.mysimplemeditation.app.data.AppDatabase
import com.mysimplemeditation.app.data.entities.LogEntryEntity
import com.mysimplemeditation.app.data.entities.TriggerEntity
import com.mysimplemeditation.app.data.entities.TriggerEntity.Companion.TIME_END
import com.mysimplemeditation.app.data.entities.TriggerEntity.Companion.TIME_START
import com.mysimplemeditation.app.ui.main.MainActivity
import com.mysimplemeditation.app.util.AudioHelper
import com.mysimplemeditation.app.util.SettingsManager
import kotlinx.coroutines.*
import java.util.Calendar

class TimerService : Service() {

    companion object {
        const val ACTION_START = "com.mysimplemeditation.app.START"
        const val ACTION_STOP = "com.mysimplemeditation.app.STOP"
        const val ACTION_TICK = "com.mysimplemeditation.app.TICK"
        const val ACTION_STARTED = "com.mysimplemeditation.app.STARTED"
        const val ACTION_STOPPED = "com.mysimplemeditation.app.STOPPED"
        const val ACTION_ENDED = "com.mysimplemeditation.app.ENDED"

        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_VOLUME = "extra_volume"
        const val EXTRA_USE_ALARM = "extra_use_alarm"
        const val EXTRA_GLOBAL_MODE = "extra_global_mode"
        const val EXTRA_REMAINING = "remaining"
        const val EXTRA_ELAPSED = "elapsed"
        const val EXTRA_PHASE = "phase"

        private const val CHANNEL_ID = "mymeditation_timer"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isRunning = false
            private set
        @Volatile
        var lastRemaining = 0
            private set
        @Volatile
        var lastElapsed = 0
            private set
        @Volatile
        var lastPhase = ""
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null
    private var db: AppDatabase? = null
    private lateinit var settings: SettingsManager

    private var sessionId: Long = -1
    private var volume: Int = 80
    private var useAlarm: Boolean = false
    private var globalMode: String = "default"
    private var isClosedSession: Boolean = false
    private var preparationSeconds: Int = 0
    private var totalSittingSeconds: Int = 0
    private var triggers: List<TriggerEntity> = emptyList()
    private var firedTriggers: MutableSet<Long> = mutableSetOf()
    private var repeatingTriggerTimers: MutableMap<Long, Job> = mutableMapOf()

    private var currentPhase: String = ""
    private var elapsedSeconds: Int = 0
    private var startTimeMs: Long = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        settings = SettingsManager(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
                volume = intent.getIntExtra(EXTRA_VOLUME, 80)
                useAlarm = intent.getBooleanExtra(EXTRA_USE_ALARM, false)
                globalMode = intent.getStringExtra(EXTRA_GLOBAL_MODE) ?: "default"
                startTimer()
            }
            ACTION_STOP -> {
                stopTimer()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        timerJob?.cancel()
        repeatingTriggerTimers.values.forEach { it.cancel() }
        repeatingTriggerTimers.clear()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTimer() {
        serviceScope.launch {
            val session = db?.sessionDao()?.getSessionById(sessionId) ?: return@launch
            isClosedSession = session.type == "CLOSED"
            preparationSeconds = session.preparationMinutes * 60 + session.preparationSeconds
            totalSittingSeconds = if (isClosedSession) {
                session.sittingMinutes * 60 + session.sittingSeconds
            } else Int.MAX_VALUE

            triggers = db?.sessionDao()?.getTriggersForSessionSync(sessionId) ?: emptyList()
            firedTriggers.clear()
            repeatingTriggerTimers.clear()

            // Update last used
            db?.sessionDao()?.updateLastUsed(sessionId, System.currentTimeMillis())
            settings.lastSessionId = sessionId

            // Acquire wake lock
            acquireWakeLock()

            // Start foreground
            startForeground(NOTIFICATION_ID, buildNotification("Meditation starting..."))

            startTimeMs = System.currentTimeMillis()
            elapsedSeconds = 0
            currentPhase = if (preparationSeconds > 0) "Preparing" else "Sitting"

            sendBroadcast(Intent(ACTION_STARTED).setPackage(packageName))
            isRunning = true

            timerJob = serviceScope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(1000)
                    elapsedSeconds++

                    val totalSessionSeconds = preparationSeconds + totalSittingSeconds
                    val remaining = if (isClosedSession) {
                        totalSessionSeconds - elapsedSeconds
                    } else 0

                    // Determine phase
                    currentPhase = if (elapsedSeconds <= preparationSeconds) {
                        "Preparing"
                    } else {
                        "Sitting"
                    }

                    // Check if session ended
                    if (isClosedSession && remaining <= 0) {
                        currentPhase = "Ended"
                        withContext(Dispatchers.Main) {
                            endSession()
                        }
                        break
                    }

                    // Check triggers
                    val sittingElapsed = elapsedSeconds - preparationSeconds
                    if (sittingElapsed == 1) {
                        // Execute START triggers at the beginning of sitting phase
                        executeStartTriggers()
                    }
                    if (sittingElapsed > 0) {
                        checkTriggers(sittingElapsed)
                    }

                    // Send tick broadcast
                    val tickIntent = Intent(ACTION_TICK).apply {
                        putExtra(EXTRA_REMAINING, remaining.coerceAtLeast(0))
                        putExtra(EXTRA_ELAPSED, elapsedSeconds)
                        putExtra(EXTRA_PHASE, currentPhase)
                        setPackage(packageName)
                    }
                    sendBroadcast(tickIntent)

                    lastRemaining = remaining.coerceAtLeast(0)
                    lastElapsed = elapsedSeconds
                    lastPhase = currentPhase

                    // Update notification
                    val notifText = if (isClosedSession) {
                        "Remaining: ${formatTime(remaining.coerceAtLeast(0))}"
                    } else {
                        "Elapsed: ${formatTime(elapsedSeconds)}"
                    }
                    withContext(Dispatchers.Main) {
                        updateNotification(notifText)
                    }
                }
            }
        }
    }

    private suspend fun checkTriggers(sittingElapsed: Int) {
        for (trigger in triggers) {
            // Skip START and END triggers - they are handled separately
            if (trigger.startTimeSeconds == TIME_START || trigger.startTimeSeconds == TIME_END) continue
            if (trigger.id in firedTriggers && !trigger.repeating) continue
            if (trigger.id in firedTriggers && trigger.repeating) continue // handled by repeating timer

            if (sittingElapsed >= trigger.startTimeSeconds && trigger.id !in firedTriggers) {
                firedTriggers.add(trigger.id)
                executeTrigger(trigger)

                // Set up repeating if needed
                if (trigger.repeating) {
                    val intervalSeconds = trigger.repeatIntervalMinutes * 60
                    val job = serviceScope.launch(Dispatchers.IO) {
                        while (isActive) {
                            delay(intervalSeconds * 1000L)
                            val currentSittingElapsed = elapsedSeconds - preparationSeconds
                            if (isClosedSession && currentSittingElapsed >= totalSittingSeconds) break
                            executeTrigger(trigger)
                        }
                    }
                    repeatingTriggerTimers[trigger.id] = job
                }
            }
        }
    }

    private fun executeStartTriggers() {
        for (trigger in triggers) {
            if (trigger.startTimeSeconds == TIME_START && trigger.id !in firedTriggers) {
                firedTriggers.add(trigger.id)
                executeTrigger(trigger)
            }
        }
    }

    private fun executeEndTriggers() {
        for (trigger in triggers) {
            if (trigger.startTimeSeconds == TIME_END && trigger.id !in firedTriggers) {
                firedTriggers.add(trigger.id)
                executeTrigger(trigger)
            }
        }
    }

    private fun executeTrigger(trigger: TriggerEntity) {
        val shouldVibrate = trigger.vibrate || globalMode == "vibration_only" || globalMode == "sound_vibration"
        val shouldSound = (trigger.type != "VIBRATE" && globalMode == "default") ||
            globalMode == "sound_only" || globalMode == "sound_vibration"

        if (shouldVibrate) {
            val vibDuration = if (trigger.vibrate) trigger.vibrationDuration else 500
            val vibExec = if (globalMode == "vibration_only" || globalMode == "sound_vibration") {
                SettingsManager(this@TimerService).vibrationExecutions
            } else {
                trigger.executions
            }
            val vibGap = if (globalMode == "vibration_only" || globalMode == "sound_vibration") {
                SettingsManager(this@TimerService).vibrationGapMs
            } else {
                trigger.gapMs
            }
            AudioHelper.playVibration(
                context = this@TimerService,
                durationMs = vibDuration,
                executions = vibExec,
                gapMs = vibGap
            )
        }

        if (shouldSound) {
            val useGlobalSound = globalMode == "sound_only" || globalMode == "sound_vibration"
            val soundType = if (useGlobalSound) SettingsManager(this@TimerService).generalSoundType else trigger.type
            val soundVolume = if (useGlobalSound) SettingsManager(this@TimerService).generalSoundVolume else trigger.volume
            val soundExec = if (useGlobalSound) SettingsManager(this@TimerService).generalSoundExecutions else trigger.executions
            val soundGap = if (useGlobalSound) SettingsManager(this@TimerService).generalSoundGapMs else trigger.gapMs

            if (soundType == "BELL") {
                AudioHelper.playBell(
                    context = this@TimerService,
                    volume = soundVolume,
                    useAlarmStream = useAlarm,
                    executions = soundExec,
                    gapMs = soundGap
                )
            } else if (soundType == "MP3") {
                val path = if (useGlobalSound) SettingsManager(this@TimerService).generalSoundMp3Path else (trigger.mp3Path ?: return)
                if (path.isBlank()) return
                AudioHelper.playMp3(
                    context = this,
                    path = path,
                    volume = soundVolume,
                    useAlarmStream = useAlarm,
                    executions = soundExec,
                    gapMs = soundGap
                )
            }
        }
    }

    private suspend fun endSession() {
        executeEndTriggers()
        logSession()
        isRunning = false
        lastRemaining = 0
        lastElapsed = 0
        lastPhase = ""
        sendBroadcast(Intent(ACTION_ENDED).setPackage(packageName))
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopTimer() {
        serviceScope.launch {
            executeEndTriggers()
            logSession()
            timerJob?.cancel()
            repeatingTriggerTimers.values.forEach { it.cancel() }
            repeatingTriggerTimers.clear()
            isRunning = false
            lastRemaining = 0
            lastElapsed = 0
            lastPhase = ""
            sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun logSession() {
        if (startTimeMs <= 0) return
        val session = db?.sessionDao()?.getSessionById(sessionId) ?: return

        val durationSeconds = if (isClosedSession) {
            // For closed sessions, use actual elapsed minus prep, capped at sitting time
            val sittingDone = (elapsedSeconds - preparationSeconds).coerceAtLeast(0)
            sittingDone.coerceAtMost(totalSittingSeconds)
        } else {
            // For open sessions, total elapsed minus prep
            (elapsedSeconds - preparationSeconds).coerceAtLeast(0)
        }

        if (durationSeconds > 0) {
            db?.logDao()?.insertLog(
                LogEntryEntity(
                    sessionId = sessionId,
                    sessionName = session.name,
                    startTime = startTimeMs,
                    durationSeconds = durationSeconds
                )
            )
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MyMeditation::TimerWakeLock"
        )
        wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours max
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun formatTime(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
