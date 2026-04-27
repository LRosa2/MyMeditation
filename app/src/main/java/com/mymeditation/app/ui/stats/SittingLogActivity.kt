package com.mymeditation.app.ui.stats

import android.app.AlertDialog
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
import com.mymeditation.app.R
import com.mymeditation.app.data.AppDatabase
import com.mymeditation.app.data.entities.LogEntryEntity
import com.mymeditation.app.databinding.ActivitySittingLogBinding
import com.mymeditation.app.util.SettingsManager
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
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

        binding.btnExportCsv.setOnClickListener { exportLauncher.launch("meditation_log.csv") }
        binding.btnImportCsv.setOnClickListener {
            importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
        }

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

    private fun loadLog() {
        lifecycleScope.launch {
            val entries = db.logDao().getAllLogsList()
            adapter.setItems(entries)
            binding.txtEmptyLog.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmDelete(entry: LogEntryEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                lifecycleScope.launch {
                    db.logDao().deleteLog(entry)
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
                val reader = BufferedReader(InputStreamReader(contentResolver.openInputStream(uri)))
                val lines = reader.readLines()
                reader.close()

                // Skip header
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                var imported = 0
                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue
                    val parts = line.split(",", limit = 4)
                    if (parts.size < 4) continue
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
            }
        }
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
            holder.session.text = entry.sessionName
            val dateFormat = settings.getDateTimeFormat()
            holder.date.text = dateFormat.format(Date(entry.startTime))
            holder.duration.text = formatDuration(entry.durationSeconds)
            holder.btnDelete.setOnClickListener { onDeleteClick(entry) }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val session: TextView = view.findViewById(R.id.txtLogSession)
            val date: TextView = view.findViewById(R.id.txtLogDate)
            val duration: TextView = view.findViewById(R.id.txtLogDuration)
            val btnDelete: View = view.findViewById(R.id.btnDeleteLog)
        }
    }
}
