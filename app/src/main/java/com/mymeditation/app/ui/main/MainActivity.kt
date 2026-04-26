package com.mymeditation.app.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mymeditation.app.R
import com.mymeditation.app.data.AppDatabase
import com.mymeditation.app.data.entities.SessionEntity
import com.mymeditation.app.databinding.ActivityMainBinding
import com.mymeditation.app.service.TimerService
import com.mymeditation.app.ui.reminders.RemindersActivity
import com.mymeditation.app.ui.sessions.SessionsActivity
import com.mymeditation.app.ui.settings.SettingsActivity
import com.mymeditation.app.ui.stats.StatisticsActivity
import com.mymeditation.app.util.AudioHelper
import com.mymeditation.app.util.SettingsManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsManager
    private var sessions = listOf<SessionEntity>()
    private var selectedSession: SessionEntity? = null
    private var isTimerRunning = false

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                TimerService.ACTION_TICK -> {
                    val remaining = intent.getIntExtra(TimerService.EXTRA_REMAINING, 0)
                    val elapsed = intent.getIntExtra(TimerService.EXTRA_ELAPSED, 0)
                    val phase = intent.getStringExtra(TimerService.EXTRA_PHASE) ?: ""
                    updateTimerDisplay(remaining, elapsed, phase)
                }
                TimerService.ACTION_STARTED -> {
                    isTimerRunning = true
                    updateButtonStates()
                }
                TimerService.ACTION_STOPPED -> {
                    isTimerRunning = false
                    updateButtonStates()
                    binding.txtTimer.text = "00:00:00"
                    binding.txtPhase.text = ""
                }
                TimerService.ACTION_ENDED -> {
                    isTimerRunning = false
                    updateButtonStates()
                    binding.txtTimer.text = "00:00:00"
                    binding.txtPhase.text = getString(R.string.session_ended)
                }
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)
        settings = SettingsManager(this)

        requestNotificationPermission()
        requestAlarmPermission()

        setupVolumeSlider()
        setupButtons()

        lifecycleScope.launch {
            loadSessions()
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(TimerService.ACTION_TICK)
            addAction(TimerService.ACTION_STARTED)
            addAction(TimerService.ACTION_STOPPED)
            addAction(TimerService.ACTION_ENDED)
        }
        registerReceiver(timerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        lifecycleScope.launch {
            loadSessions()
        }

        // Restore volume if setting enabled
        if (settings.rememberVolume) {
            AudioHelper.restoreVolume(this, settings)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(timerReceiver) } catch (_: Exception) {}

        // Save volume if setting enabled
        if (settings.rememberVolume) {
            AudioHelper.saveCurrentVolume(this, settings)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_sessions -> {
                startActivity(Intent(this, SessionsActivity::class.java))
                true
            }
            R.id.menu_reminders -> {
                startActivity(Intent(this, RemindersActivity::class.java))
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_statistics -> {
                startActivity(Intent(this, StatisticsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // User needs to grant permission in settings
                AlertDialog.Builder(this)
                    .setTitle("Alarm Permission")
                    .setMessage("This app needs permission to schedule exact alarms for reliable timer operation. Please grant this permission in the next screen.")
                    .setPositiveButton("OK") { _, _ ->
                        val intent = Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }

    private fun setupVolumeSlider() {
        binding.seekBarVolume.progress = settings.lastVolume
        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settings.lastVolume = progress
                    val streamType = if (settings.playAsAlarm)
                        android.media.AudioManager.STREAM_ALARM
                    else
                        android.media.AudioManager.STREAM_MUSIC
                    AudioHelper.setStreamVolume(this@MainActivity, streamType, progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupButtons() {
        binding.btnStart.setOnClickListener {
            val session = selectedSession ?: return@setOnClickListener
            val volume = binding.seekBarVolume.progress
            val useAlarm = settings.playAsAlarm

            // Save volume before starting
            if (settings.rememberVolume) {
                AudioHelper.saveCurrentVolume(this, settings)
            }

            val intent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_START
                putExtra(TimerService.EXTRA_SESSION_ID, session.id)
                putExtra(TimerService.EXTRA_VOLUME, volume)
                putExtra(TimerService.EXTRA_USE_ALARM, useAlarm)
            }
            startService(intent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            }
        }

        binding.btnStop.setOnClickListener {
            val intent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP
            }
            startService(intent)
        }
    }

    private suspend fun loadSessions() {
        sessions = db.sessionDao().getAllSessionsList()

        if (sessions.isEmpty()) {
            binding.txtSessionInfo.text = getString(R.string.no_sessions)
            return
        }

        val names = sessions.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSession.adapter = adapter

        // Select default or last used session
        val defaultSession = sessions.find { it.isDefault }
        val target = defaultSession ?: sessions.first()
        val index = sessions.indexOf(target)
        binding.spinnerSession.setSelection(index)
        selectedSession = target
        updateSessionInfo(target)

        binding.spinnerSession.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                selectedSession = sessions[pos]
                updateSessionInfo(sessions[pos])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun updateSessionInfo(session: SessionEntity) {
        val type = if (session.type == "CLOSED") "Closed" else "Open"
        val prep = "${session.preparationMinutes}m ${session.preparationSeconds}s prep"
        val sitting = if (session.type == "CLOSED") {
            " | ${session.sittingMinutes}m ${session.sittingSeconds}s sitting"
        } else ""
        binding.txtSessionInfo.text = "$type | $prep$sitting"
    }

    private fun updateTimerDisplay(remaining: Int, elapsed: Int, phase: String) {
        val time = if (selectedSession?.type == "CLOSED") remaining else elapsed
        binding.txtTimer.text = formatTime(time)
        binding.txtPhase.text = phase
    }

    private fun updateButtonStates() {
        binding.btnStart.isEnabled = !isTimerRunning
        binding.btnStop.isEnabled = isTimerRunning
        binding.spinnerSession.isEnabled = !isTimerRunning
    }

    private fun formatTime(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}
