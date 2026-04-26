package com.mymeditation.app.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mymeditation.app.data.AppDatabase
import com.mymeditation.app.databinding.ActivitySettingsBinding
import com.mymeditation.app.util.DatabaseExporter
import com.mymeditation.app.util.SettingsManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(com.mymeditation.app.R.string.menu_settings)

        settings = SettingsManager(this)

        binding.chkRememberVolume.isChecked = settings.rememberVolume
        binding.chkPlayAsAlarm.isChecked = settings.playAsAlarm

        binding.chkRememberVolume.setOnCheckedChangeListener { _, isChecked ->
            settings.rememberVolume = isChecked
        }

        binding.chkPlayAsAlarm.setOnCheckedChangeListener { _, isChecked ->
            settings.playAsAlarm = isChecked
        }

        binding.btnExportDb.setOnClickListener { exportDatabase() }
        binding.btnImportDb.setOnClickListener { importDatabase() }
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
                    "${getString(com.mymeditation.app.R.string.export_success)}: $path",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { e ->
                Toast.makeText(
                    this@SettingsActivity,
                    "${getString(com.mymeditation.app.R.string.export_fail)}: ${e.message}",
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
            .setTitle(getString(com.mymeditation.app.R.string.import_database))
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isNotEmpty()) {
                    lifecycleScope.launch {
                        val result = DatabaseExporter.importDatabase(this@SettingsActivity, path)
                        result.onSuccess {
                            Toast.makeText(
                                this@SettingsActivity,
                                getString(com.mymeditation.app.R.string.import_success),
                                Toast.LENGTH_LONG
                            ).show()
                        }.onFailure { e ->
                            Toast.makeText(
                                this@SettingsActivity,
                                "${getString(com.mymeditation.app.R.string.import_fail)}: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(com.mymeditation.app.R.string.cancel), null)
            .show()
    }
}
