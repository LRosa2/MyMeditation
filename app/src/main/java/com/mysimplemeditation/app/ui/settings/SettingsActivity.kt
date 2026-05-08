package com.mysimplemeditation.app.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.mysimplemeditation.app.R
import com.mysimplemeditation.app.databinding.ActivitySettingsBinding
import com.mysimplemeditation.app.util.AudioHelper
import com.mysimplemeditation.app.util.BatteryOptimizationHelper
import com.mysimplemeditation.app.util.DatabaseExporter
import com.mysimplemeditation.app.util.SettingsManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsManager
    private var currentMp3EditField: com.google.android.material.textfield.TextInputEditText? = null

    private val mp3PickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val path = try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                it.toString()
            } catch (_: Exception) {
                it.toString()
            }
            currentMp3EditField?.setText(path)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.menu_settings)

        settings = SettingsManager(this)

        binding.chkRememberVolume.isChecked = settings.rememberVolume
        binding.chkPlayAsAlarm.isChecked = settings.playAsAlarm
        binding.chkAutoSilence.isChecked = settings.autoSilencePhone
        binding.chkHighReliabilityMode.isChecked = !BatteryOptimizationHelper.isBatteryOptimized(this)

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

        binding.chkAutoSilence.setOnCheckedChangeListener { _, isChecked ->
            settings.autoSilencePhone = isChecked
        }

        binding.btnConfigureSound.setOnClickListener { showSoundConfigDialog() }
        binding.btnConfigureVibration.setOnClickListener { showVibrationConfigDialog() }

        binding.btnExportDb.setOnClickListener { exportDatabase() }
        binding.btnImportDb.setOnClickListener { importDatabase() }
        binding.chkHighReliabilityMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && BatteryOptimizationHelper.isBatteryOptimized(this)) {
                BatteryOptimizationHelper.requestExemption(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.chkHighReliabilityMode.isChecked = !BatteryOptimizationHelper.isBatteryOptimized(this)
    }

    private fun showSoundConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_general_sound, null)
        val spinnerType = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerGeneralSoundType)
        val layoutMp3Path = dialogView.findViewById<View>(R.id.layoutGeneralMp3Path)
        val editMp3Path = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editGeneralMp3Path)
        val btnBrowse = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGeneralBrowseMp3)
        val seekBarVolume = dialogView.findViewById<SeekBar>(R.id.seekBarGeneralVolume)
        val editExec = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editGeneralSoundExecutions)
        val editGap = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editGeneralSoundGap)
        val btnTest = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTestGeneralSound)

        val typeAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, listOf("Bell", "MP3"))
        typeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerType.adapter = typeAdapter
        spinnerType.setSelection(if (settings.generalSoundType == "BELL") 0 else 1)

        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                layoutMp3Path.visibility = if (pos == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        editMp3Path.setText(settings.generalSoundMp3Path)
        seekBarVolume.progress = settings.generalSoundVolume
        editExec.setText(settings.generalSoundExecutions.toString())
        editGap.setText(settings.generalSoundGapMs.toString())

        btnBrowse.setOnClickListener {
            currentMp3EditField = editMp3Path
            mp3PickerLauncher.launch(arrayOf("audio/*"))
        }

        btnTest.setOnClickListener {
            val type = if (spinnerType.selectedItemPosition == 0) "BELL" else "MP3"
            val vol = seekBarVolume.progress
            val exec = editExec.text.toString().toIntOrNull() ?: 1
            val gap = editGap.text.toString().toIntOrNull() ?: 500
            if (type == "BELL") {
                AudioHelper.playBell(this, vol, settings.playAsAlarm, exec, gap)
            } else {
                val path = editMp3Path.text.toString()
                if (path.isNotBlank()) {
                    AudioHelper.playMp3(this, path, vol, settings.playAsAlarm, exec, gap)
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.configure_sound))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                settings.generalSoundType = if (spinnerType.selectedItemPosition == 0) "BELL" else "MP3"
                settings.generalSoundMp3Path = editMp3Path.text.toString()
                settings.generalSoundVolume = seekBarVolume.progress
                settings.generalSoundExecutions = editExec.text.toString().toIntOrNull() ?: 1
                settings.generalSoundGapMs = editGap.text.toString().toIntOrNull() ?: 500
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showVibrationConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_general_vibration, null)
        val editExec = dialogView.findViewById<EditText>(R.id.editGeneralVibExecutions)
        val editDuration = dialogView.findViewById<EditText>(R.id.editGeneralVibDuration)
        val editGap = dialogView.findViewById<EditText>(R.id.editGeneralVibGap)
        val btnTest = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTestGeneralVibration)

        editExec.setText(settings.vibrationExecutions.toString())
        editDuration.setText(settings.vibrationDurationMs.toString())
        editGap.setText(settings.vibrationGapMs.toString())

        btnTest.setOnClickListener {
            val exec = editExec.text.toString().toIntOrNull() ?: 2
            val dur = editDuration.text.toString().toIntOrNull() ?: 500
            val gap = editGap.text.toString().toIntOrNull() ?: 1000
            AudioHelper.playVibration(this, dur, exec, gap)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.configure_vibration))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                settings.vibrationExecutions = editExec.text.toString().toIntOrNull() ?: 2
                settings.vibrationDurationMs = editDuration.text.toString().toIntOrNull() ?: 500
                settings.vibrationGapMs = editGap.text.toString().toIntOrNull() ?: 1000
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
                    "${getString(R.string.export_success)}: $path",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { e ->
                Toast.makeText(
                    this@SettingsActivity,
                    "${getString(R.string.export_fail)}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun importDatabase() {
        // Simple approach: show a dialog asking for the path
        // In a production app, you'd use SAF (Storage Access Framework)
        val input = EditText(this).apply {
            hint = getString(R.string.backup_db_default_path)
            setText(getString(R.string.backup_db_default_path))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_database))
            .setView(input)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val path = input.text.toString().trim()
                if (path.isNotEmpty()) {
                    lifecycleScope.launch {
                        val result = DatabaseExporter.importDatabase(this@SettingsActivity, path)
                        result.onSuccess {
                            Toast.makeText(
                                this@SettingsActivity,
                                getString(R.string.import_success),
                                Toast.LENGTH_LONG
                            ).show()
                        }.onFailure { e ->
                            Toast.makeText(
                                this@SettingsActivity,
                                "${getString(R.string.import_fail)}: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
