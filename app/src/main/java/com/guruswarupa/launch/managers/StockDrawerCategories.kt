package com.guruswarupa.launch.managers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.LayoutMode

internal fun StockDrawerManager.autoCategorizeHomeApps() {
    val homeAdapter = homeGridAdapter
    if (homeAdapter != null) {
        val list = homeAdapter.currentList.toMutableList()
        val changed = categorizeIntoFolders(list, com.guruswarupa.launch.models.Constants.Prefs.STOCK_HOME_FOLDERS)
        if (changed) {
            homeAdapter.updateAppList(list)
            appOrderManager.saveOrder(list, com.guruswarupa.launch.models.Constants.Prefs.STOCK_HOME_APP_ORDER)
        }
    }

    val drawerKey = com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_FOLDERS
    val drawerList = allOrderedApps.filter {
        val pkg = it.activityInfo.packageName
        pkg != AppAdapter.SEPARATOR_PACKAGE && !pkg.startsWith("launcher_")
    }.toMutableList()
    if (categorizeIntoFolders(drawerList, drawerKey)) {
        appOrderManager.saveOrder(drawerList, com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_APP_ORDER)
        recomputeOrderedApps(activity.fullAppList)
        applyCurrentFilter()
    }
}

internal fun StockDrawerManager.categorizeIntoFolders(list: MutableList<ResolveInfo>, foldersKey: String): Boolean {
    val pm = activity.packageManager
    val existingFolderNames = folderManager.getFolders(foldersKey).map { it.name }.toSet()

    val groupable = list.filter { app ->
        !appOrderManager.isFolderEntry(app) &&
            app.activityInfo.packageName != AppAdapter.SEPARATOR_PACKAGE &&
            !app.activityInfo.packageName.startsWith("launcher_") &&
            !com.guruswarupa.launch.managers.WebAppManager.isWebAppPackage(app.activityInfo.packageName)
    }

    val byCategory = groupable.groupBy { app ->
        categoryLabel(pm, app.activityInfo.packageName)
    }.filterKeys { it != null }

    var changed = false
    byCategory.forEach { (categoryName, apps) ->
        if (categoryName == null || apps.size < 2) return@forEach
        if (existingFolderNames.contains(categoryName)) return@forEach

        val appKeys = apps.map { appOrderManager.keyOf(it) }
        val folder = folderManager.createFolder(foldersKey, categoryName, appKeys)
        val folderInfo = appOrderManager.createFolderInfo(folder)

        val firstIndex = list.indexOfFirst { it === apps.first() }
        apps.forEach { app -> list.remove(app) }
        list.add(firstIndex.coerceIn(0, list.size), folderInfo)
        changed = true
    }
    return changed
}

internal fun StockDrawerManager.categoryFromPackageName(packageName: String): String? {
    val name = packageName.lowercase()
    return when {
        listOf("game", "supercell", "king.", "gameloft", "pubg", "roblox", "minecraft", "ludo").any { name.contains(it) } ->
            activity.getString(R.string.category_games)
        listOf("whatsapp", "instagram", "facebook", "twitter", "telegram", "snapchat", "linkedin", "reddit", "discord", "threads", "signal", "messenger", "pinterest").any { name.contains(it) } ->
            activity.getString(R.string.category_social)
        listOf("music", "spotify", "youtube", "netflix", "video", "player", "gallery", "photos", "camera", "podcast", "jio", "hotstar", "prime").any { name.contains(it) } ->
            activity.getString(R.string.category_media)
        listOf("maps", "navigation", "uber", "ola", "rapido", "transit").any { name.contains(it) } ->
            activity.getString(R.string.category_maps)
        listOf("news", "times", "hindu", "inshorts").any { name.contains(it) } ->
            activity.getString(R.string.category_news)
        listOf("docs", "sheets", "slides", "office", "notes", "keep", "calendar", "drive", "mail", "gmail", "outlook", "notion", "todo", "pdf", "calculator", "clock").any { name.contains(it) } ->
            activity.getString(R.string.category_productivity)
        else -> null
    }
}

internal fun StockDrawerManager.categoryLabel(pm: android.content.pm.PackageManager, packageName: String): String? {
    return try {
        val appInfo = pm.getApplicationInfo(packageName, 0)
        when (appInfo.category) {
            android.content.pm.ApplicationInfo.CATEGORY_GAME -> activity.getString(R.string.category_games)
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> activity.getString(R.string.category_social)
            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> activity.getString(R.string.category_productivity)
            android.content.pm.ApplicationInfo.CATEGORY_NEWS -> activity.getString(R.string.category_news)
            android.content.pm.ApplicationInfo.CATEGORY_MAPS -> activity.getString(R.string.category_maps)
            android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> activity.getString(R.string.category_media)
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> activity.getString(R.string.category_media)
            android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> activity.getString(R.string.category_media)
            else -> categoryFromPackageName(packageName)
        }
    } catch (_: Exception) {
        null
    }
}
