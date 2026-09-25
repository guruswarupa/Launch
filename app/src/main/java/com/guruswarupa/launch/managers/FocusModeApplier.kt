package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.utils.TimeUtils
import java.util.concurrent.Executor

class FocusModeApplier(
    private val activity: FragmentActivity,
    private val backgroundExecutor: Executor,
    private val appListManager: AppListManager,
    private val appDockManager: AppDockManager,
    private val sharedPreferences: android.content.SharedPreferences,
    private val searchContainer: LinearLayout,
    private var adapter: AppAdapter?,
    private val fullAppList: MutableList<android.content.pm.ResolveInfo>,
    private val appList: MutableList<android.content.pm.ResolveInfo>,
    private val onUpdateAppSearchManager: () -> Unit,
    private val onUpdateFastScrollerVisibility: () -> Unit,
    private val showOnlyFavoritesInitially: () -> Boolean = { false }
) {

    private fun safeExecute(task: Runnable): Boolean =
        TimeUtils.safeExecuteOn(
            isActivityAlive = { !(activity.isFinishing || activity.isDestroyed) },
            executor = backgroundExecutor
        ) { task.run() }

    fun setAdapter(adapter: AppAdapter) {
        this.adapter = adapter
    }

    fun applyFocusMode(isFocusMode: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        safeExecute {
            try {
                val workspaceMode = appListManager.getWorkspaceMode()
                val finalFilteredApps = appListManager.filterAndPrepareApps(fullAppList, isFocusMode, workspaceMode)
                val sortedFinalList = appListManager.sortAppsAlphabetically(finalFilteredApps, showOnlyFavoritesInitially())
                val listWithSeparators = appListManager.addSeparators(sortedFinalList, showOnlyFavoritesInitially())

                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

                    appList.clear()
                    appList.addAll(listWithSeparators)

                    searchContainer.visibility = if (com.guruswarupa.launch.utils.LayoutMode.isStock(sharedPreferences)) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                    appDockManager.lockDrawerForFocusMode(isFocusMode)
                    adapter?.updateAppList(listWithSeparators)

                    if (!activity.isFinishing) {
                        onUpdateAppSearchManager()
                        onUpdateFastScrollerVisibility()
                    }
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        @SuppressLint("NotifyDataSetChanged")
                        adapter?.notifyDataSetChanged()
                    }
                }
            }
        }
    }
}
