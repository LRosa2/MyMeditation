package com.mymeditation.app.ui.stats

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mymeditation.app.R
import com.mymeditation.app.data.AppDatabase
import com.mymeditation.app.data.dao.DailyTotal
import com.mymeditation.app.databinding.ActivityStatisticsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.menu_statistics)

        db = AppDatabase.getInstance(this)
        loadStats()

        binding.btnShowChains.setOnClickListener { loadChains() }
        binding.btnViewLog.setOnClickListener {
            startActivity(Intent(this, SittingLogActivity::class.java))
        }

        binding.recyclerChains.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadStats() {
        lifecycleScope.launch {
            val now = Calendar.getInstance()

            // Today
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val todaySeconds = db.logDao().getTotalSecondsForDay(
                startOfDay.timeInMillis, now.timeInMillis
            ) ?: 0
            val todayCount = db.logDao().getSessionCountSince(startOfDay.timeInMillis) ?: 0

            binding.txtTodayTime.text = formatDuration(todaySeconds)
            binding.txtTodayCount.text = "$todayCount sessions"

            // This week (start of week = Monday)
            val startOfWeek = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (startOfWeek.timeInMillis > System.currentTimeMillis()) {
                startOfWeek.add(Calendar.WEEK_OF_YEAR, -1)
            }

            val weekSeconds = db.logDao().getTotalSecondsSince(startOfWeek.timeInMillis) ?: 0
            val weekCount = db.logDao().getSessionCountSince(startOfWeek.timeInMillis) ?: 0

            binding.txtWeekTime.text = formatDuration(weekSeconds)
            binding.txtWeekCount.text = "$weekCount sessions"

            // This month
            val startOfMonth = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val monthSeconds = db.logDao().getTotalSecondsSince(startOfMonth.timeInMillis) ?: 0
            val monthCount = db.logDao().getSessionCountSince(startOfMonth.timeInMillis) ?: 0

            binding.txtMonthTime.text = formatDuration(monthSeconds)
            binding.txtMonthCount.text = "$monthCount sessions"

            // All time
            val allTimeSeconds = db.logDao().getTotalSecondsAllTime() ?: 0
            val allTimeCount = db.logDao().getSessionCountAllTime() ?: 0

            binding.txtAllTimeTime.text = formatDuration(allTimeSeconds)
            binding.txtAllTimeCount.text = "$allTimeCount sessions"
        }
    }

    private fun loadChains() {
        val min1 = binding.editChainMin1.text.toString().toIntOrNull() ?: 15
        val min2 = binding.editChainMin2.text.toString().toIntOrNull() ?: 30
        val min3 = binding.editChainMin3.text.toString().toIntOrNull() ?: 45

        // Use the first threshold for chains calculation
        val thresholdSeconds = min1 * 60

        lifecycleScope.launch {
            val dailyTotals = db.logDao().getDailyTotals()
            val chains = calculateChains(dailyTotals, thresholdSeconds)
            binding.recyclerChains.adapter = ChainAdapter(chains)
        }
    }

    private fun calculateChains(dailyTotals: List<DailyTotal>, thresholdSeconds: Int): List<Chain> {
        // Build a set of days that meet the threshold
        val qualifyingDays = dailyTotals
            .filter { it.totalSeconds >= thresholdSeconds }
            .map { it.day }
            .toSet()

        if (qualifyingDays.isEmpty()) return emptyList()

        // Sort qualifying days and find consecutive chains
        val sortedDays = qualifyingDays.sorted()
        val chains = mutableListOf<Chain>()
        var chainStart = sortedDays[0]
        var chainEnd = sortedDays[0]
        var chainDays = 1

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (i in 1 until sortedDays.size) {
            val prevDate = dateFormat.parse(sortedDays[i - 1])
            val currDate = dateFormat.parse(sortedDays[i])
            val diffMs = currDate.time - prevDate.time
            val diffDays = diffMs / (24 * 60 * 60 * 1000)

            if (diffDays == 1L) {
                // Consecutive day
                chainEnd = sortedDays[i]
                chainDays++
            } else {
                // Chain broken
                chains.add(Chain(chainStart, chainEnd, chainDays))
                chainStart = sortedDays[i]
                chainEnd = sortedDays[i]
                chainDays = 1
            }
        }
        // Add last chain
        chains.add(Chain(chainStart, chainEnd, chainDays))

        // Sort by days descending
        return chains.sortedByDescending { it.days }
    }

    private fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${totalSeconds}s"
        }
    }

    data class Chain(val startDate: String, val endDate: String, val days: Int)

    inner class ChainAdapter(private val items: List<Chain>) :
        RecyclerView.Adapter<ChainAdapter.ViewHolder>() {

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chain, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val chain = items[position]
            val displayFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
            val parseFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

            val start = parseFormat.parse(chain.startDate)
            val end = parseFormat.parse(chain.endDate)
            val startStr = start?.let { displayFormat.format(it) } ?: chain.startDate
            val endStr = end?.let { displayFormat.format(it) } ?: chain.endDate

            holder.range.text = getString(R.string.chain_date_range, startStr, endStr)
            holder.days.text = getString(R.string.chain_days, chain.days)
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val range: TextView = view.findViewById(R.id.txtChainRange)
            val days: TextView = view.findViewById(R.id.txtChainDays)
        }
    }
}
