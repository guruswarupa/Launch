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

internal fun StockDrawerManager.attachReorderAndFolders(
    targetRecyclerView: RecyclerView,
    adapterOf: () -> AppAdapter?,
    orderKey: String,
    foldersKey: String,
    isEnabled: () -> Boolean,
    dropZones: List<StockDrawerManager.DropZone> = emptyList(),
    hiddenWhileDropZonesShown: View? = null
): ItemTouchHelper {
    var didMove = false
    var mergeTargetPosition: Int? = null
    var hoveredMergeView: View? = null
    var hoveredDropZone: StockDrawerManager.DropZone? = null

    fun clearHoverHighlight() {
        hoveredMergeView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
        hoveredMergeView = null
    }

    fun setDropZoneHover(zone: StockDrawerManager.DropZone?) {
        if (hoveredDropZone === zone) return
        hoveredDropZone?.view?.let {
            it.alpha = 0.65f
            it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }
        hoveredDropZone = zone
        zone?.view?.let {
            it.alpha = 1f
            it.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start()
        }
    }

    val callback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        0
    ) {
        override fun isLongPressDragEnabled(): Boolean = false

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            if (!isEnabled() || !LayoutMode.isStock(sharedPreferences)) return 0
            val adapter = adapterOf() ?: return 0
            val entry = adapter.currentList.getOrNull(viewHolder.bindingAdapterPosition) ?: return 0
            if (!isReorderable(entry)) return 0
            return super.getMovementFlags(recyclerView, viewHolder)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val adapter = adapterOf() ?: return false
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

            val draggedEntry = adapter.currentList.getOrNull(from)
            val targetEntry = adapter.currentList.getOrNull(to)
            if (draggedEntry == null || targetEntry == null) return false
            if (!isReorderable(draggedEntry) || !isReorderable(targetEntry)) return false

            val draggedIsFolder = appOrderManager.isFolderEntry(draggedEntry)
            val overlap = overlapFraction(viewHolder.itemView, target.itemView)

            if (!draggedIsFolder && overlap >= StockDrawerManager.MERGE_OVERLAP_THRESHOLD) {
                if (mergeTargetPosition != to) {
                    clearHoverHighlight()
                    mergeTargetPosition = to
                    hoveredMergeView = target.itemView
                    target.itemView.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start()
                }
                return false
            }

            if (mergeTargetPosition != null) clearHoverHighlight()
            mergeTargetPosition = null
            adapter.moveItem(from, to)
            didMove = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                didMove = false
                mergeTargetPosition = null
                clearHoverHighlight()
                if (dropZones.isNotEmpty()) {
                    hiddenWhileDropZonesShown?.isVisible = false
                    setDropZoneHover(null)
                    dropZones.forEach { zone ->
                        zone.view.animate().cancel()
                        zone.view.isVisible = true
                        zone.view.scaleX = 0.7f
                        zone.view.scaleY = 0.7f
                        zone.view.alpha = 0f
                        zone.view.animate().scaleX(1f).scaleY(1f).alpha(0.65f).setDuration(150).start()
                    }
                }
                viewHolder?.itemView?.apply {
                    alpha = 0.85f
                    scaleX = 1.08f
                    scaleY = 1.08f
                }
            }
        }

        override fun onChildDraw(
            c: android.graphics.Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            if (dropZones.isNotEmpty() && actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                val zoneUnderDrag = dropZones.firstOrNull { isDraggedOverZone(recyclerView, viewHolder, dX, dY, it.view) }
                setDropZoneHover(zoneUnderDrag)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.apply {
                alpha = 1f
                scaleX = 1f
                scaleY = 1f
            }
            clearHoverHighlight()
            val droppedOnZone = hoveredDropZone
            if (dropZones.isNotEmpty()) {
                hiddenWhileDropZonesShown?.isVisible = true
                dropZones.forEach { zone ->
                    zone.view.animate().cancel()
                    zone.view.isVisible = false
                    zone.view.scaleX = 1f
                    zone.view.scaleY = 1f
                }
                hoveredDropZone = null
            }

            val adapter = adapterOf() ?: return
            val mergeTo = mergeTargetPosition
            mergeTargetPosition = null
            val from = viewHolder.bindingAdapterPosition

            when {
                droppedOnZone != null && from != RecyclerView.NO_POSITION -> {
                    droppedOnZone.onDrop(adapter, from)
                }
                mergeTo != null && from != RecyclerView.NO_POSITION -> {
                    performMerge(adapter, orderKey, foldersKey, from, mergeTo)
                }
                didMove -> {
                    appOrderManager.saveOrder(adapter.currentList, orderKey)
                }
                else -> (viewHolder as? AppAdapter.ViewHolder)?.let { adapter.showContextMenuForHolder(it) }
            }
        }
    }

    val helper = ItemTouchHelper(callback)
    helper.attachToRecyclerView(targetRecyclerView)
    return helper
}

