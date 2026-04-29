package com.mysimplemeditation.app.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mysimplemeditation.app.R
import com.mysimplemeditation.app.data.AppDatabase
import com.mysimplemeditation.app.data.entities.SessionEntity
import com.mysimplemeditation.app.databinding.ActivityMainBinding
import com.mysimplemeditation.app.service.TimerService
import com.mysimplemeditation.app.ui.reminders.RemindersActivity
import com.mysimplemeditation.app.ui.sessions.SessionsActivity
import com.mysimplemeditation.app.ui.settings.SettingsActivity
import com.mysimplemeditation.app.ui.stats.StatisticsActivity
import com.mysimplemeditation.app.util.AudioHelper
import com.mysimplemeditation.app.util.SettingsManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsManager
    private var sessions = listOf<SessionEntity>()
    private var selectedSession: SessionEntity? = null
    private var isTimerRunning = false
    private var isTimerPaused = false

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
                    isTimerPaused = false
                    updateButtonStates()
                }
                TimerService.ACTION_STOPPED -> {
                    isTimerRunning = false
                    isTimerPaused = false
                    updateButtonStates()
                    binding.txtTimer.text = "00:00:00"
                    binding.txtPhase.text = ""
                }
                TimerService.ACTION_ENDED -> {
                    isTimerRunning = false
                    isTimerPaused = false
                    updateButtonStates()
                    binding.txtTimer.text = "00:00:00"
                    binding.txtPhase.text = getString(R.string.session_ended)
                }
                TimerService.ACTION_PAUSED -> {
                    isTimerPaused = true
                    updateButtonStates()
                }
                TimerService.ACTION_RESUMED -> {
                    isTimerPaused = false
                    updateButtonStates()
                }
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before creating activity
        settings = SettingsManager(this)
        when (settings.themeMode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)

        requestNotificationPermission()

        setupVolumeSlider()
        setupTriggerModeSpinner()
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
            addAction(TimerService.ACTION_PAUSED)
            addAction(TimerService.ACTION_RESUMED)
        }
        registerReceiver(timerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Sync with service state in case broadcasts were missed while screen was off
        if (TimerService.isRunning) {
            isTimerRunning = true
            isTimerPaused = TimerService.isPaused
            updateButtonStates()
            updateTimerDisplay(TimerService.lastRemaining, TimerService.lastElapsed, TimerService.lastPhase)
        } else if (isTimerRunning) {
            // Timer ended while we were away
            isTimerRunning = false
            isTimerPaused = false
            updateButtonStates()
            binding.txtTimer.text = "00:00:00"
            binding.txtPhase.text = getString(R.string.session_ended)
        }

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
            R.id.menu_about -> {
                showAboutDialog()
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

    private fun setupTriggerModeSpinner() {
        val modes = listOf("Default (Session)", "Only Vibration", "Only Sound", "Sound + Vibration")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_dark, modes)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        binding.spinnerTriggerMode.adapter = adapter

        val currentMode = settings.globalTriggerMode
        val selection = when (currentMode) {
            "vibration_only" -> 1
            "sound_only" -> 2
            "sound_vibration" -> 3
            else -> 0
        }
        binding.spinnerTriggerMode.setSelection(selection)

        binding.spinnerTriggerMode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                settings.globalTriggerMode = when (pos) {
                    1 -> "vibration_only"
                    2 -> "sound_only"
                    3 -> "sound_vibration"
                    else -> "default"
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnStart.setOnClickListener {
            when {
                isTimerPaused -> {
                    val intent = Intent(this, TimerService::class.java).apply {
                        action = TimerService.ACTION_RESUME
                    }
                    startService(intent)
                }
                isTimerRunning -> {
                    val intent = Intent(this, TimerService::class.java).apply {
                        action = TimerService.ACTION_PAUSE
                    }
                    startService(intent)
                }
                else -> {
                    val session = selectedSession ?: return@setOnClickListener

                    val volume = binding.seekBarVolume.progress
                    val useAlarm = settings.playAsAlarm
                    val globalMode = settings.globalTriggerMode

                    // Save volume before starting
                    if (settings.rememberVolume) {
                        AudioHelper.saveCurrentVolume(this, settings)
                    }

                    val intent = Intent(this, TimerService::class.java).apply {
                        action = TimerService.ACTION_START
                        putExtra(TimerService.EXTRA_SESSION_ID, session.id)
                        putExtra(TimerService.EXTRA_VOLUME, volume)
                        putExtra(TimerService.EXTRA_USE_ALARM, useAlarm)
                        putExtra(TimerService.EXTRA_GLOBAL_MODE, globalMode)
                    }
                    startForegroundService(intent)
                }
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
        val adapter = ArrayAdapter(this, R.layout.spinner_item_dark, names)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        binding.spinnerSession.adapter = adapter

        // Preserve current selection if it still exists, otherwise fall back to default
        val preservedSession = selectedSession?.takeIf { s -> sessions.any { it.id == s.id } }
        val defaultSession = sessions.find { it.isDefault }
        val target = preservedSession ?: defaultSession ?: sessions.first()
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
        when {
            isTimerPaused -> {
                binding.btnStart.text = getString(R.string.resume)
                binding.btnStart.isEnabled = true
                binding.btnStop.isEnabled = true
                binding.spinnerSession.isEnabled = false
            }
            isTimerRunning -> {
                binding.btnStart.text = getString(R.string.pause)
                binding.btnStart.isEnabled = true
                binding.btnStop.isEnabled = true
                binding.spinnerSession.isEnabled = false
            }
            else -> {
                binding.btnStart.text = getString(R.string.start)
                binding.btnStart.isEnabled = true
                binding.btnStop.isEnabled = false
                binding.spinnerSession.isEnabled = true
            }
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun showAboutDialog() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "?"
        }
        val author = getString(R.string.app_author)
        val email = getString(R.string.support_email)
        val message = "Version: $versionName\nAuthor: $author\n\nSupport: $email"

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton("Email") { _, _ ->
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$email")
                }
                startActivity(Intent.createChooser(intent, "Send email"))
            }
            .show()

        // Make dialog text selectable so email can be copied
        val textView = dialog.findViewById<TextView>(android.R.id.message)
        textView?.apply {
            text = SpannableString(text.toString()).apply {
                val emailStart = toString().indexOf(email)
                if (emailStart >= 0) {
                    setSpan(
                        URLSpan("mailto:$email"),
                        emailStart,
                        emailStart + email.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
