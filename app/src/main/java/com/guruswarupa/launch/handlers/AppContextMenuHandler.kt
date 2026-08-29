package com.guruswarupa.launch.handlers

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.core.ShareManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.ui.activities.WebAppActivity
import com.guruswarupa.launch.ui.activities.WebAppSettingsActivity
import java.util.concurrent.ExecutorService

class AppContextMenuHandler(
    private val activity: MainActivity,
    private val executor: ExecutorService,
    private val labelResolver: (String, ResolveInfo) -> String,
    private val onAppModified: () -> Unit,
    private val openWebApp: (ResolveInfo) -> Unit,
    private val shareManager: ShareManager
) {

    @SuppressLint("NotifyDataSetChanged")
    fun showAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
        val popupMenu = PopupMenu(activity, view, Gravity.END, 0, R.style.PopupMenuStyle)
        popupMenu.menuInflater.inflate(R.menu.app_context_menu, popupMenu.menu)
        val textColor = ContextCompat.getColor(activity, R.color.text)
        val appName = labelResolver(packageName, appInfo)

        val dailyLimitItem = popupMenu.menu.add(0, 100, 0, activity.getString(R.string.daily_usage_set_limit_action))
        val limitSpannable = android.text.SpannableString(dailyLimitItem.title)
        limitSpannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, limitSpannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        dailyLimitItem.title = limitSpannable

        val usageHeader = popupMenu.menu.findItem(R.id.usage_header)
        if (usageHeader != null) {
            executor.execute {
                val usageTime = activity.usageStatsManager.getAppUsageTime(packageName)
                val formattedTime = activity.usageStatsManager.formatUsageTime(usageTime)
                if (activity.isFinishing || activity.isDestroyed) return@execute
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    usageHeader.title = activity.getString(R.string.app_context_usage_format, formattedTime)
                    val spannable = android.text.SpannableString(usageHeader.title)
                    spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    usageHeader.title = spannable
                }
            }
        }

        val toggleSessionTimerItem = popupMenu.menu.findItem(R.id.toggle_session_timer)
        if (toggleSessionTimerItem != null) {
            val isEnabled = activity.appTimerManager.isSessionTimerEnabled(packageName)
            toggleSessionTimerItem.title = activity.getString(
                if (isEnabled) R.string.app_context_disable_session_timer else R.string.app_context_enable_session_timer
            )
            val spannable = android.text.SpannableString(toggleSessionTimerItem.title)
            spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            toggleSessionTimerItem.title = spannable
        }

        val favoriteMenuItem = popupMenu.menu.findItem(R.id.toggle_favorite)
        if (favoriteMenuItem != null) {
            val isFavorite = activity.favoriteAppManager.isFavoriteApp(packageName)
            favoriteMenuItem.title = if (isFavorite) "Remove from Favorites" else "Add to Favorites"
            val spannable = android.text.SpannableString(favoriteMenuItem.title)
            spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            favoriteMenuItem.title = spannable
        }

        val hideMenuItem = popupMenu.menu.findItem(R.id.toggle_hide)
        if (hideMenuItem != null) {
            try {
                val isHidden = activity.hiddenAppManager.isAppHidden(packageName)
                hideMenuItem.title = if (isHidden) "Unhide App" else "Hide App"
                val spannable = android.text.SpannableString(hideMenuItem.title)
                spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                hideMenuItem.title = spannable
            } catch (_: UninitializedPropertyAccessException) {
                hideMenuItem.isVisible = false
            }
        }

        for (i in 0 until popupMenu.menu.size()) {
            val item = popupMenu.menu.getItem(i)
            val itemTitle = item.title?.toString() ?: continue
            val spannable = android.text.SpannableString(itemTitle)
            spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            item.title = spannable
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                100 -> {
                    activity.appTimerManager.showDailyLimitDialog(appName, packageName) {
                        onAppModified()
                    }
                    true
                }
                R.id.toggle_session_timer -> {
                    val isEnabled = activity.appTimerManager.isSessionTimerEnabled(packageName)
                    activity.appTimerManager.setSessionTimerEnabled(packageName, !isEnabled)
                    val sessionTimerStatus = activity.getString(
                        if (!isEnabled) R.string.app_context_session_timer_enabled else R.string.app_context_session_timer_disabled
                    )
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.app_context_session_timer_changed, sessionTimerStatus, appName),
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
                R.id.app_info -> {
                    showAppInfo(packageName)
                    true
                }
                R.id.share_app -> {
                    shareApp(packageName, appInfo)
                    true
                }
                R.id.uninstall_app -> {
                    uninstallApp(packageName)
                    true
                }
                R.id.toggle_favorite -> {
                    toggleFavoriteApp(packageName, appInfo)
                    true
                }
                R.id.toggle_hide -> {
                    toggleHideApp(packageName, appInfo)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
        fixPopupMenuTextColors(popupMenu)
    }

    fun showWebAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
        val popupMenu = PopupMenu(activity, view, Gravity.END, 0, R.style.PopupMenuStyle)
        val textColor = ContextCompat.getColor(activity, R.color.text)
        val appName = appInfo.activityInfo.name

        popupMenu.menu.add(0, 200, 0, activity.getString(R.string.open_web_app))
        popupMenu.menu.add(0, 201, 1, activity.getString(R.string.edit_web_app))
        popupMenu.menu.add(0, 202, 2, activity.getString(R.string.open_in_browser))
        popupMenu.menu.add(0, 203, 3, activity.getString(R.string.remove_web_app))
        popupMenu.menu.add(0, 204, 4, if (activity.favoriteAppManager.isFavoriteApp(packageName)) activity.getString(R.string.remove_from_favorites_plain) else activity.getString(R.string.add_to_favorites_plain))
        popupMenu.menu.add(0, 205, 5, if (activity.hiddenAppManager.isAppHidden(packageName)) activity.getString(R.string.unhide_app_plain) else activity.getString(R.string.hide_app_plain))

        for (i in 0 until popupMenu.menu.size()) {
            val item = popupMenu.menu.getItem(i)
            val spannable = android.text.SpannableString(item.title)
            spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            item.title = spannable
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                200 -> {
                    openWebApp(appInfo)
                    true
                }
                201 -> {
                    activity.startActivity(Intent(activity, WebAppSettingsActivity::class.java))
                    true
                }
                202 -> {
                    val url = appInfo.activityInfo.nonLocalizedLabel?.toString().orEmpty()
                    if (url.isNotBlank()) {
                        val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
                        if (browserIntent.resolveActivity(activity.packageManager) != null) {
                            activity.startActivity(browserIntent)
                        } else {
                            Toast.makeText(activity, R.string.web_app_load_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                203 -> {
                    val webApp = activity.webAppManager.getWebApp(packageName)
                    if (webApp != null) {
                        activity.webAppManager.removeWebApp(webApp.id)
                        Toast.makeText(activity, activity.getString(R.string.removed_web_app, appName), Toast.LENGTH_SHORT).show()
                        activity.sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply { setPackage(activity.packageName) })
                    }
                    true
                }
                204 -> {
                    toggleFavoriteApp(packageName, appInfo)
                    true
                }
                205 -> {
                    toggleHideApp(packageName, appInfo)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
        fixPopupMenuTextColors(popupMenu)
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun fixPopupMenuTextColors(popupMenu: PopupMenu) {
        try {
            val textColor = TypographyManager.getConfiguredFontColor(activity) ?: ContextCompat.getColor(activity, R.color.text)
            val popupField = popupMenu.javaClass.getDeclaredField("mPopup")
            popupField.isAccessible = true
            val menuPopupHelper = popupField.get(popupMenu)
            val menuPopupHelperClass = menuPopupHelper?.javaClass
            val listViewFieldNames = arrayOf("mDropDownList", "mPopup", "mListView")
            var listView: android.widget.ListView? = null

            for (fieldName in listViewFieldNames) {
                try {
                    val listViewField = menuPopupHelperClass?.getDeclaredField(fieldName)
                    listViewField?.isAccessible = true
                    val result = listViewField?.get(menuPopupHelper)
                    if (result is android.widget.ListView) {
                        listView = result
                        break
                    }
                } catch (_: NoSuchFieldException) {
                }
            }

            fun fixTextColors(view: View) {
                if (view is TextView) {
                    view.setTextColor(textColor)
                } else if (view is ViewGroup) {
                    findTextViewsAndSetColor(view, textColor)
                }
            }

            listView?.let { lv ->
                try {
                    for (i in 0 until lv.childCount) fixTextColors(lv.getChildAt(i))
                } catch (_: Exception) {
                }
                lv.post {
                    try {
                        for (i in 0 until lv.childCount) {
                            val itemView = lv.getChildAt(i)
                            if (itemView is TextView) itemView.setTextColor(textColor) else if (itemView is ViewGroup) findTextViewsAndSetColor(itemView, textColor)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun findTextViewsAndSetColor(viewGroup: ViewGroup, color: Int) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView) child.setTextColor(color) else if (child is ViewGroup) findTextViewsAndSetColor(child, color)
        }
    }

    private fun showAppInfo(packageName: String) {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = "package:$packageName".toUri() })
    }

    private fun shareApp(packageName: String, appInfo: ResolveInfo) {
        val appName = labelResolver(packageName, appInfo)
        shareManager.shareApk(packageName, appName)
    }

    private fun uninstallApp(packageName: String) {
        @Suppress("DEPRECATION")
        activity.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { data = "package:$packageName".toUri() })
    }

    private fun toggleFavoriteApp(packageName: String, appInfo: ResolveInfo) {
        val appName = labelResolver(packageName, appInfo)
        if (activity.favoriteAppManager.isFavoriteApp(packageName)) {
            activity.favoriteAppManager.removeFavoriteApp(packageName)
            Toast.makeText(activity, activity.getString(R.string.removed_from_favorites, appName), Toast.LENGTH_SHORT).show()

            val remainingFavorites = activity.favoriteAppManager.getFavoriteApps()
            if (remainingFavorites.isEmpty() && activity.showOnlyFavoritesInitially) {
                activity.showOnlyFavoritesInitially = false
            }
        } else {
            activity.favoriteAppManager.addFavoriteApp(packageName)
            Toast.makeText(activity, activity.getString(R.string.added_to_favorites, appName), Toast.LENGTH_SHORT).show()
        }
        activity.filterAppsWithoutReload()
    }

    private fun toggleHideApp(packageName: String, appInfo: ResolveInfo) {
        try {
            val appName = labelResolver(packageName, appInfo)
            if (activity.hiddenAppManager.isAppHidden(packageName)) {
                activity.hiddenAppManager.unhideApp(packageName)
                Toast.makeText(activity, activity.getString(R.string.unhid_app, appName), Toast.LENGTH_SHORT).show()
            } else {
                activity.hiddenAppManager.hideApp(packageName)
                Toast.makeText(activity, activity.getString(R.string.hid_app, appName), Toast.LENGTH_SHORT).show()
            }
            activity.filterAppsWithoutReload()
        } catch (_: UninitializedPropertyAccessException) {
            Toast.makeText(activity, activity.getString(R.string.hidden_apps_feature_not_available), Toast.LENGTH_SHORT).show()
        }
    }
}
