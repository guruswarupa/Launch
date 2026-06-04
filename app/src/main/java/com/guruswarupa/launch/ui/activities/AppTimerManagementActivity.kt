package com.guruswarupa.launch.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.AppTimerManager
import com.guruswarupa.launch.managers.AppUsageMonitor
import com.guruswarupa.launch.managers.DailyUsageManager
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import com.guruswarupa.launch.utils.setDialogInputView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit






class AppTimerManagementActivity : ComponentActivity() {
    companion object {
        private val iconCache = ConcurrentHashMap<String, Drawable>()
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var appTimerManager: AppTimerManager
    private lateinit var dailyUsageManager: DailyUsageManager
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var searchBox: EditText
    private lateinit var summaryText: TextView
    private lateinit var emptyState: TextView
    private lateinit var doneButton: Button
    private lateinit var clearSearchButton: Button
    private lateinit var adapter: AppTimerAdapter
    private var allItems: List<AppTimerItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_timer_management)
        applyContentInsets()

        appTimerManager = AppTimerManager(this)
        dailyUsageManager = DailyUsageManager(this)

        setupViews()
        loadAppsList()
    }

    override fun onBackPressed() {
        finish()
    }

    private fun setupViews() {
        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.wallpaper_background))

        recyclerView = findViewById(R.id.apps_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppTimerAdapter(mutableListOf())
        recyclerView.adapter = adapter

        loadingProgressBar = findViewById(R.id.loading_progress) ?: ProgressBar(this).apply {
            visibility = View.GONE
        }
        searchBox = findViewById(R.id.app_search_box)
        summaryText = findViewById(R.id.summary_text)
        emptyState = findViewById(R.id.empty_state)
        doneButton = findViewById(R.id.done_button)
        clearSearchButton = findViewById(R.id.clear_search_button)

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        doneButton.setOnClickListener { finish() }
        clearSearchButton.setOnClickListener { searchBox.text?.clear() }
    }

    private fun loadAppsList() {
        loadingProgressBar.visibility = View.VISIBLE
        recyclerView.alpha = 0f

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val usageMap = dailyUsageManager.getTodayUsageMap()
                val pm = packageManager
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val launcherApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(launcherIntent, 0)
                }

                launcherApps
                    .filter { it.activityInfo?.packageName != packageName }
                    .distinctBy { it.activityInfo.packageName }
                    .filter { (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) }
                    .map { appInfo ->
                        val pkgName = appInfo.activityInfo.packageName
                        AppTimerItem(
                            name = appInfo.loadLabel(pm).toString(),
                            packageName = pkgName,
                            limitMs = appTimerManager.getDailyLimit(pkgName),
                            usageTimeMs = usageMap[pkgName] ?: 0L,
                            enabled = dailyUsageManager.isTimerEnabled(pkgName)
                        )
                    }.sortedBy { it.name.lowercase() }
            }

            loadingProgressBar.visibility = View.GONE
            recyclerView.alpha = 1.0f
            allItems = apps
            applyFilter(searchBox.text?.toString().orEmpty())
        }
    }

    private fun applyContentInsets() {
        val mainContent = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + 16.toPx(),
                view.paddingRight,
                systemBars.bottom + 16.toPx()
            )
            insets
        }
    }

    private fun showEditLimitDialog(item: AppTimerItem, position: Int) {
        val currentLimit = appTimerManager.getDailyLimit(item.packageName)
        val currentLimitMinutes = if (currentLimit > 0) currentLimit / 60000 else 0

        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.daily_usage_hint_minutes_disable)
            setText(currentLimitMinutes.toString())
            DialogStyler.styleInput(this@AppTimerManagementActivity, this)
        }

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(getString(R.string.daily_usage_set_limit_title, item.name))
            .setMessage(getString(R.string.daily_usage_set_limit_message))
            .setDialogInputView(this, input)
            .setPositiveButton(R.string.daily_usage_action_set) { _, _ ->
                try {
                    val minutes = input.text.toString().toLongOrNull() ?: 0L
                    val limitMs = minutes * 60000L

                    appTimerManager.setDailyLimit(item.packageName, limitMs)
                    dailyUsageManager.setTimerEnabled(item.packageName, limitMs > 0)

                    val updatedItem = item.copy(limitMs = limitMs, enabled = limitMs > 0)
                    (recyclerView.adapter as? AppTimerAdapter)?.updateItem(updatedItem, position)

                    Toast.makeText(
                        this,
                        if (limitMs > 0) getString(R.string.daily_usage_limit_set, formatTime(limitMs))
                        else getString(R.string.daily_usage_limit_disabled),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: NumberFormatException) {
                    Toast.makeText(this, getString(R.string.daily_usage_invalid_number), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatTime(ms: Long): String {
        if (ms == 0L) return "0m"
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    private fun applyFilter(query: String) {
        val trimmedQuery = query.trim()
        val filteredItems = if (trimmedQuery.isEmpty()) {
            allItems
        } else {
            allItems.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
        }

        adapter.submitList(filteredItems)
        summaryText.text = buildSummary(filteredItems)
        emptyState.visibility = if (filteredItems.isEmpty() && allItems.isNotEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (filteredItems.isEmpty() && allItems.isNotEmpty()) View.GONE else View.VISIBLE
    }

    private fun buildSummary(items: List<AppTimerItem>): String {
        val activeTimers = items.count { it.enabled && it.limitMs > 0 }
        return "${items.size} apps • $activeTimers active timers"
    }

    private data class AppTimerItem(
        val name: String,
        val packageName: String,
        val limitMs: Long,
        val usageTimeMs: Long,
        val enabled: Boolean
    )

    private inner class AppTimerAdapter(
        private val items: MutableList<AppTimerItem>
    ) : RecyclerView.Adapter<AppTimerAdapter.ViewHolder>() {

        init {
            setHasStableIds(true)
        }

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.app_icon)
            val name: TextView = v.findViewById(R.id.app_name)
            val limit: TextView = v.findViewById(R.id.app_limit)
            val usage: TextView = v.findViewById(R.id.app_usage)
            val sw: SwitchCompat = v.findViewById(R.id.timer_switch)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_app_timer, p, false))

        override fun getItemId(position: Int): Long = items[position].packageName.hashCode().toLong()

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val item = items[p]
            h.itemView.tag = item.packageName
            bindIcon(h, item.packageName)
            h.name.text = item.name
            h.limit.text = if (item.limitMs > 0) "Limit: ${formatTime(item.limitMs)}" else "No limit"
            h.usage.text = "Used: ${formatTime(item.usageTimeMs)}"

            h.sw.setOnCheckedChangeListener(null)
            h.sw.isChecked = item.enabled
            h.sw.setOnCheckedChangeListener { _, isChecked ->
                val currentPosition = h.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
                val currentItem = items[currentPosition]
                dailyUsageManager.setTimerEnabled(currentItem.packageName, isChecked)
                AppUsageMonitor.syncMonitoring(this@AppTimerManagementActivity)
                items[currentPosition] = currentItem.copy(enabled = isChecked)
                notifyItemChanged(currentPosition)
            }

            h.itemView.setOnClickListener {
                val currentPosition = h.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
                showEditLimitDialog(items[currentPosition], currentPosition)
            }
        }

        fun updateItem(item: AppTimerItem, position: Int) {
            items[position] = item
            notifyItemChanged(position)
            val currentQuery = searchBox.text?.toString().orEmpty()
            allItems = allItems.map {
                if (it.packageName == item.packageName) item else it
            }
            if (currentQuery.isEmpty()) {
                summaryText.text = buildSummary(items)
            } else {
                applyFilter(currentQuery)
            }
        }

        fun submitList(newItems: List<AppTimerItem>) {
            val diffCallback = AppTimerItemDiffCallback(items, newItems)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            items.clear()
            items.addAll(newItems)
            diffResult.dispatchUpdatesTo(this)
        }

        private fun bindIcon(holder: ViewHolder, packageName: String) {
            val cachedIcon = iconCache[packageName]
            if (cachedIcon != null) {
                holder.icon.setImageDrawable(cachedIcon)
                return
            }

            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)

            lifecycleScope.launch(Dispatchers.IO) {
                val icon = try {
                    packageManager.getApplicationIcon(packageName)
                } catch (_: Exception) {
                    null
                }

                if (icon != null) {
                    iconCache[packageName] = icon
                }

                withContext(Dispatchers.Main) {
                    if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION &&
                        holder.itemView.tag == packageName) {
                        holder.icon.setImageDrawable(icon ?: holder.icon.drawable)
                    }
                }
            }
        }
    }

    private class AppTimerItemDiffCallback(
        private val oldList: List<AppTimerItem>,
        private val newList: List<AppTimerItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].packageName == newList[newItemPosition].packageName
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
