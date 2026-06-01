package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserManager
import android.provider.ContactsContract
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.core.ShareManager
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.managers.WebAppIconFetcher
import com.guruswarupa.launch.managers.WebAppManager
import com.guruswarupa.launch.models.AppMetadata
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.activities.WebAppActivity
import com.guruswarupa.launch.ui.activities.WebAppSettingsActivity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class AppAdapter(
    private val activity: MainActivity,
    appList: MutableList<ResolveInfo>,
    private val searchBox: AutoCompleteTextView,
    private var isGridMode: Boolean,
    private val context: Context,
    private val prefs: android.content.SharedPreferences
) : ListAdapter<ResolveInfo, AppAdapter.ViewHolder>(AppListDiffCallback()) {

    companion object {
        const val VIEW_TYPE_LIST = 0
        const val VIEW_TYPE_GRID = 1
        const val VIEW_TYPE_SEPARATOR = 2
        const val VIEW_TYPE_SEPARATOR_SMALL = 3
        const val SEPARATOR_PACKAGE = "com.guruswarupa.launch.SEPARATOR"


        const val PAYLOAD_ICON_STYLE = 1
        const val PAYLOAD_ICON_SIZE = 2
        const val PAYLOAD_VIEW_MODE = 3
        const val PAYLOAD_ICON_VISUAL_STATE = 4

        private val SPECIAL_PACKAGE_NAMES = setOf(
            "contact_unified", "play_store_search", "maps_search", "yt_search", "browser_search", "math_result",
            "file_result", "settings_result", "system_settings_result",
            "launcher_settings_shortcut", "launcher_vault_shortcut"
        )
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: com.google.android.material.imageview.ShapeableImageView? = view.findViewById(R.id.app_icon)
        val appName: TextView? = view.findViewById(R.id.app_name)
        val appUsageTime: TextView? = view.findViewById(R.id.app_usage_time)
        val container: View? = view.findViewById(R.id.app_item_container)
        var lastClickTime = 0L
    }

    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val mainUserSerial = userManager.getSerialNumberForUser(Process.myUserHandle()).toInt()
    private val usageStatsManager = AppUsageStatsManager(activity)
    private val labelCache = ConcurrentHashMap<String, String>()
    private val usageCache = ConcurrentHashMap<String, String>()
    private val executor = Executors.newSingleThreadExecutor()
    private val pendingLabelTasks = ConcurrentHashMap<String, Future<*>>()
    private var itemsRendered = 0
    private var isFastScrolling = false
    private val fastScrollDebounceHandler = Handler(Looper.getMainLooper())
    private var fastScrollDebounceRunnable: Runnable? = null
    private var currentIconStyle = prefs.getString(Constants.Prefs.ICON_STYLE, "squircle") ?: "round"
    private var currentIconSize = prefs.getInt(Constants.Prefs.ICON_SIZE, 40)
    private var currentShowAppNamesInGrid = prefs.getBoolean(Constants.Prefs.SHOW_APP_NAME_IN_GRID, true)

    private val iconLoader = IconLoader(
        activity = activity,
        context = context,
        separatorPackage = SEPARATOR_PACKAGE,
        specialPackageNames = SPECIAL_PACKAGE_NAMES,
        sharedPreferences = prefs,
        cacheManager = activity.cacheManager
    ).apply {
        updateIconStyle(currentIconStyle)
        updateIconSize(currentIconSize)
    }

    private val appClickHandler = AppClickHandler(
        activity = activity,
        context = context,
        searchBox = searchBox,
        userManager = userManager,
        mainUserSerial = mainUserSerial,
        labelResolver = { packageName, appInfo ->
            labelCache["${packageName}|${appInfo.preferredOrder}"] ?: packageName
        }
    )

    private val searchResultBinderRegistry = createSearchResultBinderRegistry(
        activity = activity,
        context = context,
        searchBox = searchBox,
        iconLoader = iconLoader,
        showContactChoiceDialog = ::showContactChoiceDialog,
        getPhotoUriForContact = ::getPhotoUriForContact,
        applyIconVisualState = { packageName, holder -> applyIconVisualState(packageName, holder.appIcon) }
    )

    init {
        setHasStableIds(true)
        submitList(ArrayList(appList))
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position)
        return ("${item.activityInfo.packageName}|${item.activityInfo.name}|${item.preferredOrder}").hashCode().toLong()
    }

    fun updateViewMode(isGrid: Boolean) {
        if (this.isGridMode != isGrid) {
            this.isGridMode = isGrid
            notifyItemRangeChanged(0, currentList.size, PAYLOAD_VIEW_MODE)
        }
    }

    fun setFastScrollingState(isScrolling: Boolean) {
        isFastScrolling = isScrolling
        fastScrollDebounceRunnable?.let { fastScrollDebounceHandler.removeCallbacks(it) }
        if (!isScrolling) {
            fastScrollDebounceRunnable = Runnable { forceRefreshVisibleIcons() }
            fastScrollDebounceHandler.postDelayed(fastScrollDebounceRunnable!!, 100)
        }
    }

    private fun forceRefreshVisibleIcons() {}

    fun clearUsageCache() {
        usageCache.clear()
    }

    fun clearContactPhotoCache() {
        iconLoader.clearContactPhotoCache()
    }

    fun cleanup() {
        iconLoader.cleanup()
    }

    fun getCurrentIconStyle(): String = currentIconStyle
    
    fun getCurrentIconSize(): Int = currentIconSize

    fun updateIconStyle(style: String) {
        currentIconStyle = style
        iconLoader.updateIconStyle(style)
        (context as? Activity)?.runOnUiThread {
            notifyItemRangeChanged(0, currentList.size, PAYLOAD_ICON_STYLE)
        }
    }

    fun updateIconSize(size: Int) {
        currentIconSize = size
        iconLoader.updateIconSize(size)
        (context as? Activity)?.runOnUiThread {
            notifyItemRangeChanged(0, currentList.size, PAYLOAD_ICON_SIZE)
        }
    }

    fun refreshIcons() {
        iconLoader.onIconPackChanged()
        iconLoader.clearIconCaches(clearDiskCache = false) // Let onIconPackChanged handle disk cache
        (context as? Activity)?.runOnUiThread {
            notifyItemRangeChanged(0, currentList.size, PAYLOAD_ICON_STYLE)
        }
    }

    fun updateShowAppNamesInGrid(show: Boolean) {
        if (currentShowAppNamesInGrid != show) {
            currentShowAppNamesInGrid = show
            if (isGridMode) {
                (context as? Activity)?.runOnUiThread {
                    notifyItemRangeChanged(0, currentList.size, PAYLOAD_VIEW_MODE)
                }
            }
        }
    }


    fun getItemAtPosition(position: Int): ResolveInfo? {
        if (position < 0 || position >= currentList.size) return null
        return getItem(position)
    }


    fun getCurrentListSize(): Int = currentList.size

    private fun applyIconVisualState(packageName: String, imageView: ImageView?) {
        if (imageView == null) return

        val overDailyLimit = packageName !in SPECIAL_PACKAGE_NAMES &&
            packageName != SEPARATOR_PACKAGE &&
            !WebAppManager.isWebAppPackage(packageName) &&
            activity.appTimerManager.isAppOverDailyLimit(packageName)

        if (overDailyLimit) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            imageView.colorFilter = ColorMatrixColorFilter(matrix)
            imageView.alpha = 0.5f
        } else {
            imageView.colorFilter = null
            imageView.clearColorFilter()
            imageView.alpha = 1f
            imageView.drawable?.clearColorFilter()
        }
    }

    fun getAppLabel(position: Int): String {
        if (position < 0 || position >= currentList.size) return ""
        val appInfo = getItem(position)
        val packageName = appInfo.activityInfo.packageName
        if (packageName == SEPARATOR_PACKAGE) return ""
        if (WebAppManager.isWebAppPackage(packageName)) return appInfo.activityInfo.name ?: ""
        if (packageName in SPECIAL_PACKAGE_NAMES) return appInfo.activityInfo.name ?: ""
        val cacheKey = "${packageName}|${appInfo.preferredOrder}"
        return labelCache[cacheKey] ?: appInfo.activityInfo.name ?: packageName
    }

    fun updateAppList(newAppList: List<ResolveInfo>) {
        val newItems = ArrayList(newAppList)
        val isFirstLoad = itemsRendered == 0

        try {
            val metadataCache = activity.cacheManager.getMetadataCache()
            for (app in newItems) {
                val packageName = app.activityInfo.packageName
                if (packageName == SEPARATOR_PACKAGE) continue
                val cacheKey = "${packageName}|${app.preferredOrder}"

                val cachedMetadata = metadataCache[cacheKey]
                if (cachedMetadata != null && !labelCache.containsKey(cacheKey)) {
                    labelCache[cacheKey] = cachedMetadata.label
                }
            }
        } catch (_: Exception) {
        }


        submitList(newItems) {

            if (isFirstLoad && newItems.isNotEmpty()) {
                itemsRendered = newItems.size
                executor.execute {
                    iconLoader.preloadIcons(newItems.filter { it.activityInfo.packageName != SEPARATOR_PACKAGE })
                }
            }
        }
    }

    fun forceRebindViewHolder(viewHolder: ViewHolder, position: Int) {
        if (position < 0 || position >= currentList.size) return
        onBindViewHolder(viewHolder, position)
    }

    private class AppListDiffCallback : DiffUtil.ItemCallback<ResolveInfo>() {
        override fun areItemsTheSame(oldItem: ResolveInfo, newItem: ResolveInfo): Boolean {
            if (oldItem.activityInfo.packageName == SEPARATOR_PACKAGE && newItem.activityInfo.packageName == SEPARATOR_PACKAGE) {
                return oldItem.activityInfo.name == newItem.activityInfo.name
            }
            return oldItem.activityInfo.packageName == newItem.activityInfo.packageName &&
                oldItem.preferredOrder == newItem.preferredOrder
        }

        override fun areContentsTheSame(oldItem: ResolveInfo, newItem: ResolveInfo): Boolean {
            return oldItem.activityInfo.packageName == newItem.activityInfo.packageName &&
                oldItem.activityInfo.name == newItem.activityInfo.name &&
                oldItem.preferredOrder == newItem.preferredOrder
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        val packageName = item.activityInfo.packageName

        if (packageName == SEPARATOR_PACKAGE) {
            val separatorId = item.activityInfo.name ?: ""
            return if (separatorId.startsWith("letter_separator_") || separatorId.startsWith("small_separator_") || separatorId == "launcher_shortcuts_separator") {
                VIEW_TYPE_SEPARATOR_SMALL
            } else {
                VIEW_TYPE_SEPARATOR
            }
        }

        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = when (viewType) {
            VIEW_TYPE_SEPARATOR -> R.layout.item_app_separator
            VIEW_TYPE_SEPARATOR_SMALL -> R.layout.item_app_separator_small
            VIEW_TYPE_GRID -> R.layout.app_item_grid
            else -> R.layout.app_item
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        TypographyManager.applyToView(view)
        return ViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val appInfo = getItem(position)
        val packageName = appInfo.activityInfo.packageName
        val serial = appInfo.preferredOrder
        val cacheKey = "${packageName}|${serial}"

        if (packageName == SEPARATOR_PACKAGE) return


        if (payloads.isNotEmpty()) {
            var needsFullBind = false

            for (payload in payloads) {
                when (payload) {
                    PAYLOAD_ICON_STYLE -> {

                        iconLoader.applyShapeAppearance(holder.appIcon)
                        holder.appIcon?.setImageDrawable(null)
                        bindCachedOrAsyncIcon(holder, appInfo, cacheKey)
                    }
                    PAYLOAD_ICON_SIZE -> {

                        iconLoader.updateIconSize(holder.appIcon)
                        holder.appIcon?.setImageDrawable(null)
                        bindCachedOrAsyncIcon(holder, appInfo, cacheKey)
                    }
                    PAYLOAD_VIEW_MODE -> {

                        needsFullBind = true
                    }
                    PAYLOAD_ICON_VISUAL_STATE -> {

                        applyIconVisualState(packageName, holder.appIcon)
                    }
                }
            }

            if (!needsFullBind && payloads.none { it == PAYLOAD_VIEW_MODE }) {
                return
            }
        }


        if (WebAppManager.isWebAppPackage(packageName)) {
            bindWebApp(holder, appInfo, packageName)
            return
        }

        val needsRefresh = holder.itemView.tag != null && holder.itemView.tag != cacheKey
        if (needsRefresh) {
            TypographyManager.applyToView(holder.itemView)
        }

        iconLoader.updateIconSize(holder.appIcon)
        iconLoader.applyShapeAppearance(holder.appIcon)
        if (needsRefresh) holder.appIcon?.setImageDrawable(null)

        holder.appIcon?.background = null
        holder.itemView.elevation = 0f
        holder.itemView.tag = cacheKey
        configureLabelVisibility(holder)
        holder.appUsageTime?.visibility = View.GONE

        if (searchResultBinderRegistry.bind(holder, appInfo, position)) {
            return
        }

        bindStandardApp(holder, appInfo, position, packageName, cacheKey, serial)
    }

    private fun configureLabelVisibility(holder: ViewHolder) {
        if (isGridMode) {
            holder.appName?.visibility = if (currentShowAppNamesInGrid) View.VISIBLE else View.GONE
        } else {
            holder.appName?.visibility = View.VISIBLE
        }
    }

    private fun bindStandardApp(
        holder: ViewHolder,
        appInfo: ResolveInfo,
        position: Int,
        packageName: String,
        cacheKey: String,
        serial: Int
    ) {
        val activityName = appInfo.activityInfo.name
        if (packageName == activity.packageName) {
            when {
                activityName.contains("SettingsActivity") -> {
                    holder.appName?.text = activity.getString(R.string.settings_app_name)
                    iconLoader.setIconResource(holder.appIcon, R.mipmap.ic_launcher)
                }
                activityName.contains("EncryptedVaultActivity") -> {
                    holder.appName?.text = activity.getString(R.string.vault_app_name)
                    iconLoader.setIconResource(holder.appIcon, R.drawable.ic_vault)
                }
                else -> {

                    holder.appName?.text = labelCache[cacheKey] ?: packageName
                    bindCachedOrAsyncIcon(holder, appInfo, cacheKey)
                }
            }
        } else {
            bindAppLabel(holder, appInfo, packageName, cacheKey)
            bindCachedOrAsyncIcon(holder, appInfo, cacheKey)
        }

        applyIconVisualState(packageName, holder.appIcon)

        if (position < currentList.size - 1 && position % 8 == 0) {
            iconLoader.preloadNextIcons(currentList, position + 1, minOf(position + Constants.Limits.MAX_VISIBLE_PRELOAD_ITEMS, currentList.size))
        }

        holder.itemView.setOnClickListener {
            appClickHandler.handleAppClick(holder, appInfo, packageName, serial)
        }
        holder.itemView.setOnLongClickListener {
            showAppContextMenu(holder.itemView, packageName, appInfo)
            true
        }
    }

    private fun bindAppLabel(
        holder: ViewHolder,
        appInfo: ResolveInfo,
        packageName: String,
        cacheKey: String
    ) {
        val cachedLabel = labelCache[cacheKey]
        if (cachedLabel != null) {
            holder.appName?.text = cachedLabel
            return
        }


        holder.appName?.text = packageName
        loadLabelAsync(holder, appInfo, packageName, cacheKey)
    }

    private fun bindCachedOrAsyncIcon(
        holder: ViewHolder,
        appInfo: ResolveInfo,
        cacheKey: String
    ) {
        val packageName = appInfo.activityInfo.packageName
        val cachedIcon = iconLoader.getCachedIcon(cacheKey)
        if (cachedIcon != null) {

            if (!(cachedIcon is android.graphics.drawable.BitmapDrawable && cachedIcon.bitmap.isRecycled)) {
                holder.appIcon?.setImageDrawable(cachedIcon)
                applyIconVisualState(packageName, holder.appIcon)
            } else {

                iconLoader.setIconResource(holder.appIcon, R.drawable.ic_default_app_icon)
                val priority = if (isFastScrolling) IconLoader.PRIORITY_HIGH else IconLoader.PRIORITY_MEDIUM
                iconLoader.submitIconLoadTask(appInfo, priority, holder) { readyPackageName, readyHolder ->
                    applyIconVisualState(readyPackageName, readyHolder.appIcon)
                }
            }
            return
        }

        iconLoader.setIconResource(holder.appIcon, R.drawable.ic_default_app_icon)
        val priority = if (isFastScrolling) IconLoader.PRIORITY_HIGH else IconLoader.PRIORITY_MEDIUM
        iconLoader.submitIconLoadTask(appInfo, priority, holder) { readyPackageName, readyHolder ->
            applyIconVisualState(readyPackageName, readyHolder.appIcon)
        }
    }

    override fun getItemCount(): Int = currentList.size

    private fun loadLabelAsync(holder: ViewHolder, appInfo: ResolveInfo, packageName: String, cacheKey: String) {
        if (labelCache.containsKey(cacheKey)) return
        pendingLabelTasks[cacheKey]?.cancel(true)

        val labelTask = object : Runnable, Future<Boolean> {
            @Volatile private var isDone = false
            @Volatile private var isCancelled = false

            override fun run() {
                try {
                    val label = appInfo.loadLabel(activity.packageManager).toString()
                    labelCache[cacheKey] = label
                    try {
                        activity.cacheManager.updateMetadataCache(
                            packageName,
                            AppMetadata(packageName, appInfo.activityInfo.name, label, System.currentTimeMillis())
                        )
                    } catch (_: Exception) {
                    }

                    (context as? Activity)?.runOnUiThread {
                        val currentPosition = holder.bindingAdapterPosition
                        if (currentPosition != RecyclerView.NO_POSITION && holder.itemView.tag == cacheKey) {
                            holder.appName?.text = label
                        }
                    }
                } catch (_: Exception) {
                    labelCache[cacheKey] = packageName
                    (context as? Activity)?.runOnUiThread {
                        val currentPosition = holder.bindingAdapterPosition
                        if (currentPosition != RecyclerView.NO_POSITION && holder.itemView.tag == cacheKey) {
                            holder.appName?.text = packageName
                        }
                    }
                } finally {
                    isDone = true
                }
            }

            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                if (isDone || isCancelled) return false
                isCancelled = true
                return true
            }

            override fun isCancelled(): Boolean = isCancelled
            override fun isDone(): Boolean = isDone
            override fun get(): Boolean? = null
            override fun get(timeout: Long, unit: TimeUnit): Boolean? = null
        }

        executor.execute(labelTask)
        pendingLabelTasks[cacheKey] = labelTask
        if (pendingLabelTasks.size > 50) {
            pendingLabelTasks.entries.removeAll { it.value.isDone }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
        val popupMenu = PopupMenu(activity, view, Gravity.END, 0, R.style.PopupMenuStyle)
        popupMenu.menuInflater.inflate(R.menu.app_context_menu, popupMenu.menu)
        val textColor = ContextCompat.getColor(activity, R.color.text)
        val cacheKey = "${packageName}|${appInfo.preferredOrder}"
        val appName = labelCache[cacheKey] ?: packageName

        val dailyLimitItem = popupMenu.menu.add(0, 100, 0, activity.getString(R.string.daily_usage_set_limit_action))
        val limitSpannable = android.text.SpannableString(dailyLimitItem.title)
        limitSpannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, limitSpannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        dailyLimitItem.title = limitSpannable

        val usageHeader = popupMenu.menu.findItem(R.id.usage_header)
        if (usageHeader != null) {
            executor.execute {
                val usageTime = usageStatsManager.getAppUsageTime(packageName)
                val formattedTime = usageStatsManager.formatUsageTime(usageTime)
                activity.runOnUiThread {
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
                        notifyItemRangeChanged(0, currentList.size, PAYLOAD_ICON_VISUAL_STATE)
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

    private fun showWebAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
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
                    if (url.isNotBlank()) activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
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
        val cacheKey = "${packageName}|${appInfo.preferredOrder}"
        val appName = labelCache[cacheKey] ?: packageName
        ShareManager(activity).shareApk(packageName, appName)
    }

    private fun uninstallApp(packageName: String) {
        @Suppress("DEPRECATION")
        activity.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { data = "package:$packageName".toUri() })
    }

    private fun toggleFavoriteApp(packageName: String, appInfo: ResolveInfo) {
        val cacheKey = "${packageName}|${appInfo.preferredOrder}"
        val appName = labelCache[cacheKey] ?: packageName
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
            val cacheKey = "${packageName}|${appInfo.preferredOrder}"
            val appName = labelCache[cacheKey] ?: packageName
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

    private fun bindWebApp(holder: ViewHolder, appInfo: ResolveInfo, packageName: String) {
        iconLoader.updateIconSize(holder.appIcon)
        holder.itemView.tag = packageName
        iconLoader.applyShapeAppearance(holder.appIcon)
        iconLoader.setIconResource(holder.appIcon, R.drawable.ic_browser)
        holder.appIcon?.background = null
        holder.appName?.text = appInfo.activityInfo.name
        holder.appUsageTime?.visibility = View.GONE
        configureLabelVisibility(holder)
        applyIconVisualState(packageName, holder.appIcon)

        val siteUrl = appInfo.activityInfo.nonLocalizedLabel?.toString().orEmpty()
        if (siteUrl.isNotBlank()) {
            WebAppIconFetcher.loadIcon(activity, siteUrl) { drawable ->
                if (holder.itemView.tag == packageName && drawable != null) {
                    iconLoader.setIconDrawable(holder.appIcon, drawable, useLegacyIconPlate = true)
                    applyIconVisualState(packageName, holder.appIcon)
                }
            }
        }

        holder.itemView.setOnClickListener {
            openWebApp(appInfo)
            searchBox.text.clear()
            activity.appSearchManager.filterAppsAndContacts("")
        }
        holder.itemView.setOnLongClickListener {
            showWebAppContextMenu(holder.itemView, packageName, appInfo)
            true
        }
    }

    private fun openWebApp(appInfo: ResolveInfo) {
        val name = appInfo.activityInfo.name
        val url = appInfo.activityInfo.nonLocalizedLabel?.toString().orEmpty()
        if (url.isBlank()) {
            Toast.makeText(activity, R.string.web_app_load_failed, Toast.LENGTH_SHORT).show()
            return
        }


        val webAppManager = com.guruswarupa.launch.managers.WebAppManager(
            activity.getSharedPreferences(com.guruswarupa.launch.models.Constants.Prefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        )
        val webAppEntry = webAppManager.getWebApps().firstOrNull {
            it.name == name && it.url == url
        }

        activity.startActivity(
            Intent(activity, WebAppActivity::class.java).apply {
                putExtra(WebAppActivity.EXTRA_WEB_APP_NAME, name)
                putExtra(WebAppActivity.EXTRA_WEB_APP_URL, url)
                putExtra(WebAppActivity.EXTRA_BLOCK_REDIRECTS, webAppEntry?.blockRedirects ?: true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        )
    }

    private fun showContactChoiceDialog(contactName: String) {
        val phoneNumber = getPhoneNumberForContact(contactName)
        val photoUri = getPhotoUriForContact(contactName)
        val options = listOf(
            activity.getString(R.string.call_button) to R.drawable.ic_phone,
            activity.getString(R.string.whatsapp) to R.drawable.ic_whatsapp,
            activity.getString(R.string.sms) to R.drawable.ic_message
        )
        val adapter = object : ArrayAdapter<Pair<String, Int>>(activity, R.layout.dialog_contact_item, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.dialog_contact_item, parent, false)
                val item = getItem(position)!!
                view.findViewById<ImageView>(R.id.option_icon).setImageResource(item.second)
                view.findViewById<TextView>(R.id.option_text).text = item.first
                return view
            }
        }
        val builder = AlertDialog.Builder(activity, R.style.CustomDialogTheme)

        @SuppressLint("InflateParams")
        val titleView = LayoutInflater.from(activity).inflate(R.layout.dialog_contact_title, null)
        titleView.findViewById<TextView>(R.id.contact_name).text = contactName
        titleView.findViewById<TextView>(R.id.contact_number).text = phoneNumber
        val photoImageView = titleView.findViewById<ImageView>(R.id.contact_photo)
        if (photoUri != null) {
            try {
                activity.contentResolver.openInputStream(photoUri.toUri())?.use { inputStream ->
                    val drawable = Drawable.createFromStream(inputStream, photoUri)
                    if (drawable != null) photoImageView.setImageDrawable(drawable) else photoImageView.setImageResource(R.drawable.ic_person)
                }
            } catch (_: Exception) {
                photoImageView.setImageResource(R.drawable.ic_person)
            }
        } else {
            photoImageView.setImageResource(R.drawable.ic_person)
        }

        builder.setCustomTitle(titleView).setAdapter(adapter) { _, which ->
            when (which) {
                0 -> call(phoneNumber)
                1 -> activity.contactActionHandler.openWhatsAppChat(contactName)
                2 -> activity.contactActionHandler.openSMSChat(contactName)
            }
        }.setNegativeButton(activity.getString(R.string.cancel_button), null).show()
    }

    private fun getPhotoUriForContact(contactName: String): String? {
        val cursor = activity.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
            "${ContactsContract.Contacts.DISPLAY_NAME} = ?",
            arrayOf(contactName),
            null
        )
        var photoUri: String? = null
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                if (index != -1) photoUri = it.getString(index)
            }
        }
        return photoUri
    }

    private fun call(phoneNumber: String) {
        activity.startActivity(Intent(Intent.ACTION_DIAL).apply { data = "tel:$phoneNumber".toUri() })
    }

    private fun getPhoneNumberForContact(contactName: String): String {
        val cursor: Cursor? = activity.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(contactName),
            null
        )
        var phoneNumber: String? = null
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (index != -1) phoneNumber = it.getString(index)
            }
        }
        return phoneNumber ?: activity.getString(R.string.contact_phone_not_found)
    }
}
