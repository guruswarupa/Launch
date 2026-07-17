package com.guruswarupa.launch.managers

import android.content.pm.ResolveInfo
import android.util.Log
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.MainActivity
import java.util.ConcurrentModificationException
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class AppListUIUpdater(
    private val activity: MainActivity,
    private val recyclerView: RecyclerView,
    private var adapter: AppAdapter?,
    private val appList: MutableList<ResolveInfo>,
    private val fullAppList: MutableList<ResolveInfo>,
    private val appListLoader: AppListLoader,
    private val appListManager: AppListManager,
    private val backgroundExecutor: Executor,
    private val searchBox: AutoCompleteTextView
) {
    companion object {
        private const val TAG = "AppListUIUpdater"
    }
    private var isGridMode: Boolean = false
    private var preservedWorkProfileListOnce = false

    fun setAdapter(adapter: AppAdapter) {
        this.adapter = adapter
    }

    fun setupCallbacks() {
        try {
            appListLoader.onAppListUpdated = { sortedList, filteredList, isFinal ->
                try {
                    val listWithSeparators = appListManager.addSeparators(sortedList, activity.showOnlyFavoritesInitially)
                    updateAppListUI(listWithSeparators, filteredList, isFinal)

                    activity.updateFastScrollerVisibility()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onAppListUpdated callback", e)
                }
            }
            appListLoader.onAdapterNeedsUpdate = { isGrid ->
                try {
                    this.isGridMode = isGrid
                    val columns = activity.getPreferredGridColumns()

                    val layoutManager = if (isGrid) {
                        val gridLayoutManager = GridLayoutManager(activity, columns)
                        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                            override fun getSpanSize(position: Int): Int {
                                try {
                                    val viewType = adapter?.getItemViewType(position)
                                    return if (viewType == AppAdapter.VIEW_TYPE_SEPARATOR ||
                                               viewType == AppAdapter.VIEW_TYPE_SEPARATOR_SMALL) {
                                        columns
                                    } else {
                                        1
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error getting span size: ${e.message}")
                                    return 1
                                }
                            }
                        }
                        gridLayoutManager
                    } else {
                        LinearLayoutManager(activity)
                    }

                    recyclerView.layoutManager = layoutManager

                    if (adapter != null) {
                        adapter?.updateViewMode(isGrid)
                    } else {
                        val newAdapter = AppAdapter(activity, appList, searchBox, isGrid, activity, activity.sharedPreferences)
                        adapter = newAdapter
                        activity.adapter = newAdapter
                        recyclerView.adapter = newAdapter
                    }
                    activity.updateFastScrollerVisibility()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onAdapterNeedsUpdate callback", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up callbacks", e)
        }
    }

    fun updateAppListUI(
        newAppList: List<ResolveInfo>,
        newFullAppList: List<ResolveInfo>,
        isFinal: Boolean = false
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        try {
            if (shouldKeepCurrentWorkProfileList(newAppList, newFullAppList)) {
                preservedWorkProfileListOnce = true
                appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapter)
                return
            }

            if (newAppList.isNotEmpty() || !appListManager.isWorkProfileModeEnabled()) {
                preservedWorkProfileListOnce = false
            }

            val deduplicatedFullAppList = newFullAppList.distinctBy { "${it.activityInfo.packageName}|${it.activityInfo.name}" }

            synchronized(fullAppList) {
                if (deduplicatedFullAppList !== fullAppList) {
                    fullAppList.clear()
                    fullAppList.addAll(deduplicatedFullAppList)
                }
            }

            synchronized(appList) {
                if (activity.appList !== appList) {
                    appList.clear()
                    appList.addAll(newAppList)
                } else {
                    activity.appList.clear()
                    activity.appList.addAll(newAppList)
                }
            }

            adapter?.updateAppList(newAppList)

            if (activity.pendingScrollToTop) {
                activity.pendingScrollToTop = false
                recyclerView.post {
                    // Position 3 skips the top spacers if they exist (added when favorites are present)
                    // If no spacers, 0 is the top. scrollToPositionWithOffset ensures it's at the top.
                    val hasSpacers = newAppList.any { 
                        it.activityInfo.packageName == AppAdapter.SEPARATOR_PACKAGE && 
                        it.activityInfo.name?.startsWith("all_apps_top_spacer_") == true 
                    }
                    val targetPos = if (hasSpacers) 3 else 0
                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(targetPos, 0)
                        ?: recyclerView.scrollToPosition(targetPos)
                }
            }

            val isEmpty = newAppList.isEmpty()
            recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
            activity.views.appListEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            activity.updateFastScrollerVisibility()

            activity.updateAppSearchManager(newFullAppList, newAppList)
        } catch (e: NullPointerException) {
            Log.e(TAG, "Null reference in updateAppListUI - possible null activity or adapter", e)
            // Don't show toast to avoid spam during activity transitions
        } catch (e: ConcurrentModificationException) {
            Log.e(TAG, "Concurrent modification in updateAppListUI", e)
            // Don't show toast, let the system recover naturally
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in updateAppListUI", e)
        }
    }

    private fun shouldKeepCurrentWorkProfileList(
        newAppList: List<ResolveInfo>,
        newFullAppList: List<ResolveInfo>
    ): Boolean {
        try {
            if (preservedWorkProfileListOnce) return false
            if (!appListManager.isWorkProfileModeEnabled()) return false
            if (newAppList.isNotEmpty()) return false
            if (appList.isEmpty()) return false

            val currentHasVisibleWorkApps = appList.any { app ->
                try {
                    val packageName = app.activityInfo.packageName
                    packageName != "com.guruswarupa.launch.SEPARATOR" &&
                        !packageName.startsWith("launcher_") &&
                        appListManager.isWorkProfileApp(app)
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking work profile app: ${e.message}")
                    false
                }
            }
            if (!currentHasVisibleWorkApps) return false

            val incomingHasWorkApps = newFullAppList.any { app ->
                try {
                    appListManager.isWorkProfileApp(app)
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking incoming work profile app: ${e.message}")
                    false
                }
            }

            return !incomingHasWorkApps
        } catch (e: Exception) {
            Log.e(TAG, "Error in shouldKeepCurrentWorkProfileList", e)
            return false
        }
    }

    fun filterAppsWithoutReload() {
        if (fullAppList.isEmpty()) {
            Log.w(TAG, "Full app list is empty, triggering full reload")
            appListLoader.loadApps(forceRefresh = false, fullAppList, appList, adapter)
            return
        }

        try {
            backgroundExecutor.execute {
                try {
                    val focusMode = appListManager.getFocusMode()
                    val workspaceMode = appListManager.getWorkspaceMode()

                    val currentFullList = synchronized(fullAppList) {
                        ArrayList(fullAppList)
                    }
                    val filteredApps = appListManager.filterAndPrepareApps(currentFullList, focusMode, workspaceMode)
                    val sortedFinalList = appListManager.sortAppsAlphabetically(filteredApps, activity.showOnlyFavoritesInitially)
                    val listWithSeparators = appListManager.addSeparators(sortedFinalList, activity.showOnlyFavoritesInitially)

                    activity.runOnUiThread {
                        updateAppListUI(listWithSeparators, currentFullList, isFinal = true)
                    }
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Illegal state during filtering - activity may be finishing", e)
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Unable to filter apps: UI state changed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: NullPointerException) {
                    Log.e(TAG, "Null reference during filtering - possible data corruption", e)
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Filtering error: Data inconsistency detected", Toast.LENGTH_SHORT).show()
                        // Trigger full reload to recover from data corruption
                        appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapter)
                    }
                } catch (e: IndexOutOfBoundsException) {
                    Log.e(TAG, "Index out of bounds during filtering - list corruption detected", e)
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Filtering error: List corruption detected", Toast.LENGTH_SHORT).show()
                        // Trigger full reload to recover from list corruption
                        appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapter)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error during app filtering: ${e.javaClass.simpleName}", e)
                    activity.runOnUiThread {
                        val errorType = when (e) {
                            is IndexOutOfBoundsException -> "Index error"
                            is NullPointerException -> "Null reference"
                            is IllegalStateException -> "State error"
                            else -> "Unknown error"
                        }
                        Toast.makeText(activity, "Filtering error ($errorType): ${e.message?.take(50) ?: "Unknown"}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "Filtering task rejected by executor: ${e.message}")
            Toast.makeText(activity, "Unable to filter apps: Task queue full", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error executing filtering task", e)
            Toast.makeText(activity, "Unable to filter apps: Internal error", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshAppsForFocusMode() {
        appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapter)
    }

    fun refreshAppsForWorkspace() {
        appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapter)
    }
}
