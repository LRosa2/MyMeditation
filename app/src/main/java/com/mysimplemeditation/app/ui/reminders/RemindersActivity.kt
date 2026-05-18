package com.mysimplemeditation.app.ui.reminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mysimplemeditation.app.R
import com.mysimplemeditation.app.data.AppDatabase
import com.mysimplemeditation.app.data.entities.ReminderEntity
import com.mysimplemeditation.app.databinding.ActivityRemindersBinding
import com.mysimplemeditation.app.receiver.ReminderReceiver
import com.mysimplemeditation.app.util.SettingsManager
import kotlinx.coroutines.launch

class RemindersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemindersBinding
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsManager
    private lateinit var adapter: ReminderAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            android.widget.Toast.makeText(this, "Notifications needed for reminders", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemindersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.menu_reminders)

        db = AppDatabase.getInstance(this)
        settings = SettingsManager(this)
        adapter = ReminderAdapter(
            onEnabledChanged = { reminder, enabled -> updateReminder(reminder, enabled) },
            onEditClick = { reminder -> editReminder(reminder) },
            onDeleteClick = { reminder -> confirmDelete(reminder) }
        )

        binding.recyclerReminders.layoutManager = LinearLayoutManager(this)
        binding.recyclerReminders.adapter = adapter

        // Exact alarm permission checkbox
        binding.chkExactAlarmPermission.isChecked = ReminderReceiver.hasExactAlarmPermission(this)
        binding.chkExactAlarmPermission.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !ReminderReceiver.hasExactAlarmPermission(this)) {
                requestExactAlarmPermission()
            }
        }

        binding.btnAddReminder.setOnClickListener {
            requestNotificationPermission()
            startActivity(Intent(this, ReminderEditActivity::class.java))
        }

        loadReminders()
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlertDialog.Builder(this)
                .setTitle("Exact Alarm Permission")
                .setMessage("This permission allows reminders to fire at the exact time you set. Without it, reminders may be delayed by several minutes when your phone is idle.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(
                        android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    binding.chkExactAlarmPermission.isChecked = false
                }
                .show()
        } else {
            binding.chkExactAlarmPermission.isChecked = true
            android.widget.Toast.makeText(this, "Exact alarms are automatically allowed on this Android version", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
        binding.chkExactAlarmPermission.isChecked = ReminderReceiver.hasExactAlarmPermission(this)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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

    private fun loadReminders() {
        lifecycleScope.launch {
            val reminders = db.reminderDao().getAllRemindersList()
            adapter.setItems(reminders)
            binding.txtEmptyReminders.visibility = if (reminders.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateReminder(reminder: ReminderEntity, enabled: Boolean) {
        lifecycleScope.launch {
            val updatedReminder = reminder.copy(enabled = enabled)
            db.reminderDao().updateReminder(updatedReminder)
            if (enabled) {
                // Check alarm permission before scheduling
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    if (!alarmManager.canScheduleExactAlarms()) {
                        requestAlarmPermission()
                    }
                }
                ReminderReceiver.schedule(this@RemindersActivity, updatedReminder)
            } else {
                ReminderReceiver.cancel(this@RemindersActivity, updatedReminder)
            }
            loadReminders()
        }
    }

    private fun requestAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Alarm Permission")
                    .setMessage("For reliable reminders, please grant exact alarm permission in the next screen.")
                    .setPositiveButton("OK") { _, _ ->
                        try {
                            val intent = Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:$packageName")
                            )
                            // Check if the intent can be resolved
                            if (intent.resolveActivity(packageManager) != null) {
                                startActivity(intent)
                            } else {
                                // Fallback to app alarm settings
                                openAppAlarmSettings()
                            }
                        } catch (e: Exception) {
                            openAppAlarmSettings()
                        }
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }

    private fun openAppAlarmSettings() {
        try {
            // Try the alarms settings for this app (API 31+)
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun editReminder(reminder: ReminderEntity) {
        val intent = Intent(this, ReminderEditActivity::class.java).apply {
            putExtra("reminder_id", reminder.id)
        }
        startActivity(intent)
    }

    private fun confirmDelete(reminder: ReminderEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                lifecycleScope.launch {
                    db.reminderDao().deleteReminder(reminder)
                    ReminderReceiver.cancel(this@RemindersActivity, reminder)
                    loadReminders()
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    inner class ReminderAdapter(
        private val onEnabledChanged: (ReminderEntity, Boolean) -> Unit,
        private val onEditClick: (ReminderEntity) -> Unit,
        private val onDeleteClick: (ReminderEntity) -> Unit
    ) : RecyclerView.Adapter<ReminderAdapter.ViewHolder>() {

        private var items = listOf<ReminderEntity>()

        fun setItems(newItems: List<ReminderEntity>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reminder, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val reminder = items[position]
            holder.time.text = settings.formatTimeOfDay(reminder.timeHour, reminder.timeMinute)
            holder.message.text = reminder.message
            holder.threshold.text = "Threshold: ${reminder.thresholdMinutes} min"

            holder.chkEnabled.setOnCheckedChangeListener(null)
            holder.chkEnabled.isChecked = reminder.enabled
            holder.chkEnabled.setOnCheckedChangeListener { _, isChecked ->
                onEnabledChanged(reminder, isChecked)
            }

            holder.btnDelete.setOnClickListener { onDeleteClick(reminder) }
            holder.layoutInfo.setOnClickListener { onEditClick(reminder) }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val time: TextView = view.findViewById(R.id.txtReminderTime)
            val message: TextView = view.findViewById(R.id.txtReminderMessage)
            val threshold: TextView = view.findViewById(R.id.txtReminderThreshold)
            val chkEnabled: CheckBox = view.findViewById(R.id.chkReminderEnabled)
            val btnDelete: View = view.findViewById(R.id.btnDeleteReminder)
            val layoutInfo: View = view.findViewById(R.id.layoutReminderInfo)
        }
    }
}
