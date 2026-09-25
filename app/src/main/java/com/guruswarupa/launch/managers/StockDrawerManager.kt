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

class StockDrawerManager(
    private val activity: MainActivity,
    private val drawerRoot: View,
    private val recyclerView: RecyclerView,
    private val searchBox: AutoCompleteTextView,
    private val emptyState: View,
    private val searchContainer: View,
    private val addToHomeZone: View,
    private val appListManager: AppListManager,
    private val appDockManager: AppDockManager,
    private val appOrderManager: AppOrderManager,
    private val folderManager: FolderManager,
    private val favoriteAppManager: FavoriteAppManager,
    private val sharedPreferences: SharedPreferences,
    private val screenPagerManager: ScreenPagerManager
) {
    companion object {
        private const val MERGE_OVERLAP_THRESHOLD = 0.55f
    }

    private var adapter: AppAdapter? = null
    private var allOrderedApps: List<ResolveInfo> = emptyList()
    private var isOpen = false
    private var openCloseAnimator: Animator? = null
    private var drawerLayoutManager: GridLayoutManager? = null
    private var currentColumns = 0
    private var hotseatAdapter: AppAdapter? = null
    private var hotseatView: RecyclerView? = null
    private var hotseatLayoutManager: GridLayoutManager? = null

    fun setup() {

        val surfaceColor = com.guruswarupa.launch.ui.theme.ThemeManager.color(activity, R.attr.appSurface)
        drawerRoot.setBackgroundColor(
            android.graphics.Color.argb(
                255,
                android.graphics.Color.red(surfaceColor),
                android.graphics.Color.green(surfaceColor),
                android.graphics.Color.blue(surfaceColor)
            )
        )

        currentColumns = activity.getPreferredGridColumns()
        val gridLayoutManager = GridLayoutManager(activity, currentColumns)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val viewType = adapter?.getItemViewType(position)
                return if (viewType == AppAdapter.VIEW_TYPE_SEPARATOR || viewType == AppAdapter.VIEW_TYPE_SEPARATOR_SMALL) {
                    currentColumns
                } else {
                    1
                }
            }
        }
        drawerLayoutManager = gridLayoutManager
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null

        val newAdapter = AppAdapter(
            activity,
            mutableListOf(),
            searchBox,
            true,
            activity,
            sharedPreferences
        )
        adapter = newAdapter
        recyclerView.adapter = newAdapter

        val helper = attachReorderAndFolders(
            targetRecyclerView = recyclerView,
            adapterOf = { adapter },
            orderKey = com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_APP_ORDER,
            foldersKey = com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_FOLDERS,
            isEnabled = { searchBox.text.isNullOrEmpty() },
            dropZones = listOf(DropZone(addToHomeZone) { adapter, position -> addDraggedEntryToHome(adapter, position) }),
            hiddenWhileDropZonesShown = searchContainer
        )
        newAdapter.onItemLongPress = { holder ->
            if (searchBox.text.isNullOrEmpty()) {
                helper.startDrag(holder)
                true
            } else {
                false
            }
        }
        newAdapter.onFolderClick = { folderId ->
            showFolderContents(
                orderKey = com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_APP_ORDER,
                foldersKey = com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_FOLDERS,
                folderId = folderId,
                refresh = {
                    recomputeOrderedApps(activity.fullAppList)
                    applyCurrentFilter()
                }
            )
        }
        newAdapter.folderAppsResolver = { folderId ->
            resolveFolderApps(com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_FOLDERS, folderId)
        }

        setupSearch()
        setupCloseGesture()
        setupModeToggles()

        drawerRoot.setOnClickListener { }
    }

    fun onFullAppListUpdated(fullList: List<ResolveInfo>) {
        if (!LayoutMode.isStock(sharedPreferences)) return
        recomputeOrderedApps(fullList)
        applyCurrentFilter()
        recomputeHotseat(fullList)

        if (isOpen) {
            activity.appSearchManager.updateData(
                activity.fullAppList, allOrderedApps, activity.contactManager.getContactsList()
            )
        }
    }

    private fun recomputeHotseat(fullList: List<ResolveInfo>) {
        val currentHotseatAdapter = hotseatAdapter ?: return
        val focusMode = appDockManager.getCurrentMode()
        val workspaceMode = appDockManager.isWorkspaceModeActive()
        val filtered = appListManager.filterAndPrepareApps(fullList, focusMode, workspaceMode)
        val dockApps = appListManager.computeStockDock(filtered)
        currentHotseatAdapter.updateAppList(dockApps)
        hotseatView?.isVisible = dockApps.isNotEmpty()
    }

    private fun recomputeOrderedApps(fullList: List<ResolveInfo>) {
        val focusMode = appDockManager.getCurrentMode()

        val filtered = appListManager.filterAndPrepareApps(fullList, focusMode, workspaceMode = false)
        val folders = folderManager.getFolders(com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_FOLDERS)
        val ordered = appOrderManager.applyOrder(
            filtered, appListManager, com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_APP_ORDER, folders
        )

        allOrderedApps = ordered +
            appListManager.createSeparatorInfo("SMALL") +
            appListManager.createLauncherShortcut("launcher_settings_shortcut") +
            appListManager.createLauncherShortcut("launcher_vault_shortcut")
    }

    private fun applyCurrentFilter() {
        val query = searchBox.text?.toString()?.trim().orEmpty()
        val visible = if (query.isEmpty()) {
            allOrderedApps
        } else {
            allOrderedApps.filter { entry ->
                if (entry.activityInfo.packageName == AppAdapter.SEPARATOR_PACKAGE) return@filter false
                val label = if (appOrderManager.isFolderEntry(entry)) {
                    entry.activityInfo.name ?: ""
                } else {
                    appListManager.getDisplayLabel(entry)
                }
                label.contains(query, ignoreCase = true)
            }
        }
        adapter?.updateAppList(visible)
        emptyState.isVisible = visible.isEmpty()
        recyclerView.isVisible = visible.isNotEmpty()
    }

    private fun setupSearch() {
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    applyCurrentFilter()
                } else {
                    recyclerView.isVisible = true
                    emptyState.isVisible = false
                }
            }
        })
    }

    private fun retargetSearchToDrawer() {
        activity.appSearchManager.onSearchQueryChanged = { query ->
            if (query.isNotEmpty()) ensureContactsLoadedForDrawerSearch()
        }
        activity.appSearchManager.configure(
            fullAppList = activity.fullAppList.toMutableList(),
            homeAppList = allOrderedApps,
            adapter = adapter,
            searchBox = searchBox,
            contactsList = activity.contactManager.getContactsList(),
            appMetadataCache = activity.cacheManager.getMetadataCache(),
            isAppFiltered = { packageName -> appDockManager.isAppHiddenInFocusMode(packageName) },
            isFocusModeActive = { appDockManager.getCurrentMode() }
        )
    }

    private fun ensureContactsLoadedForDrawerSearch() {
        if (activity.contactManager.hasLoadedContacts()) return
        activity.contactManager.loadContacts { _ ->
            if (activity.isFinishing || activity.isDestroyed || !isOpen) return@loadContacts
            retargetSearchToDrawer()
        }
    }

    private fun setupModeToggles() {
        val focusToggle = drawerRoot.findViewById<View>(R.id.stock_drawer_focus_toggle) ?: return
        val focusIcon = drawerRoot.findViewById<android.widget.ImageView>(R.id.stock_drawer_focus_icon)
        val focusText = drawerRoot.findViewById<TextView>(R.id.stock_drawer_focus_text)
        val workToggle = drawerRoot.findViewById<View>(R.id.stock_drawer_work_toggle) ?: return
        val workIcon = drawerRoot.findViewById<android.widget.ImageView>(R.id.stock_drawer_work_icon)
        val workText = drawerRoot.findViewById<TextView>(R.id.stock_drawer_work_text)

        focusToggle.setOnClickListener {
            appDockManager.toggleFocusMode()
            refreshModeToggles()
        }
        focusToggle.setOnLongClickListener {
            if (!appDockManager.getCurrentMode()) {
                appDockManager.showFocusModeSettings()
            } else {
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(R.string.toast_focus_mode_settings_unavailable_during_focus_mod),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            true
        }
        workToggle.setOnClickListener {
            appDockManager.toggleWorkProfile()
            refreshModeToggles()
        }
        refreshModeToggles()
    }

    private fun refreshModeToggles() {
        val focusToggle = drawerRoot.findViewById<View>(R.id.stock_drawer_focus_toggle) ?: return
        val focusIcon = drawerRoot.findViewById<android.widget.ImageView>(R.id.stock_drawer_focus_icon)
        val focusText = drawerRoot.findViewById<TextView>(R.id.stock_drawer_focus_text)
        val workToggle = drawerRoot.findViewById<View>(R.id.stock_drawer_work_toggle) ?: return
        val workIcon = drawerRoot.findViewById<android.widget.ImageView>(R.id.stock_drawer_work_icon)
        val workText = drawerRoot.findViewById<TextView>(R.id.stock_drawer_work_text)

        val accentColor = com.guruswarupa.launch.ui.theme.ThemeManager.color(activity, R.attr.appAccent)
        val normalColor = com.guruswarupa.launch.ui.theme.ThemeManager.color(activity, R.attr.appTextPrimary)

        val focusActive = appDockManager.getCurrentMode()
        focusIcon?.setImageResource(if (focusActive) R.drawable.ic_focus_mode else R.drawable.ic_normal_mode)
        val focusColor = if (focusActive) accentColor else normalColor
        focusIcon?.imageTintList = android.content.res.ColorStateList.valueOf(focusColor)
        focusText?.setTextColor(focusColor)

        val workActive = appDockManager.isWorkProfileModeEnabled()
        workIcon?.setImageResource(if (workActive) R.drawable.ic_work_profile_active else R.drawable.ic_work_inactive)
        val workColor = if (workActive) accentColor else normalColor
        workIcon?.imageTintList = android.content.res.ColorStateList.valueOf(workColor)
        workText?.setTextColor(workColor)

        val hideFocus = sharedPreferences.getBoolean(com.guruswarupa.launch.models.Constants.Prefs.DOCK_HIDE_FOCUS_MODE, false)
        val hideWork = sharedPreferences.getBoolean(com.guruswarupa.launch.models.Constants.Prefs.DOCK_HIDE_WORK_PROFILE, false)
        focusToggle.isVisible = !hideFocus
        workToggle.isVisible = !hideWork
    }

    private fun isReorderable(entry: ResolveInfo): Boolean {
        val packageName = entry.activityInfo.packageName
        return packageName != AppAdapter.SEPARATOR_PACKAGE && !packageName.startsWith("launcher_")
    }

    private fun overlapFraction(dragged: View, target: View): Float {
        val dLeft = dragged.left + dragged.translationX
        val dTop = dragged.top + dragged.translationY
        val dRight = dLeft + dragged.width
        val dBottom = dTop + dragged.height

        val tLeft = target.left.toFloat()
        val tTop = target.top.toFloat()
        val tRight = tLeft + target.width
        val tBottom = tTop + target.height

        val overlapLeft = maxOf(dLeft, tLeft)
        val overlapTop = maxOf(dTop, tTop)
        val overlapRight = minOf(dRight, tRight)
        val overlapBottom = minOf(dBottom, tBottom)

        if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) return 0f

        val overlapArea = (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
        val targetArea = target.width * target.height
        return if (targetArea > 0) overlapArea / targetArea else 0f
    }

    private fun isDraggedOverZone(hostRecyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, zone: View): Boolean {
        if (!zone.isVisible) return false

        val rvLocation = IntArray(2)
        hostRecyclerView.getLocationOnScreen(rvLocation)
        val itemCenterX = rvLocation[0] + viewHolder.itemView.left + dX + viewHolder.itemView.width / 2f
        val itemCenterY = rvLocation[1] + viewHolder.itemView.top + dY + viewHolder.itemView.height / 2f

        val zoneLocation = IntArray(2)
        zone.getLocationOnScreen(zoneLocation)
        val zoneLeft = zoneLocation[0].toFloat()
        val zoneTop = zoneLocation[1].toFloat()

        return itemCenterX in zoneLeft..(zoneLeft + zone.width) && itemCenterY in zoneTop..(zoneTop + zone.height)
    }

    private class DropZone(val view: View, val onDrop: (AppAdapter, Int) -> Unit)

    private fun attachReorderAndFolders(
        targetRecyclerView: RecyclerView,
        adapterOf: () -> AppAdapter?,
        orderKey: String,
        foldersKey: String,
        isEnabled: () -> Boolean,
        dropZones: List<DropZone> = emptyList(),
        hiddenWhileDropZonesShown: View? = null
    ): ItemTouchHelper {
        var didMove = false
        var mergeTargetPosition: Int? = null
        var hoveredMergeView: View? = null
        var hoveredDropZone: DropZone? = null

        fun clearHoverHighlight() {
            hoveredMergeView?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
            hoveredMergeView = null
        }

        fun setDropZoneHover(zone: DropZone?) {
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

                if (!draggedIsFolder && overlap >= MERGE_OVERLAP_THRESHOLD) {
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

    private fun addDraggedEntryToHome(adapter: AppAdapter, position: Int) {
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

    private fun promoteToHotseat(gridAdapter: AppAdapter, gridPosition: Int) {
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

    private fun removeDraggedEntryFromHome(adapter: AppAdapter, position: Int, foldersKey: String) {
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

    private fun performMerge(
        adapter: AppAdapter,
        orderKey: String,
        foldersKey: String,
        draggedPosition: Int,
        targetPosition: Int
    ) {
        val list = adapter.currentList
        val draggedApp = list.getOrNull(draggedPosition) ?: return
        val targetApp = list.getOrNull(targetPosition) ?: return
        val draggedKey = appOrderManager.keyOf(draggedApp)

        if (appOrderManager.isFolderEntry(targetApp)) {
            val folderId = appOrderManager.folderIdOf(targetApp)
            folderManager.addAppToFolder(foldersKey, folderId, draggedKey)
            val refreshedFolder = folderManager.findFolder(foldersKey, folderId)
            val updated = list.toMutableList()
            updated.removeAt(draggedPosition)
            if (refreshedFolder != null) {

                val newTargetIndex = (if (draggedPosition < targetPosition) targetPosition - 1 else targetPosition)
                    .coerceIn(updated.indices)
                updated[newTargetIndex] = appOrderManager.createFolderInfo(refreshedFolder)
            }
            adapter.updateAppList(updated)
            appOrderManager.saveOrder(updated, orderKey)
        } else {
            val targetKey = appOrderManager.keyOf(targetApp)
            val defaultName = activity.getString(R.string.stock_folder_default_name)
            val folder = folderManager.createFolder(foldersKey, defaultName, listOf(targetKey, draggedKey))
            val folderInfo = appOrderManager.createFolderInfo(folder)
            val updated = list.toMutableList()
            updated.removeAt(draggedPosition)
            val adjustedTargetIndex = if (draggedPosition < targetPosition) targetPosition - 1 else targetPosition
            if (adjustedTargetIndex in updated.indices) {
                updated[adjustedTargetIndex] = folderInfo
            } else {
                updated.add(folderInfo)
            }
            adapter.updateAppList(updated)
            appOrderManager.saveOrder(updated, orderKey)

            showFolderContents(
                orderKey = orderKey,
                foldersKey = foldersKey,
                folderId = folder.id,
                refresh = { onOrderChangedForFolder(orderKey, foldersKey) },
                focusNameForEditing = true
            )
        }
    }

    private fun onOrderChangedForFolder(orderKey: String, foldersKey: String) {
        if (orderKey == com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_APP_ORDER) {
            recomputeOrderedApps(activity.fullAppList)
            applyCurrentFilter()
        } else {
            activity.appListLoader.loadApps(forceRefresh = false)
        }
    }

    private fun showFolderContents(
        orderKey: String,
        foldersKey: String,
        folderId: String,
        refresh: () -> Unit,
        focusNameForEditing: Boolean = false
    ) {
        val folder = folderManager.findFolder(foldersKey, folderId) ?: return
        val appsInFolder = resolveFolderApps(foldersKey, folderId)
        if (appsInFolder.isEmpty()) return

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_stock_folder_contents, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.folder_name_input)
        val contentsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.folder_contents_recycler_view)
        val deleteButton = dialogView.findViewById<View>(R.id.folder_delete_button)

        DialogStyler.styleInput(activity, nameInput)
        nameInput.background = null
        nameInput.setText(folder.name)
        nameInput.hint = activity.getString(R.string.stock_folder_name_hint)
        var lastSavedName = folder.name

        fun commitNameIfChanged() {
            val newName = nameInput.text.toString().trim()
                .ifEmpty { activity.getString(R.string.stock_folder_default_name) }
            if (newName != lastSavedName) {
                folderManager.renameFolder(foldersKey, folder.id, newName)
                lastSavedName = newName
                refresh()
            }
        }

        nameInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commitNameIfChanged() }
        nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                commitNameIfChanged()
                hideKeyboard(nameInput)
                nameInput.clearFocus()
                true
            } else {
                false
            }
        }

        contentsRecyclerView.layoutManager = GridLayoutManager(activity, activity.getPreferredGridColumns().coerceAtMost(4))
        val contentsAdapter = AppAdapter(activity, appsInFolder.toMutableList(), searchBox, true, activity, sharedPreferences)
        contentsRecyclerView.adapter = contentsAdapter

        lateinit var dialog: AlertDialog

        val removeZone = dialogView.findViewById<View>(R.id.folder_remove_zone)
        val addToHomeZone = dialogView.findViewById<View>(R.id.folder_add_to_home_zone)

        val showAddToHome = foldersKey == com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_FOLDERS

        val dragHelper = attachFolderContentsDrag(
            contentsRecyclerView = contentsRecyclerView,
            contentsAdapter = contentsAdapter,
            foldersKey = foldersKey,
            folderId = folder.id,
            removeZone = removeZone,
            addToHomeZone = if (showAddToHome) addToHomeZone else null
        ) { folderStillExists ->
            if (!folderStillExists) {
                dialog.dismiss()
            }
            refresh()
        }
        contentsAdapter.onItemLongPress = { holder ->
            dragHelper.startDrag(holder)
            true
        }

        deleteButton.setOnClickListener {
            AlertDialog.Builder(activity, R.style.CustomDialogTheme)
                .setTitle(activity.getString(R.string.stock_folder_delete_confirm_title))
                .setMessage(activity.getString(R.string.stock_folder_delete_confirm_message, lastSavedName))
                .setPositiveButton(activity.getString(R.string.delete_button)) { _, _ ->
                    folderManager.deleteFolder(foldersKey, folder.id)
                    dialog.dismiss()
                    refresh()
                }
                .setNegativeButton(activity.getString(R.string.cancel_button), null)
                .show()
        }

        dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()
        dialog.setOnDismissListener { commitNameIfChanged() }
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (focusNameForEditing) {
            nameInput.requestFocus()
            nameInput.selectAll()
            showKeyboard(nameInput)
        }
    }

    private fun showKeyboard(view: View) {
        view.post {
            val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun attachFolderContentsDrag(
        contentsRecyclerView: RecyclerView,
        contentsAdapter: AppAdapter,
        foldersKey: String,
        folderId: String,
        removeZone: View,
        addToHomeZone: View?,
        onChanged: (folderStillExists: Boolean) -> Unit
    ): ItemTouchHelper {
        val zones = listOfNotNull(removeZone, addToHomeZone)
        var hoveredZone: View? = null

        fun setZoneHover(zone: View?) {
            if (hoveredZone === zone) return
            hoveredZone?.let {
                it.alpha = 0.65f
                it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            hoveredZone = zone
            zone?.let {
                it.alpha = 1f
                it.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start()
            }
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    setZoneHover(null)
                    zones.forEach { zone ->
                        zone.animate().cancel()
                        zone.isVisible = true
                        zone.scaleX = 0.7f
                        zone.scaleY = 0.7f
                        zone.alpha = 0f
                        zone.animate().scaleX(1f).scaleY(1f).alpha(0.65f).setDuration(150).start()
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
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                    val zoneUnderDrag = zones.firstOrNull { isDraggedOverZone(recyclerView, viewHolder, dX, dY, it) }
                    setZoneHover(zoneUnderDrag)
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.apply {
                    alpha = 1f
                    scaleX = 1f
                    scaleY = 1f
                }
                val droppedOnZone = hoveredZone
                setZoneHover(null)
                zones.forEach { zone ->
                    zone.animate().cancel()
                    zone.isVisible = false
                    zone.scaleX = 1f
                    zone.scaleY = 1f
                }

                val position = viewHolder.bindingAdapterPosition
                val app = if (position != RecyclerView.NO_POSITION) contentsAdapter.currentList.getOrNull(position) else null
                if (droppedOnZone == null || app == null) {
                    if (droppedOnZone == null && position != RecyclerView.NO_POSITION) {
                        (viewHolder as? AppAdapter.ViewHolder)?.let { contentsAdapter.showContextMenuForHolder(it) }
                    }
                    return
                }

                val key = appOrderManager.keyOf(app)
                folderManager.removeAppFromFolder(foldersKey, folderId, key)
                if (droppedOnZone === addToHomeZone) {
                    favoriteAppManager.addFavoriteApp(app.activityInfo.packageName)
                }

                val updated = contentsAdapter.currentList.toMutableList()
                updated.removeAt(position)
                contentsAdapter.updateAppList(updated)

                val folderStillExists = folderManager.findFolder(foldersKey, folderId) != null
                onChanged(folderStillExists)
            }
        }

        val helper = ItemTouchHelper(callback)
        helper.attachToRecyclerView(contentsRecyclerView)
        return helper
    }

    private fun setupCloseGesture() {
        val density = activity.resources.displayMetrics.density
        val closeThresholdPx = (24 * density).toInt()
        var startY = 0f
        var tracking = false

        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startY = e.y
                        tracking = !rv.canScrollVertically(-1)
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (tracking) {
                            val dy = e.y - startY
                            if (dy > closeThresholdPx) {
                                tracking = false
                                hide()
                                return true
                            }
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}
        })
    }

    fun attachOpenGesture(homeRecyclerView: RecyclerView) {
        val density = activity.resources.displayMetrics.density
        val openThresholdPx = (24 * density).toInt()
        var startX = 0f
        var startY = 0f
        var tracking = false

        homeRecyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                if (!LayoutMode.isStock(sharedPreferences) || isOpen || !isDrawerEnabled()) return false
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        tracking = !rv.canScrollVertically(1)
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (tracking) {
                            val dy = startY - e.y
                            val dx = e.x - startX
                            if (dy > openThresholdPx && dy > kotlin.math.abs(dx) * 1.2f) {
                                tracking = false
                                show()
                                return true
                            } else if (kotlin.math.abs(dx) > dy * 1.2f) {
                                tracking = false
                            }
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}
        })
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun attachOpenGestureToView(view: View) {
        val density = activity.resources.displayMetrics.density
        val openThresholdPx = (24 * density).toInt()
        var startX = 0f
        var startY = 0f
        var tracking = false
        val doubleTapToLockDetector = screenPagerManager.createDoubleTapToLockDetector()

        view.setOnTouchListener { _, event ->
            if (!LayoutMode.isStock(sharedPreferences) || isOpen || !isDrawerEnabled()) return@setOnTouchListener false
            doubleTapToLockDetector.onTouchEvent(event)
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    tracking = true
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (tracking) {
                        val dy = startY - event.y
                        val dx = event.x - startX
                        if (dy > openThresholdPx && dy > kotlin.math.abs(dx) * 1.2f) {
                            tracking = false
                            show()
                        }
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    tracking = false
                    true
                }
                else -> false
            }
        }
    }

    fun attachHomeReorder(homeRecyclerView: RecyclerView, homeAdapter: AppAdapter, hotseatRecyclerView: RecyclerView, removeZone: View) {
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
            dropZones = listOf(DropZone(removeZone) { adapter, position -> removeDraggedEntryFromHome(adapter, position, foldersKey) })
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
                DropZone(removeZone) { adapter, position -> removeDraggedEntryFromHome(adapter, position, foldersKey) },
                DropZone(hotseatRecyclerView) { adapter, position -> promoteToHotseat(adapter, position) }
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

    private fun resolveFolderApps(foldersKey: String, folderId: String): List<ResolveInfo> {
        val folder = folderManager.findFolder(foldersKey, folderId) ?: return emptyList()
        return folder.appKeys.mapNotNull { key ->
            activity.fullAppList.find { appOrderManager.keyOf(it) == key }
        }.filter { !appDockManager.isAppHiddenInFocusMode(it.activityInfo.packageName) }
    }

    fun isDrawerEnabled(): Boolean =
        sharedPreferences.getBoolean(com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_ENABLED, true)

    fun hideHotseat() {
        hotseatView?.isVisible = false
    }

    fun show() {
        if (isOpen || !LayoutMode.isStock(sharedPreferences) || !isDrawerEnabled()) return

        recomputeOrderedApps(activity.fullAppList)
        applyCurrentFilter()
        refreshModeToggles()
        retargetSearchToDrawer()

        recyclerView.scrollToPosition(0)

        isOpen = true
        screenPagerManager.setPagingEnabled(false)
        openCloseAnimator?.cancel()
        drawerRoot.isVisible = true
        val startTranslation = drawerRoot.height.takeIf { it > 0 }?.toFloat()
            ?: activity.resources.displayMetrics.heightPixels.toFloat()
        drawerRoot.translationY = startTranslation
        drawerRoot.alpha = 0f

        val translate = ObjectAnimator.ofFloat(drawerRoot, View.TRANSLATION_Y, startTranslation, 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator(1.5f)
        }
        val fadeIn = ObjectAnimator.ofFloat(drawerRoot, View.ALPHA, 0f, 1f).apply {
            duration = 150
            interpolator = LinearInterpolator()
        }
        openCloseAnimator = AnimatorSet().apply {
            playTogether(translate, fadeIn)
            start()
        }
    }

    fun hide(animated: Boolean = true) {
        if (!isOpen) return
        isOpen = false

        activity.updateAppSearchManager()
        searchBox.text?.clear()
        screenPagerManager.setPagingEnabled(true)
        openCloseAnimator?.cancel()

        if (animated) {
            val endTranslation = drawerRoot.height.takeIf { it > 0 }?.toFloat()
                ?: activity.resources.displayMetrics.heightPixels.toFloat()
            val translate = ObjectAnimator.ofFloat(drawerRoot, View.TRANSLATION_Y, 0f, endTranslation).apply {
                duration = 240
                interpolator = AccelerateInterpolator(1.2f)
            }
            val fadeOut = ObjectAnimator.ofFloat(drawerRoot, View.ALPHA, 1f, 0f).apply {
                duration = 200
                interpolator = LinearInterpolator()
            }
            openCloseAnimator = AnimatorSet().apply {
                playTogether(translate, fadeOut)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        drawerRoot.isVisible = false
                    }
                })
                start()
            }
        } else {
            drawerRoot.isVisible = false
            drawerRoot.translationY = 0f
            drawerRoot.alpha = 1f
        }
    }

    fun isShown(): Boolean = isOpen

    fun refreshAppearance(iconPackChanged: Boolean) {
        val currentAdapter = adapter ?: return

        val desiredColumns = activity.getPreferredGridColumns()
        if (desiredColumns != currentColumns) {
            currentColumns = desiredColumns
            drawerLayoutManager?.spanCount = desiredColumns
            drawerLayoutManager?.requestLayout()
        }

        refreshAdapterAppearance(currentAdapter, iconPackChanged)
        hotseatAdapter?.let { refreshAdapterAppearance(it, iconPackChanged, forceHideNames = true) }

        val desiredDockCapacity = appListManager.getStockHotseatCount()
        if (hotseatLayoutManager?.spanCount != desiredDockCapacity) {
            hotseatLayoutManager?.spanCount = desiredDockCapacity
            hotseatLayoutManager?.requestLayout()
        }
    }

    private fun refreshAdapterAppearance(target: AppAdapter, iconPackChanged: Boolean, forceHideNames: Boolean = false) {
        if (iconPackChanged) {
            target.refreshIcons()
        }

        val iconStyle = sharedPreferences.getString(com.guruswarupa.launch.models.Constants.Prefs.ICON_STYLE, "squircle") ?: "squircle"
        if (iconStyle != target.getCurrentIconStyle()) {
            target.updateIconStyle(iconStyle)
        }

        val iconSize = sharedPreferences.getInt(com.guruswarupa.launch.models.Constants.Prefs.ICON_SIZE, 40)
        if (iconSize != target.getCurrentIconSize()) {
            target.updateIconSize(iconSize)
        }

        val showAppNamesInGrid = !forceHideNames &&
            sharedPreferences.getBoolean(com.guruswarupa.launch.models.Constants.Prefs.SHOW_APP_NAME_IN_GRID, true)
        target.updateShowAppNamesInGrid(showAppNamesInGrid)

        target.refreshTypography()
    }
}
