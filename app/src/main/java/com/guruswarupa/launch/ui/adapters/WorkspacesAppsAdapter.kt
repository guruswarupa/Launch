package com.guruswarupa.launch.ui.adapters

import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.WebAppIconFetcher
import com.guruswarupa.launch.managers.WebAppManager
import com.guruswarupa.launch.utils.AppDisplayHelper

class WorkspacesAppsAdapter(
    private val apps: List<ResolveInfo>,
    private val packageManager: PackageManager,
    private val selectedPackages: MutableSet<String>,
    private val onSelectionChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<WorkspacesAppsAdapter.ViewHolder>() {

    private val labelByPackage: Map<String, String> =
        apps.associate { it.activityInfo.packageName to AppDisplayHelper.getLabel(it, packageManager) }
    private var filtered: List<ResolveInfo> = apps

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
        val app = filtered[position]
        val packageName = app.activityInfo.packageName

        holder.itemView.tag = packageName
        holder.appName.text = labelByPackage[packageName] ?: packageName

        if (WebAppManager.isWebAppPackage(packageName)) {
            holder.appIcon.setImageResource(R.drawable.ic_browser)
            val siteUrl = app.activityInfo.nonLocalizedLabel?.toString().orEmpty()
            if (siteUrl.isNotBlank()) {
                WebAppIconFetcher.loadIcon(holder.itemView.context, siteUrl) { drawable ->
                    if (holder.itemView.tag == packageName && drawable != null) {
                        holder.appIcon.setImageDrawable(drawable)
                    }
                }
            }
        } else {
            try {
                holder.appIcon.setImageDrawable(app.loadIcon(packageManager))
            } catch (_: Exception) {
                holder.appIcon.setImageDrawable(null)
            }
        }

        holder.appCheckbox.setOnCheckedChangeListener(null)
        holder.appCheckbox.isChecked = selectedPackages.contains(packageName)
        holder.appCheckbox.jumpDrawablesToCurrentState()

        holder.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (selectedPackages.contains(packageName) != isChecked) {
                onSelectionChanged(packageName, isChecked)
            }
        }

        holder.itemView.setOnClickListener {
            val newState = !holder.appCheckbox.isChecked
            holder.appCheckbox.isChecked = newState
        }
    }

    fun filter(query: String) {
        filtered = if (query.isBlank()) {
            apps
        } else {
            apps.filter { (labelByPackage[it.activityInfo.packageName] ?: "").contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = filtered.size
}
