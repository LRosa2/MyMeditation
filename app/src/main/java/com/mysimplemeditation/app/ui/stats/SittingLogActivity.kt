package com.mysimplemeditation.app.ui.stats

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.mysimplemeditation.app.R
import com.mysimplemeditation.app.data.AppDatabase
import com.mysimplemeditation.app.data.entities.LogEntryEntity
import com.mysimplemeditation.app.databinding.ActivitySittingLogBinding
import com.mysimplemeditation.app.util.SettingsManager
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SittingLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySittingLogBinding
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsManager
    private lateinit var adapter: LogAdapter

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { exportCsv(it) } }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importCsv(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySittingLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.sitting_log)

        db = AppDatabase.getInstance(this)
        settings = SettingsManager(this)

        adapter = LogAdapter(
            onDeleteClick = { entry -> confirmDelete(entry) }
        )
        binding.recyclerLog.layoutManager = LinearLayoutManager(this)
        binding.recyclerLog.adapter = adapter

        binding.btnAddEntry.setOnClickListener { showAddEntryDialog() }

        loadLog()
    }

    override fun onResume() {
        super.onResume()
        loadLog()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_sitting_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                showAddEntryDialog()
                true
            }
            R.id.action_export -> {
                exportLauncher.launch("meditation_log.csv")
                true
            }
            R.id.action_import -> {
                importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                true
            }
            R.id.action_clear -> {
                confirmClearAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadLog() {
        lifecycleScope.launch {
            val entries = db.logDao().getAllLogsList()
            adapter.setItems(entries)
            binding.txtEmptyLog.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmDelete(entry: LogEntryEntity) {
        val dateFormat = settings.getDateTimeFormat()
        val dateStr = dateFormat.format(Date(entry.startTime))
        val entryInfo = "$dateStr  ${formatDuration(entry.durationSeconds)}"
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete))
            .setMessage(entryInfo)
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                lifecycleScope.launch {
                    db.logDao().deleteLog(entry)
                    loadLog()
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("Clear All")
            .setMessage("Are you sure you want to delete ALL log entries?")
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                lifecycleScope.launch {
                    db.logDao().deleteAllLogs()
                    loadLog()
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun exportCsv(uri: Uri) {
        lifecycleScope.launch {
            try {
                val entries = db.logDao().getAllLogsList()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val sb = StringBuilder()
                sb.appendLine("id,session_name,start_time,duration_seconds,duration_formatted")
                for (entry in entries) {
                    val dateStr = dateFormat.format(Date(entry.startTime))
                    val durFormatted = formatDuration(entry.durationSeconds)
                    sb.appendLine("${entry.id},\"${entry.sessionName}\",$dateStr,${entry.durationSeconds},\"$durFormatted\"")
                }
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(sb.toString().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this@SittingLogActivity, getString(R.string.export_csv_success), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SittingLogActivity, getString(R.string.export_csv_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importCsv(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.progressImport.visibility = View.VISIBLE
                val reader = BufferedReader(InputStreamReader(contentResolver.openInputStream(uri)))
                val lines = reader.readLines()
                reader.close()

                // Skip header
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                var imported = 0
                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue
                    val parts = line.split(",", limit = 5)
                    if (parts.size < 5) continue
                    val sessionName = parts[1].removeSurrounding("\"")
                    val dateStr = parts[2]
                    val durationSeconds = parts[3].toIntOrNull() ?: continue

                    val startTime = try {
                        dateFormat.parse(dateStr)?.time ?: 0L
                    } catch (_: Exception) { 0L }

                    if (startTime > 0 && durationSeconds > 0) {
                        db.logDao().insertLog(
                            LogEntryEntity(
                                sessionId = null,
                                sessionName = sessionName,
                                startTime = startTime,
                                durationSeconds = durationSeconds
                            )
                        )
                        imported++
                    }
                }
                loadLog()
                Toast.makeText(this@SittingLogActivity,
                    getString(R.string.import_csv_success) + " ($imported entries)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SittingLogActivity, getString(R.string.import_csv_fail), Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressImport.visibility = View.GONE
            }
        }
    }

    private fun showAddEntryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_log_entry, null)
        val btnDateTime = dialogView.findViewById<MaterialButton>(R.id.btnPickDateTime)
        val editHours = dialogView.findViewById<TextInputEditText>(R.id.editDurationHours)
        val editMinutes = dialogView.findViewById<TextInputEditText>(R.id.editDurationMinutes)

        val calendar = Calendar.getInstance()
        val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        btnDateTime.text = dateTimeFormat.format(calendar.time)

        btnDateTime.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                calendar.set(year, month, day)
                TimePickerDialog(this, { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    btnDateTime.text = dateTimeFormat.format(calendar.time)
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        AlertDialog.Builder(this)
            .setTitle("Add Sitting")
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val hours = editHours.text.toString().toIntOrNull() ?: 0
                val minutes = editMinutes.text.toString().toIntOrNull() ?: 0
                val durationSeconds = hours * 3600 + minutes * 60
                if (durationSeconds > 0) {
                    lifecycleScope.launch {
                        db.logDao().insertLog(
                            LogEntryEntity(
                                sessionId = null,
                                sessionName = "Manual",
                                startTime = calendar.timeInMillis,
                                durationSeconds = durationSeconds
                            )
                        )
                        loadLog()
                        Toast.makeText(this@SittingLogActivity, "Entry added", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun formatDuration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    inner class LogAdapter(
        private val onDeleteClick: (LogEntryEntity) -> Unit
    ) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

        private var items = listOf<LogEntryEntity>()

        fun setItems(newItems: List<LogEntryEntity>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            val dateFormat = settings.getDateTimeFormat()
            val dateStr = dateFormat.format(Date(entry.startTime))
            holder.date.text = "$dateStr  ${formatDuration(entry.durationSeconds)}"
            holder.btnDelete.setOnClickListener { onDeleteClick(entry) }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val date: TextView = view.findViewById(R.id.txtLogDate)
            val btnDelete: View = view.findViewById(R.id.btnDeleteLog)
        }
    }
}
