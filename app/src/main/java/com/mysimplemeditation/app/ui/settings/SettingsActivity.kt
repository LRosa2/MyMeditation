package com.mysimplemeditation.app.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.mysimplemeditation.app.data.AppDatabase
import com.mysimplemeditation.app.databinding.ActivitySettingsBinding
import com.mysimplemeditation.app.util.DatabaseExporter
import com.mysimplemeditation.app.util.SettingsManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(com.mysimplemeditation.app.R.string.menu_settings)

        settings = SettingsManager(this)

        binding.chkRememberVolume.isChecked = settings.rememberVolume
        binding.chkPlayAsAlarm.isChecked = settings.playAsAlarm

        // Time format
        if (settings.is24Hour()) {
            binding.radio24h.isChecked = true
        } else {
            binding.radio12h.isChecked = true
        }
        binding.radioTimeFormat.setOnCheckedChangeListener { _, checkedId ->
            settings.timeFormat = if (checkedId == binding.radio24h.id) "24h" else "12h"
        }

        // Theme mode
        when (settings.themeMode) {
            "light" -> binding.radioThemeLight.isChecked = true
            "dark" -> binding.radioThemeDark.isChecked = true
            else -> binding.radioThemeSystem.isChecked = true
        }
        binding.radioThemeMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.radioThemeLight.id -> "light"
                binding.radioThemeDark.id -> "dark"
                else -> "system"
            }
            settings.themeMode = mode
            applyTheme(mode)
        }

        binding.chkRememberVolume.setOnCheckedChangeListener { _, isChecked ->
            settings.rememberVolume = isChecked
        }

        binding.chkPlayAsAlarm.setOnCheckedChangeListener { _, isChecked ->
            settings.playAsAlarm = isChecked
        }

        // Vibration settings
        binding.editVibExecutions.setText(settings.vibrationExecutions.toString())
        binding.editVibDuration.setText(settings.vibrationDurationMs.toString())
        binding.editVibGap.setText(settings.vibrationGapMs.toString())

        val vibWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                settings.vibrationExecutions = binding.editVibExecutions.text.toString().toIntOrNull() ?: 2
                settings.vibrationDurationMs = binding.editVibDuration.text.toString().toIntOrNull() ?: 500
                settings.vibrationGapMs = binding.editVibGap.text.toString().toIntOrNull() ?: 1000
            }
        }
        binding.editVibExecutions.addTextChangedListener(vibWatcher)
        binding.editVibDuration.addTextChangedListener(vibWatcher)
        binding.editVibGap.addTextChangedListener(vibWatcher)

        binding.btnTestVibration.setOnClickListener {
            com.mysimplemeditation.app.util.AudioHelper.playVibration(
                context = this,
                durationMs = settings.vibrationDurationMs,
                executions = settings.vibrationExecutions,
                gapMs = settings.vibrationGapMs
            )
        }

        binding.btnExportDb.setOnClickListener { exportDatabase() }
        binding.btnImportDb.setOnClickListener { importDatabase() }
    }

    private fun applyTheme(mode: String) {
        when (mode) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun exportDatabase() {
        lifecycleScope.launch {
            val result = DatabaseExporter.exportDatabase(this@SettingsActivity)
            result.onSuccess { path ->
                Toast.makeText(
                    this@SettingsActivity,
                    "${getString(com.mysimplemeditation.app.R.string.export_success)}: $path",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { e ->
                Toast.makeText(
                    this@SettingsActivity,
                    "${getString(com.mysimplemeditation.app.R.string.export_fail)}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun importDatabase() {
        // Simple approach: show a dialog asking for the path
        // In a production app, you'd use SAF (Storage Access Framework)
        val input = android.widget.EditText(this).apply {
            hint = "/storage/emulated/0/Documents/MyMeditation/mymeditation_backup.db"
            setText("/storage/emulated/0/Documents/MyMeditation/mymeditation_backup.db")
        }

        AlertDialog.Builder(this)
            .setTitle(getString(com.mysimplemeditation.app.R.string.import_database))
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isNotEmpty()) {
                    lifecycleScope.launch {
                        val result = DatabaseExporter.importDatabase(this@SettingsActivity, path)
                        result.onSuccess {
                            Toast.makeText(
                                this@SettingsActivity,
                                getString(com.mysimplemeditation.app.R.string.import_success),
                                Toast.LENGTH_LONG
                            ).show()
                        }.onFailure { e ->
                            Toast.makeText(
                                this@SettingsActivity,
                                "${getString(com.mysimplemeditation.app.R.string.import_fail)}: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(com.mysimplemeditation.app.R.string.cancel), null)
            .show()
    }
}
