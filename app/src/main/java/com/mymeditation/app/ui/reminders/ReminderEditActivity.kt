package com.mymeditation.app.ui.reminders

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mymeditation.app.data.AppDatabase
import com.mymeditation.app.data.entities.ReminderEntity
import com.mymeditation.app.databinding.ActivityReminderEditBinding
import com.mymeditation.app.receiver.ReminderReceiver
import kotlinx.coroutines.launch

class ReminderEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderEditBinding
    private lateinit var db: AppDatabase
    private var reminderId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = AppDatabase.getInstance(this)
        reminderId = intent.getLongExtra("reminder_id", 0L)

        binding.btnSaveReminder.setOnClickListener { saveReminder() }
        binding.btnCancelReminder.setOnClickListener { finish() }

        if (reminderId > 0) loadReminder()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadReminder() {
        lifecycleScope.launch {
            val reminder = db.reminderDao().getReminderById(reminderId) ?: return@launch
            binding.editReminderMessage.setText(reminder.message)
            binding.timePickerReminder.hour = reminder.timeHour
            binding.timePickerReminder.minute = reminder.timeMinute
            binding.editThreshold.setText(reminder.thresholdMinutes.toString())
            binding.chkReminderEnabled.isChecked = reminder.enabled
        }
    }

    private fun saveReminder() {
        val message = binding.editReminderMessage.text.toString().trim()
        if (message.isEmpty()) {
            binding.editReminderMessage.error = "Message is required"
            return
        }

        val hour = binding.timePickerReminder.hour
        val minute = binding.timePickerReminder.minute
        val threshold = binding.editThreshold.text.toString().toIntOrNull() ?: 30
        val enabled = binding.chkReminderEnabled.isChecked

        lifecycleScope.launch {
            val reminder = ReminderEntity(
                id = if (reminderId > 0) reminderId else 0,
                message = message,
                timeHour = hour,
                timeMinute = minute,
                thresholdMinutes = threshold,
                enabled = enabled
            )

            if (reminderId > 0) {
                db.reminderDao().updateReminder(reminder)
            } else {
                reminderId = db.reminderDao().insertReminder(reminder)
            }

            if (enabled) {
                ReminderReceiver.schedule(this@ReminderEditActivity, reminder.copy(id = reminderId))
            } else {
                ReminderReceiver.cancel(this@ReminderEditActivity, reminder.copy(id = reminderId))
            }

            finish()
        }
    }
}
