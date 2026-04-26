package com.mymeditation.app.ui.sessions

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mymeditation.app.R
import com.mymeditation.app.data.AppDatabase
import com.mymeditation.app.data.entities.SessionEntity
import com.mymeditation.app.databinding.ActivitySessionsBinding
import kotlinx.coroutines.launch

class SessionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionsBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.menu_sessions)

        db = AppDatabase.getInstance(this)
        adapter = SessionAdapter(
            onDefaultClick = { session -> setDefault(session) },
            onDeleteClick = { session -> confirmDelete(session) },
            onEditClick = { session -> editSession(session) }
        )

        binding.recyclerSessions.layoutManager = LinearLayoutManager(this)
        binding.recyclerSessions.adapter = adapter

        binding.btnAddSession.setOnClickListener {
            startActivity(Intent(this, SessionEditActivity::class.java))
        }

        loadSessions()
    }

    override fun onResume() {
        super.onResume()
        loadSessions()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadSessions() {
        lifecycleScope.launch {
            val sessions = db.sessionDao().getAllSessionsList()
            adapter.setItems(sessions)
            binding.txtEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setDefault(session: SessionEntity) {
        lifecycleScope.launch {
            db.sessionDao().clearDefaults()
            db.sessionDao().setDefault(session.id)
            loadSessions()
        }
    }

    private fun confirmDelete(session: SessionEntity) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_session))
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                lifecycleScope.launch {
                    db.sessionDao().deleteSession(session)
                    loadSessions()
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun editSession(session: SessionEntity) {
        val intent = Intent(this, SessionEditActivity::class.java)
        intent.putExtra(SessionEditActivity.EXTRA_SESSION_ID, session.id)
        startActivity(intent)
    }

    inner class SessionAdapter(
        private val onDefaultClick: (SessionEntity) -> Unit,
        private val onDeleteClick: (SessionEntity) -> Unit,
        private val onEditClick: (SessionEntity) -> Unit
    ) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

        private var items = listOf<SessionEntity>()

        fun setItems(newItems: List<SessionEntity>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = items[position]
            holder.name.text = session.name
            holder.type.text = if (session.type == "CLOSED") "Closed" else "Open"
            holder.defaultLabel.visibility = if (session.isDefault) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener { onEditClick(session) }
            holder.btnDefault.setOnClickListener { onDefaultClick(session) }
            holder.btnDelete.setOnClickListener { onDeleteClick(session) }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.txtSessionName)
            val type: TextView = view.findViewById(R.id.txtSessionType)
            val defaultLabel: TextView = view.findViewById(R.id.txtSessionDefault)
            val btnDefault: View = view.findViewById(R.id.btnSetDefault)
            val btnDelete: View = view.findViewById(R.id.btnDeleteSession)
        }
    }
}
