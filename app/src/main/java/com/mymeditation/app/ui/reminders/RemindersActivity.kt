package com.mymeditation.app.ui.reminders

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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mymeditation.app.R
import com.mymeditation.app.data.AppDatabase
import com.mymeditation.app.data.entities.ReminderEntity
import com.mymeditation.app.databinding.ActivityRemindersBinding
import com.mymeditation.app.receiver.ReminderReceiver
import kotlinx.coroutines.launch

class RemindersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemindersBinding
    private lateinit var db: AppDatabase
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
        adapter = ReminderAdapter(
            onEnabledChanged = { reminder, enabled -> updateReminder(reminder, enabled) },
            onDeleteClick = { reminder -> confirmDelete(reminder) }
        )

        binding.recyclerReminders.layoutManager = LinearLayoutManager(this)
        binding.recyclerReminders.adapter = adapter

        binding.btnAddReminder.setOnClickListener {
            requestNotificationPermission()
            startActivity(Intent(this, ReminderEditActivity::class.java))
        }

        loadReminders()
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
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
            db.reminderDao().updateReminder(reminder.copy(enabled = enabled))
            if (enabled) {
                ReminderReceiver.schedule(this@RemindersActivity, reminder)
            } else {
                ReminderReceiver.cancel(this@RemindersActivity, reminder)
            }
            loadReminders()
        }
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
            holder.time.text = String.format("%02d:%02d", reminder.timeHour, reminder.timeMinute)
            holder.message.text = reminder.message
            holder.threshold.text = "Threshold: ${reminder.thresholdMinutes} min"

            holder.chkEnabled.setOnCheckedChangeListener(null)
            holder.chkEnabled.isChecked = reminder.enabled
            holder.chkEnabled.setOnCheckedChangeListener { _, isChecked ->
                onEnabledChanged(reminder, isChecked)
            }

            holder.btnDelete.setOnClickListener { onDeleteClick(reminder) }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val time: TextView = view.findViewById(R.id.txtReminderTime)
            val message: TextView = view.findViewById(R.id.txtReminderMessage)
            val threshold: TextView = view.findViewById(R.id.txtReminderThreshold)
            val chkEnabled: CheckBox = view.findViewById(R.id.chkReminderEnabled)
            val btnDelete: View = view.findViewById(R.id.btnDeleteReminder)
        }
    }
}
