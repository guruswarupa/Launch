package com.guruswarupa.launch.ui.adapters

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
data class AppPrivacyInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val grantedPermissions: List<String>,
    val severity: Int,
    val isSideloaded: Boolean = false,
    var isExpanded: Boolean = false
)

class PrivacyDashboardAdapter(
    private var apps: List<AppPrivacyInfo>
) : RecyclerView.Adapter<PrivacyDashboardAdapter.ViewHolder>() {
    companion object {
        private val iconCache = ConcurrentHashMap<String, Drawable>()
    }

    private val previousApps = mutableMapOf<String, AppPrivacyInfo>()
    private val iconExecutor = Executors.newFixedThreadPool(2)

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return fnv1a64(apps[position].packageName)
    }

    @SuppressLint("InlinedApi")
    private val permissionLabels = mapOf(
        Manifest.permission.CAMERA to R.string.perm_camera,
        Manifest.permission.RECORD_AUDIO to R.string.perm_microphone,
        Manifest.permission.READ_CONTACTS to R.string.perm_contacts,
        Manifest.permission.ACCESS_FINE_LOCATION to R.string.perm_location,
        Manifest.permission.ACCESS_COARSE_LOCATION to R.string.perm_location_approx,
        Manifest.permission.READ_EXTERNAL_STORAGE to R.string.perm_storage,
        Manifest.permission.WRITE_EXTERNAL_STORAGE to R.string.perm_storage_write,
        Manifest.permission.READ_SMS to R.string.perm_sms,
        Manifest.permission.CALL_PHONE to R.string.perm_phone,
        Manifest.permission.POST_NOTIFICATIONS to R.string.perm_notifications,
        "android.permission.READ_MEDIA_IMAGES" to R.string.perm_photos,
        "android.permission.READ_MEDIA_VIDEO" to R.string.perm_videos,
        "android.permission.READ_MEDIA_AUDIO" to R.string.perm_audio
    )

    @SuppressLint("InlinedApi")
    private val permissionDescriptions = mapOf(
        Manifest.permission.CAMERA to R.string.desc_camera,
        Manifest.permission.RECORD_AUDIO to R.string.desc_microphone,
        Manifest.permission.READ_CONTACTS to R.string.desc_contacts,
        Manifest.permission.ACCESS_FINE_LOCATION to R.string.desc_location,
        Manifest.permission.ACCESS_COARSE_LOCATION to R.string.desc_location_approx,
        Manifest.permission.READ_EXTERNAL_STORAGE to R.string.desc_storage,
        Manifest.permission.WRITE_EXTERNAL_STORAGE to R.string.desc_storage_write,
        Manifest.permission.READ_SMS to R.string.desc_sms,
        Manifest.permission.CALL_PHONE to R.string.desc_phone,
        Manifest.permission.POST_NOTIFICATIONS to R.string.desc_notifications,
        "android.permission.READ_MEDIA_IMAGES" to R.string.desc_photos,
        "android.permission.READ_MEDIA_VIDEO" to R.string.desc_videos,
        "android.permission.READ_MEDIA_AUDIO" to R.string.desc_audio
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView = view.findViewById(R.id.app_name)
        val packageName: TextView = view.findViewById(R.id.package_name)
        val permissionsSummary: TextView = view.findViewById(R.id.permissions_summary)
        val permissionCount: TextView = view.findViewById(R.id.permission_count)
        val detailsContainer: LinearLayout = view.findViewById(R.id.details_container)
        val appDetails: TextView = view.findViewById(R.id.app_details)
        val managePermissionsButton: Button = view.findViewById(R.id.manage_permissions_button)
        val sideloadedBadge: TextView = view.findViewById(R.id.sideloaded_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_privacy_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context

        holder.itemView.tag = app.packageName
        bindIcon(holder, app)
        holder.appName.text = app.appName
        holder.packageName.text = app.packageName

        holder.sideloadedBadge.visibility = if (app.isSideloaded) View.VISIBLE else View.GONE

        if (app.grantedPermissions.isEmpty()) {
            holder.permissionsSummary.text = context.getString(R.string.no_sensitive_permissions)
            holder.permissionsSummary.alpha = 0.5f
            holder.permissionCount.visibility = View.GONE
        } else {
            val names = app.grantedPermissions.map {
                val resId = permissionLabels[it]
                if (resId != null) context.getString(resId) else it.split(".").last()
            }
            holder.permissionsSummary.text = context.getString(R.string.access_to_format, names.joinToString(", "))
            holder.permissionsSummary.alpha = 1.0f

            holder.permissionCount.visibility = View.VISIBLE
            holder.permissionCount.text = app.grantedPermissions.size.toString()

            val color = when (app.severity) {
                2 -> 0xFFFF4444.toInt()
                1 -> 0xFFFFBB33.toInt()
                else -> 0xFF99CC00.toInt()
            }
            holder.permissionCount.setTextColor(color)
        }

        holder.detailsContainer.visibility = if (app.isExpanded) View.VISIBLE else View.GONE

        if (app.isExpanded) {
            val details = StringBuilder()
            if (app.grantedPermissions.isEmpty()) {
                details.append(context.getString(R.string.no_sensitive_permissions_granted))
            } else {
                details.append(context.getString(R.string.detailed_analysis))
                app.grantedPermissions.forEach { perm ->
                    val labelRes = permissionLabels[perm]
                    val label = if (labelRes != null) context.getString(labelRes) else perm.split(".").last()

                    val descRes = permissionDescriptions[perm]
                    val desc = if (descRes != null) {
                        context.getString(descRes)
                    } else {
                        context.getString(R.string.default_permission_description, label)
                    }

                    details.append(context.getString(R.string.permission_detail_item, label, desc))
                }
            }
            holder.appDetails.text = details.toString().trim()

            holder.managePermissionsButton.setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        holder.itemView.setOnClickListener {
            app.isExpanded = !app.isExpanded
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = apps.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newApps: List<AppPrivacyInfo>) {

        val newAppsWithState = newApps.map { newApp ->
            previousApps[newApp.packageName]?.let { oldApp ->
                newApp.copy(isExpanded = oldApp.isExpanded)
            } ?: newApp
        }

        val diffResult = DiffUtil.calculateDiff(AppPrivacyDiffCallback(apps, newAppsWithState))

        apps = newAppsWithState


        previousApps.clear()
        newAppsWithState.forEach { previousApps[it.packageName] = it }


        diffResult.dispatchUpdatesTo(this)
    }

    private fun bindIcon(holder: ViewHolder, app: AppPrivacyInfo) {
        val cachedIcon = iconCache[app.packageName] ?: app.icon
        if (cachedIcon != null) {
            iconCache[app.packageName] = cachedIcon
            holder.appIcon.setImageDrawable(cachedIcon)
            return
        }

        holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)

        iconExecutor.execute {
            val icon = try {
                holder.itemView.context.packageManager.getApplicationIcon(app.packageName)
            } catch (_: Exception) {
                null
            } ?: return@execute

            iconCache[app.packageName] = icon
            holder.itemView.post {
                if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION &&
                    holder.itemView.tag == app.packageName) {
                    holder.appIcon.setImageDrawable(icon)
                }
            }
        }
    }
}

private class AppPrivacyDiffCallback(
    private val oldList: List<AppPrivacyInfo>,
    private val newList: List<AppPrivacyInfo>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].packageName == newList[newItemPosition].packageName
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldApp = oldList[oldItemPosition]
        val newApp = newList[newItemPosition]
        return oldApp.packageName == newApp.packageName &&
                oldApp.appName == newApp.appName &&
                oldApp.grantedPermissions == newApp.grantedPermissions &&
                oldApp.severity == newApp.severity &&
                oldApp.isSideloaded == newApp.isSideloaded &&
                oldApp.isExpanded == newApp.isExpanded
    }
}

private fun fnv1a64(value: String): Long {
    var hash: ULong = 0xcbf29ce484222325uL
    for (byte in value.toByteArray()) {
        hash = hash xor byte.toULong()
        hash = hash * 0x100000001b3uL
    }
    return (hash and 0x7FFFFFFFFFFFFFFFuL).toLong()
}