internal fun StockDrawerManager.addDraggedEntryToHome(adapter: AppAdapter, position: Int) {
    val entry = adapter.currentList.getOrNull(position) ?: return
    val homeFoldersKey = com.guruswarupa.launch.models.Constants.Prefs.STOCK_HOME_FOLDERS

    if (appOrderManager.isFolderEntry(entry)) {
        val folderId = appOrderManager.folderIdOf(entry)
        val folder = folderManager.findFolder(com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_FOLDERS, folderId) ?: return
        folder.appKeys.forEach { key ->
            activity.fullAppList.find { appOrderManager.keyOf(it) == key }?.let {
                favoriteAppManager.addFavoriteApp(it.activityInfo.packageName)
            }
        }
        val alreadyOnHome = folderManager.getFolders(homeFoldersKey).any { it.appKeys.toSet() == folder.appKeys.toSet() }
        if (!alreadyOnHome) {
            folderManager.createFolder(homeFoldersKey, folder.name, folder.appKeys.toList())

            appOrderManager.removeTokens(
                com.guruswarupa.launch.models.Constants.Prefs.STOCK_HOME_APP_ORDER,
                folder.appKeys.toSet()
            )
        }
        android.widget.Toast.makeText(
            activity, activity.getString(R.string.stock_folder_added_to_home, folder.name), android.widget.Toast.LENGTH_SHORT
        ).show()
    } else {
        favoriteAppManager.addFavoriteApp(entry.activityInfo.packageName)
        val label = appListManager.getDisplayLabel(entry)
        android.widget.Toast.makeText(
            activity, activity.getString(R.string.stock_app_added_to_home, label), android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    activity.appListLoader.loadApps(forceRefresh = false)
    hide(animated = true)
}

internal fun StockDrawerManager.promoteToHotseat(gridAdapter: AppAdapter, gridPosition: Int) {
    val draggedEntry = gridAdapter.currentList.getOrNull(gridPosition) ?: return
    val dockCapacity = appListManager.getStockHotseatCount()
    val currentDock = hotseatAdapter?.currentList.orEmpty().toMutableList()

    currentDock.add(draggedEntry)
    if (currentDock.size > dockCapacity) {
        currentDock.removeAt(0)
    }

    appOrderManager.saveOrder(currentDock, com.guruswarupa.launch.models.Constants.Prefs.STOCK_DOCK_ORDER)
    activity.appListLoader.loadApps(forceRefresh = false)
}

internal fun StockDrawerManager.removeDraggedEntryFromHome(adapter: AppAdapter, position: Int, foldersKey: String) {
    val entry = adapter.currentList.getOrNull(position) ?: return

    if (appOrderManager.isFolderEntry(entry)) {
        val folderId = appOrderManager.folderIdOf(entry)
        val folder = folderManager.findFolder(foldersKey, folderId)
        folder?.appKeys?.forEach { key ->
            activity.fullAppList.find { appOrderManager.keyOf(it) == key }?.let {
                favoriteAppManager.removeFavoriteApp(it.activityInfo.packageName)
            }
        }
        if (folder != null) folderManager.deleteFolder(foldersKey, folderId)
        android.widget.Toast.makeText(
            activity, activity.getString(R.string.stock_folder_removed_from_home, folder?.name.orEmpty()), android.widget.Toast.LENGTH_SHORT
        ).show()
    } else {
        val label = appListManager.getDisplayLabel(entry)
        favoriteAppManager.removeFavoriteApp(entry.activityInfo.packageName)
        android.widget.Toast.makeText(
            activity, activity.getString(R.string.stock_app_removed_from_home, label), android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    activity.appListLoader.loadApps(forceRefresh = false)
}

internal fun StockDrawerManager.attachHomeReorder(homeRecyclerView: RecyclerView, homeAdapter: AppAdapter, hotseatRecyclerView: RecyclerView, removeZone: View) {
    homeGridAdapter = homeAdapter
    val orderKey = com.guruswarupa.launch.models.Constants.Prefs.STOCK_HOME_APP_ORDER
    val foldersKey = com.guruswarupa.launch.models.Constants.Prefs.STOCK_HOME_FOLDERS

    val dockOrderKey = com.guruswarupa.launch.models.Constants.Prefs.STOCK_DOCK_ORDER

    hotseatView = hotseatRecyclerView

    val dockCapacity = appListManager.getStockHotseatCount()

    val gridLayoutManager = object : GridLayoutManager(activity, dockCapacity) {
        override fun canScrollVertically(): Boolean = false
    }
    hotseatLayoutManager = gridLayoutManager
    hotseatRecyclerView.layoutManager = gridLayoutManager

    hotseatRecyclerView.itemAnimator = null
    val newHotseatAdapter = AppAdapter(activity, mutableListOf(), searchBox, true, activity, sharedPreferences)

    newHotseatAdapter.updateShowAppNamesInGrid(false)
    hotseatAdapter = newHotseatAdapter
    hotseatRecyclerView.adapter = newHotseatAdapter

    val hotseatHelper = attachReorderAndFolders(
        targetRecyclerView = hotseatRecyclerView,
        adapterOf = { hotseatAdapter },
        orderKey = dockOrderKey,
        foldersKey = foldersKey,
        isEnabled = { LayoutMode.isStock(sharedPreferences) },
        dropZones = listOf(StockDrawerManager.DropZone(removeZone) { adapter, position -> removeDraggedEntryFromHome(adapter, position, foldersKey) })
    )
    newHotseatAdapter.onItemLongPress = { holder ->
        if (LayoutMode.isStock(sharedPreferences)) {
            hotseatHelper.startDrag(holder)
            true
        } else {
            false
        }
    }
    newHotseatAdapter.onFolderClick = { folderId ->
        showFolderContents(
            orderKey = dockOrderKey,
            foldersKey = foldersKey,
            folderId = folderId,
            refresh = { activity.appListLoader.loadApps(forceRefresh = false) }
        )
    }
    newHotseatAdapter.folderAppsResolver = { folderId -> resolveFolderApps(foldersKey, folderId) }

    val helper = attachReorderAndFolders(
        targetRecyclerView = homeRecyclerView,
        adapterOf = { homeAdapter },
        orderKey = orderKey,
        foldersKey = foldersKey,
        isEnabled = { LayoutMode.isStock(sharedPreferences) },
        dropZones = listOf(
            StockDrawerManager.DropZone(removeZone) { adapter, position -> removeDraggedEntryFromHome(adapter, position, foldersKey) },
            StockDrawerManager.DropZone(hotseatRecyclerView) { adapter, position -> promoteToHotseat(adapter, position) }
        )
    )

    homeAdapter.onItemLongPress = { holder ->
        if (LayoutMode.isStock(sharedPreferences)) {
            helper.startDrag(holder)
            true
        } else {
            false
        }
    }

    homeAdapter.onFolderClick = { folderId ->
        showFolderContents(
            orderKey = orderKey,
            foldersKey = foldersKey,
            folderId = folderId,
            refresh = { activity.appListLoader.loadApps(forceRefresh = false) }
        )
    }
    homeAdapter.folderAppsResolver = { folderId -> resolveFolderApps(foldersKey, folderId) }
}
