package com.mymeditation.app.ui.sessions

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.mymeditation.app.ui.widgets.DurationPickerView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mymeditation.app.R
import com.mymeditation.app.data.AppDatabase
import com.mymeditation.app.data.entities.SessionEntity
import com.mymeditation.app.data.entities.TriggerEntity
import com.mymeditation.app.databinding.ActivitySessionEditBinding
import com.mymeditation.app.util.AudioHelper
import com.mymeditation.app.util.SettingsManager
import kotlinx.coroutines.launch

class SessionEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }

    private lateinit var binding: ActivitySessionEditBinding
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsManager
    private var sessionId: Long = 0L // 0 means new
    private var triggers = mutableListOf<TriggerEntity>()
    private lateinit var triggerAdapter: TriggerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = AppDatabase.getInstance(this)
        settings = SettingsManager(this)

        sessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0L)

        // Setup type spinner
        val typeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("Closed", "Open")
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerType.adapter = typeAdapter

        binding.spinnerType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val isClosed = pos == 0
                binding.pickerSitting.visibility = if (isClosed) View.VISIBLE else View.GONE
                binding.labelSittingTime.visibility = if (isClosed) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Setup triggers
        triggerAdapter = TriggerAdapter(
            onEditClick = { trigger -> showTriggerDialog(trigger) },
            onDeleteClick = { trigger -> deleteTrigger(trigger) }
        )
        binding.recyclerTriggers.layoutManager = LinearLayoutManager(this)
        binding.recyclerTriggers.adapter = triggerAdapter

        binding.btnAddTrigger.setOnClickListener { showTriggerDialog(null) }

        binding.btnSaveSession.setOnClickListener { saveSession() }
        binding.btnCancelSession.setOnClickListener { finish() }

        if (sessionId > 0) {
            loadSession()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadSession() {
        lifecycleScope.launch {
            val session = db.sessionDao().getSessionById(sessionId) ?: return@launch
            binding.editSessionName.setText(session.name)
            binding.spinnerType.setSelection(if (session.type == "CLOSED") 0 else 1)
            binding.pickerPreparation.setDurationSeconds(session.preparationMinutes * 60 + session.preparationSeconds)
            binding.pickerSitting.setDurationSeconds(session.sittingMinutes * 60 + session.sittingSeconds)
            binding.chkDefault.isChecked = session.isDefault

            triggers.clear()
            triggers.addAll(db.sessionDao().getTriggersForSessionSync(sessionId))
            triggerAdapter.setItems(triggers)
        }
    }

    private fun saveSession() {
        val name = binding.editSessionName.text.toString().trim()
        if (name.isEmpty()) {
            binding.editSessionName.error = "Name is required"
            return
        }

        val type = if (binding.spinnerType.selectedItemPosition == 0) "CLOSED" else "OPEN"
        val prepTotal = binding.pickerPreparation.getDurationSeconds()
        val prepMin = prepTotal / 60
        val prepSec = prepTotal % 60
        val sitTotal = binding.pickerSitting.getDurationSeconds()
        val sitMin = sitTotal / 60
        val sitSec = sitTotal % 60
        val isDefault = binding.chkDefault.isChecked

        lifecycleScope.launch {
            if (isDefault) {
                db.sessionDao().clearDefaults()
            }

            if (sessionId > 0L) {
                val existing = db.sessionDao().getSessionById(sessionId) ?: return@launch
                db.sessionDao().updateSession(
                    existing.copy(
                        name = name,
                        type = type,
                        preparationMinutes = prepMin,
                        preparationSeconds = prepSec,
                        sittingMinutes = sitMin,
                        sittingSeconds = sitSec,
                        isDefault = isDefault
                    )
                )
            } else {
                sessionId = db.sessionDao().insertSession(
                    SessionEntity(
                        name = name,
                        type = type,
                        preparationMinutes = prepMin,
                        preparationSeconds = prepSec,
                        sittingMinutes = sitMin,
                        sittingSeconds = sitSec,
                        isDefault = isDefault
                    )
                )
            }

            // Save triggers
            db.sessionDao().deleteTriggersForSession(sessionId)
            for (trigger in triggers) {
                db.sessionDao().insertTrigger(trigger.copy(sessionId = sessionId, id = 0))
            }

            finish()
        }
    }

    private fun showTriggerDialog(existing: TriggerEntity?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_trigger_edit, null)

        val pickerStartTime = dialogView.findViewById<DurationPickerView>(R.id.pickerStartTime)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinnerTriggerType)
        val editMp3Path = dialogView.findViewById<EditText>(R.id.editMp3Path)
        val layoutMp3Path = dialogView.findViewById<View>(R.id.layoutMp3Path)
        val seekBarVolume = dialogView.findViewById<SeekBar>(R.id.seekBarTriggerVolume)
        val chkRepeating = dialogView.findViewById<CheckBox>(R.id.chkRepeating)
        val layoutRepeatInterval = dialogView.findViewById<View>(R.id.layoutRepeatInterval)
        val editRepeatInterval = dialogView.findViewById<EditText>(R.id.editRepeatInterval)
        val editExecutions = dialogView.findViewById<EditText>(R.id.editExecutions)
        val editGap = dialogView.findViewById<EditText>(R.id.editGap)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveTrigger)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelTrigger)
        val btnTest = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTestTrigger)

        // Setup trigger type spinner
        val triggerTypeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("Ring Bell", "Play MP3 Track")
        )
        triggerTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = triggerTypeAdapter

        spinnerType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                layoutMp3Path.visibility = if (pos == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        chkRepeating.setOnCheckedChangeListener { _, isChecked ->
            layoutRepeatInterval.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Fill existing values
        if (existing != null) {
            pickerStartTime.setDurationSeconds(existing.startTimeSeconds)
            spinnerType.setSelection(if (existing.type == "BELL") 0 else 1)
            editMp3Path.setText(existing.mp3Path ?: "")
            seekBarVolume.progress = existing.volume
            chkRepeating.isChecked = existing.repeating
            editRepeatInterval.setText(existing.repeatIntervalMinutes.toString())
            editExecutions.setText(existing.executions.toString())
            editGap.setText(existing.gapMs.toString())
            layoutMp3Path.visibility = if (existing.type == "MP3") View.VISIBLE else View.GONE
            layoutRepeatInterval.visibility = if (existing.repeating) View.VISIBLE else View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        btnSave.setOnClickListener {
            val startTime = pickerStartTime.getDurationSeconds()
            val triggerType = if (spinnerType.selectedItemPosition == 0) "BELL" else "MP3"
            val mp3Path = if (triggerType == "MP3") editMp3Path.text.toString() else null
            val vol = seekBarVolume.progress
            val repeating = chkRepeating.isChecked
            val repeatInterval = editRepeatInterval.text.toString().toIntOrNull() ?: 5
            val executions = editExecutions.text.toString().toIntOrNull() ?: 1
            val gapMs = editGap.text.toString().toIntOrNull() ?: 500

            val trigger = TriggerEntity(
                id = existing?.id ?: 0,
                sessionId = sessionId,
                startTimeSeconds = startTime,
                type = triggerType,
                mp3Path = mp3Path,
                volume = vol,
                repeating = repeating,
                repeatIntervalMinutes = repeatInterval,
                executions = executions,
                gapMs = gapMs
            )

            if (existing != null) {
                val idx = triggers.indexOfFirst { it.id == existing.id }
                if (idx >= 0) triggers[idx] = trigger else triggers.add(trigger)
            } else {
                triggers.add(trigger)
            }

            triggerAdapter.setItems(triggers)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnTest.setOnClickListener {
            val triggerType = if (spinnerType.selectedItemPosition == 0) "BELL" else "MP3"
            val vol = seekBarVolume.progress
            val executions = editExecutions.text.toString().toIntOrNull() ?: 1
            val gapMs = editGap.text.toString().toIntOrNull() ?: 500
            val mp3Path = editMp3Path.text.toString()

            if (triggerType == "BELL") {
                AudioHelper.playBell(
                    volume = vol,
                    useAlarmStream = settings.playAsAlarm,
                    executions = executions,
                    gapMs = gapMs
                )
            } else {
                AudioHelper.playMp3(
                    context = this,
                    path = mp3Path,
                    volume = vol,
                    useAlarmStream = settings.playAsAlarm,
                    executions = executions,
                    gapMs = gapMs
                )
            }
        }

        dialog.show()
    }

    private fun deleteTrigger(trigger: TriggerEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                triggers.remove(trigger)
                triggerAdapter.setItems(triggers)
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    inner class TriggerAdapter(
        private val onEditClick: (TriggerEntity) -> Unit,
        private val onDeleteClick: (TriggerEntity) -> Unit
    ) : RecyclerView.Adapter<TriggerAdapter.ViewHolder>() {

        private var items = listOf<TriggerEntity>()

        fun setItems(newItems: List<TriggerEntity>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_trigger, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val trigger = items[position]
            val m = trigger.startTimeSeconds / 60
            val s = trigger.startTimeSeconds % 60
            holder.time.text = String.format("At %02d:%02d", m, s)
            holder.type.text = if (trigger.type == "BELL") "Bell" else "MP3: ${trigger.mp3Path}"
            val details = buildString {
                append("Vol: ${trigger.volume}%")
                append(" | ${trigger.executions}x")
                if (trigger.gapMs != 500) append(" gap:${trigger.gapMs}ms")
                if (trigger.repeating) append(" | Every ${trigger.repeatIntervalMinutes}min")
            }
            holder.details.text = details

            holder.btnEdit.setOnClickListener { onEditClick(trigger) }
            holder.btnDelete.setOnClickListener { onDeleteClick(trigger) }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val time: TextView = view.findViewById(R.id.txtTriggerTime)
            val type: TextView = view.findViewById(R.id.txtTriggerType)
            val details: TextView = view.findViewById(R.id.txtTriggerDetails)
            val btnEdit: View = view.findViewById(R.id.btnEditTrigger)
            val btnDelete: View = view.findViewById(R.id.btnDeleteTrigger)
        }
    }
}
