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
    internal val activity: MainActivity,
    internal val drawerRoot: View,
    internal val recyclerView: RecyclerView,
    internal val searchBox: AutoCompleteTextView,
    internal val emptyState: View,
    internal val searchContainer: View,
    internal val addToHomeZone: View,
    internal val appListManager: AppListManager,
    internal val appDockManager: AppDockManager,
    internal val appOrderManager: AppOrderManager,
    internal val folderManager: FolderManager,
    internal val favoriteAppManager: FavoriteAppManager,
    internal val sharedPreferences: SharedPreferences,
    internal val screenPagerManager: ScreenPagerManager
) {
    companion object {
        internal const val MERGE_OVERLAP_THRESHOLD = 0.55f
    }

    internal var adapter: AppAdapter? = null
    internal var allOrderedApps: List<ResolveInfo> = emptyList()
    internal var isOpen = false
    internal var openCloseAnimator: Animator? = null
    internal var drawerLayoutManager: GridLayoutManager? = null
    internal var currentColumns = 0
    internal var hotseatAdapter: AppAdapter? = null
    internal var hotseatView: RecyclerView? = null
    val homeGestures: StockHomeGestures by lazy {
        StockHomeGestures(activity, screenPagerManager.createDoubleTapToLockDetector())
    }

    internal var homeGridAdapter: AppAdapter? = null
    internal var hotseatLayoutManager: GridLayoutManager? = null

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

    internal fun applyKidsModeFilter(list: List<ResolveInfo>): List<ResolveInfo> {
        val kidsModeManager = activity.kidsModeManager
        if (!kidsModeManager.isActive()) return list
        return list.filter { kidsModeManager.isAppAllowed(it.activityInfo.packageName) }
    }

    fun onFullAppListUpdated(fullListUnfiltered: List<ResolveInfo>) {
        if (!LayoutMode.isStock(sharedPreferences)) return
        val fullList = applyKidsModeFilter(fullListUnfiltered)
        recomputeOrderedApps(fullList)
        applyCurrentFilter()
        recomputeHotseat(fullList)

        if (isOpen) {
            activity.appSearchManager.updateData(
                activity.fullAppList, allOrderedApps, activity.contactManager.getContactsList()
            )
        }
    }

    internal fun recomputeHotseat(fullList: List<ResolveInfo>) {
        val currentHotseatAdapter = hotseatAdapter ?: return
        val focusMode = appDockManager.getCurrentMode()
        val workspaceMode = appDockManager.isWorkspaceModeActive()
        val filtered = appListManager.filterAndPrepareApps(fullList, focusMode, workspaceMode)
        val dockApps = appListManager.computeStockDock(filtered)
        currentHotseatAdapter.updateAppList(dockApps)
        hotseatView?.isVisible = dockApps.isNotEmpty()
    }

    internal fun recomputeOrderedApps(fullListUnfiltered: List<ResolveInfo>) {
        val focusMode = appDockManager.getCurrentMode()
        val fullList = applyKidsModeFilter(fullListUnfiltered)

        val filtered = appListManager.filterAndPrepareApps(fullList, focusMode, workspaceMode = false)
        val folders = if (activity.kidsModeManager.isActive()) {
            emptyList()
        } else {
            folderManager.getFolders(com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_FOLDERS)
        }
        val ordered = appOrderManager.applyOrder(
            filtered, appListManager, com.guruswarupa.launch.models.Constants.Prefs.STOCK_DRAWER_APP_ORDER, folders
        )

        allOrderedApps = if (activity.kidsModeManager.isActive()) {
            ordered
        } else {
            ordered +
                appListManager.createSeparatorInfo("SMALL") +
                appListManager.createLauncherShortcut("launcher_settings_shortcut") +
                appListManager.createLauncherShortcut("launcher_vault_shortcut")
        }
    }

    internal fun applyCurrentFilter() {
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

    internal fun setupSearch() {
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

    internal fun retargetSearchToDrawer() {
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

    internal fun ensureContactsLoadedForDrawerSearch() {
        if (activity.contactManager.hasLoadedContacts()) return
        activity.contactManager.loadContacts { _ ->
            if (activity.isFinishing || activity.isDestroyed || !isOpen) return@loadContacts
            retargetSearchToDrawer()
        }
    }

    internal fun setupModeToggles() {
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

    internal fun refreshModeToggles() {
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

    internal fun isReorderable(entry: ResolveInfo): Boolean {
        val packageName = entry.activityInfo.packageName
        return packageName != AppAdapter.SEPARATOR_PACKAGE && !packageName.startsWith("launcher_")
    }

    internal fun overlapFraction(dragged: View, target: View): Float {
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

    internal fun isDraggedOverZone(hostRecyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, zone: View): Boolean {
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

    internal class DropZone(val view: View, val onDrop: (AppAdapter, Int) -> Unit)

    internal fun setupCloseGesture() {
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
            private var downOnEmpty = false

            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                if (!LayoutMode.isStock(sharedPreferences) || isOpen) return false
                if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    downOnEmpty = rv.findChildViewUnder(e.x, e.y) == null
                }
                if (homeGestures.onEvent(e, downOnEmpty)) return true
                if (!isDrawerEnabled()) return false
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

        view.setOnTouchListener { _, event ->
            if (!LayoutMode.isStock(sharedPreferences) || isOpen) return@setOnTouchListener false
            if (homeGestures.onEvent(event, true)) return@setOnTouchListener true
            if (!isDrawerEnabled()) return@setOnTouchListener false
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

    internal fun refreshAdapterAppearance(target: AppAdapter, iconPackChanged: Boolean, forceHideNames: Boolean = false) {
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
