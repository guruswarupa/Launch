package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import android.widget.Button
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.adapters.AppUsageAdapter
import com.guruswarupa.launch.ui.adapters.AppUsageItem
import com.guruswarupa.launch.ui.views.DailyUsagePieView
import com.guruswarupa.launch.ui.views.WeeklyUsageGraphView
import com.guruswarupa.launch.utils.TimeUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread




class UsageStatsDisplayManager(
    private val activity: MainActivity,
    private val usageStatsManager: AppUsageStatsManager,
    private val weeklyUsageGraph: WeeklyUsageGraphView,
    private val adapter: AppAdapter,
    private val recyclerView: RecyclerView
) {
    private var lastUpdateDate: String = ""
    private val weeklyLoadInProgress = AtomicBoolean(false)
    private lateinit var permissionButton: Button

    init {

        weeklyUsageGraph.onDaySelected = { day, appUsages ->
            showDailyUsageDialog(day, appUsages)
        }


        initializePermissionButton()
    }

    private fun initializePermissionButton() {
        permissionButton = activity.findViewById(R.id.request_usage_stats_permission_button)
        permissionButton.setOnClickListener {
            requestUsageStatsPermission()
        }


        updatePermissionButtonVisibility()
    }

    private fun updatePermissionButtonVisibility() {
        if (usageStatsManager.hasUsageStatsPermission()) {
            permissionButton.visibility = android.view.View.GONE
        } else {
            permissionButton.visibility = android.view.View.VISIBLE
        }
    }

    private fun requestUsageStatsPermission() {

        val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
        activity.startActivity(intent)
    }




    fun refreshPermissionButton() {
        updatePermissionButtonVisibility()
        if (usageStatsManager.hasUsageStatsPermission()) {
            loadWeeklyUsageData()
        }
    }

    fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.DAY_OF_YEAR)}-${calendar.get(Calendar.YEAR)}"
    }

    fun checkDateChangeAndRefreshUsage() {
        val currentDate = getCurrentDateString()
        if (currentDate != lastUpdateDate) {
            lastUpdateDate = currentDate
            refreshUsageStats()
        }
    }

    fun loadWeeklyUsageData() {
        if (!usageStatsManager.hasUsageStatsPermission()) return
        if (!weeklyLoadInProgress.compareAndSet(false, true)) return

        thread(name = "weekly-usage-loader") {
            try {
                val weeklyData = usageStatsManager.getWeeklyUsageData()
                val appUsageData = usageStatsManager.getWeeklyAppUsageData()

                activity.runOnUiThread {
                    weeklyUsageGraph.setUsageData(weeklyData)
                    weeklyUsageGraph.setAppUsageData(appUsageData)
                }
            } finally {
                weeklyLoadInProgress.set(false)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshUsageStats() {

        activity.runOnUiThread {
            adapter.clearUsageCache()

            val layoutManager = recyclerView.layoutManager
            if (layoutManager is LinearLayoutManager) {
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                    adapter.notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1)
                } else {
                    adapter.notifyDataSetChanged()
                }
            } else {
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun showDailyUsageDialog(day: String, appUsages: Map<String, Long>) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_daily_usage, null)
        val dayTitle = dialogView.findViewById<TextView>(R.id.day_title)
        val totalTime = dialogView.findViewById<TextView>(R.id.total_time)
        val pieChart = dialogView.findViewById<DailyUsagePieView>(R.id.daily_pie_chart)
        val appUsageList = dialogView.findViewById<RecyclerView>(R.id.app_usage_list)
        val closeButton = dialogView.findViewById<Button>(R.id.close_button)


        dayTitle.text = day


        val totalUsage = appUsages.values.sum()
        val totalTimeText = formatUsageTimeForDialog(totalUsage)
        totalTime.text = activity.getString(R.string.total_usage_format, totalTimeText)


        pieChart.setAppUsageData(appUsages)


        val sortedApps = appUsages.toList().sortedByDescending { it.second }
        val totalUsageFloat = totalUsage.toFloat()
        val appUsageItems = sortedApps.mapIndexed { index, (appName, usage) ->
            val percentage = if (totalUsageFloat > 0) (usage.toFloat() / totalUsageFloat * 100f) else 0f
            val color = pieChart.getColorForApp(index)
            AppUsageItem(appName, usage, percentage, color)
        }

        appUsageList.layoutManager = LinearLayoutManager(activity)
        appUsageList.adapter = AppUsageAdapter(appUsageItems)


        val dialog = android.app.AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun formatUsageTimeForDialog(timeInMillis: Long): String = TimeUtils.formatDuration(timeInMillis)
}
