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
import java.util.concurrent.Executor

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
    private var isGridMode: Boolean = false
    private var preservedWorkProfileListOnce = false

    fun setAdapter(adapter: AppAdapter) {
        this.adapter = adapter
    }

    fun setupCallbacks() {
        appListLoader.onAppListUpdated = { sortedList, filteredList, isFinal ->
            val listWithSeparators = appListManager.addSeparators(sortedList, activity.showOnlyFavoritesInitially)
            updateAppListUI(listWithSeparators, filteredList, isFinal)

            activity.updateFastScrollerVisibility()
        }
        appListLoader.onAdapterNeedsUpdate = { isGrid ->
            this.isGridMode = isGrid
            val columns = activity.getPreferredGridColumns()

            val layoutManager = if (isGrid) {
                val gridLayoutManager = GridLayoutManager(activity, columns)
                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val viewType = adapter?.getItemViewType(position)
                        return if (viewType == AppAdapter.VIEW_TYPE_SEPARATOR ||
                                   viewType == AppAdapter.VIEW_TYPE_SEPARATOR_SMALL) {
                            columns
                        } else {
                            1
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
        }
    }

    fun updateAppListUI(
        newAppList: List<ResolveInfo>,
        newFullAppList: List<ResolveInfo>,
        isFinal: Boolean = false
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        if (shouldKeepCurrentWorkProfileList(newAppList, newFullAppList)) {
            preservedWorkProfileListOnce = true
            appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapter)
            return
        }

        if (newAppList.isNotEmpty() || !appListManager.isWorkProfileModeEnabled()) {
            preservedWorkProfileListOnce = false
        }

        val deduplicatedFullAppList = newFullAppList.distinctBy { "${it.activityInfo.packageName}|${it.activityInfo.name}" }

        if (deduplicatedFullAppList !== fullAppList) {
            fullAppList.clear()
            fullAppList.addAll(deduplicatedFullAppList)
        }

        if (activity.appList !== appList) {
            appList.clear()
            appList.addAll(newAppList)
        } else {
            activity.appList.clear()
            activity.appList.addAll(newAppList)
        }

        adapter?.updateAppList(newAppList)
        val isEmpty = newAppList.isEmpty()
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        activity.views.appListEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        activity.updateFastScrollerVisibility()

        activity.updateAppSearchManager(newFullAppList, newAppList)
    }

    private fun shouldKeepCurrentWorkProfileList(
        newAppList: List<ResolveInfo>,
        newFullAppList: List<ResolveInfo>
    ): Boolean {
        if (preservedWorkProfileListOnce) return false
        if (!appListManager.isWorkProfileModeEnabled()) return false
        if (newAppList.isNotEmpty()) return false
        if (appList.isEmpty()) return false

        val currentHasVisibleWorkApps = appList.any { app ->
            val packageName = app.activityInfo.packageName
            packageName != "com.guruswarupa.launch.SEPARATOR" &&
                !packageName.startsWith("launcher_") &&
                appListManager.isWorkProfileApp(app)
        }
        if (!currentHasVisibleWorkApps) return false

        val incomingHasWorkApps = newFullAppList.any { app ->
            appListManager.isWorkProfileApp(app)
        }

        return !incomingHasWorkApps
    }

    fun filterAppsWithoutReload() {
        if (fullAppList.isEmpty()) {
            appListLoader.loadApps(forceRefresh = false, fullAppList, appList, adapter)
            return
        }

        try {
            backgroundExecutor.execute {
                try {
                    val focusMode = appListManager.getFocusMode()
                    val workspaceMode = appListManager.getWorkspaceMode()

                    val currentFullList = ArrayList(fullAppList)
                    val filteredApps = appListManager.filterAndPrepareApps(currentFullList, focusMode, workspaceMode)
                    val sortedFinalList = appListManager.sortAppsAlphabetically(filteredApps, activity.showOnlyFavoritesInitially)
                    val listWithSeparators = appListManager.addSeparators(sortedFinalList, activity.showOnlyFavoritesInitially)

                    activity.runOnUiThread {
                        updateAppListUI(listWithSeparators, currentFullList, isFinal = true)
                    }
                } catch (e: Exception) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Error filtering apps: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AppListUIUpdater", "Task rejected by executor", e)
        }
    }

    fun refreshAppsForFocusMode() {
        appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapter)
    }

    fun refreshAppsForWorkspace() {
        appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapter)
    }
}
