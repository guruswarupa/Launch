package com.guruswarupa.launch.ui.adapters

import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.managers.FocusModeManager
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.WebAppIconFetcher
import com.guruswarupa.launch.managers.WebAppManager
import com.guruswarupa.launch.utils.AppDisplayHelper
import java.util.concurrent.ExecutorService

/**
 * Lets the user pick which apps to hide while focus mode is active - a blocklist rather than an
 * allowlist, since most people only want to shut off a handful of distracting apps and keep
 * everything else reachable. Icons load off the main thread through a small [LruCache] so
 * scrolling a long app list stays smooth (the previous version called [ResolveInfo.loadIcon]
 * synchronously in [onBindViewHolder], which is a real decode on every bind); labels are resolved
 * once up front instead of on every bind/filter pass.
 */
class FocusModeAppAdapter(
    private val appList: List<ResolveInfo>,
    private val packageManager: PackageManager,
    focusModeManager: FocusModeManager,
    private val iconExecutor: ExecutorService,
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<FocusModeAppAdapter.ViewHolder>() {

    private val blockedApps = focusModeManager.getBlockedApps().toMutableSet()
    private val iconCache = LruCache<String, Drawable>(256)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val labelByPackage: Map<String, String> =
        appList.associate { it.activityInfo.packageName to AppDisplayHelper.getLabel(it, packageManager) }
    private var filtered: List<ResolveInfo> = appList

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView = view.findViewById(R.id.app_name)
        val appCheckbox: SwitchCompat = view.findViewById(R.id.app_checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.focus_mode_app_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = filtered[position]
        val packageName = appInfo.activityInfo.packageName

        holder.itemView.tag = packageName
        holder.appName.text = labelByPackage[packageName] ?: packageName

        if (WebAppManager.isWebAppPackage(packageName)) {
            holder.appIcon.setImageResource(R.drawable.ic_browser)
            val siteUrl = appInfo.activityInfo.nonLocalizedLabel?.toString().orEmpty()
            if (siteUrl.isNotBlank()) {
                WebAppIconFetcher.loadIcon(holder.itemView.context, siteUrl) { drawable ->
                    if (holder.itemView.tag == packageName && drawable != null) {
                        holder.appIcon.setImageDrawable(drawable)
                    }
                }
            }
        } else {
            val cached = iconCache.get(packageName)
            if (cached != null) {
                holder.appIcon.setImageDrawable(cached)
            } else {
                holder.appIcon.setImageDrawable(null)
                try {
                    iconExecutor.submit {
                        val drawable = try {
                            appInfo.loadIcon(packageManager)
                        } catch (_: Exception) {
                            null
                        }
                        if (drawable != null) iconCache.put(packageName, drawable)
                        mainHandler.post {
                            if (holder.itemView.tag == packageName && drawable != null) {
                                holder.appIcon.setImageDrawable(drawable)
                            }
                        }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    // The host activity is finishing and already shut the executor down - the
                    // row is about to be destroyed too, so there's nothing to show it in.
                }
            }
        }

        holder.appCheckbox.setOnCheckedChangeListener(null)
        holder.appCheckbox.isChecked = blockedApps.contains(packageName)

        holder.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                blockedApps.add(packageName)
            } else {
                blockedApps.remove(packageName)
            }
            onSelectionChanged(blockedApps.size)
        }

        holder.itemView.setOnClickListener {
            holder.appCheckbox.isChecked = !holder.appCheckbox.isChecked
        }
    }

    /** Narrows the visible rows to those whose label contains [query] (case-insensitive). */
    fun filter(query: String) {
        filtered = if (query.isBlank()) {
            appList
        } else {
            appList.filter { (labelByPackage[it.activityInfo.packageName] ?: "").contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }

    fun getBlockedApps(): Set<String> = blockedApps

    fun blockedCount(): Int = blockedApps.size

    override fun getItemCount() = filtered.size
}
