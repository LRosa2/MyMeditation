package com.mysimplemeditation.app.ui.stats

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
import com.mysimplemeditation.app.R
import com.mysimplemeditation.app.data.AppDatabase
import com.mysimplemeditation.app.databinding.ActivityStatisticsBinding
import kotlinx.coroutines.launch

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

        binding.btnViewChains.setOnClickListener {
            startActivity(Intent(this, ChainsActivity::class.java))
        }
        binding.btnViewLog.setOnClickListener {
            startActivity(Intent(this, SittingLogActivity::class.java))
        }
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

    private fun formatDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${totalSeconds}s"
        }
    }
}
