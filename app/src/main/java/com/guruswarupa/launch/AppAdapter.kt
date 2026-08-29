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
import com.guruswarupa.launch.handlers.AppContextMenuHandler
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

class AppAdapter(
    private val activity: MainActivity,
    private val appList: MutableList<ResolveInfo>,
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
        const val PAYLOAD_USAGE = 5
        const val PAYLOAD_TYPOGRAPHY = 6

        private val SPECIAL_PACKAGE_NAMES = setOf(
            "com.android.settings",
            "com.google.android.googlequicksearchbox"
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
    private val labelCache = ConcurrentHashMap<String, String>()
    private val usageCache = ConcurrentHashMap<String, String>()
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val pendingLabelJobs = ConcurrentHashMap<String, Job>()
    private var itemsRendered = 0
    private var isFastScrolling = false
    private val fastScrollDebounceHandler = Handler(Looper.getMainLooper())
    private var currentIconStyle = prefs.getString(Constants.Prefs.ICON_STYLE, "squircle") ?: "round"
    private var currentIconSize = prefs.getInt(Constants.Prefs.ICON_SIZE, 40)
    private var currentShowAppNamesInGrid = prefs.getBoolean(Constants.Prefs.SHOW_APP_NAME_IN_GRID, true)

    private var currentFontScale = prefs.getInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, 100) / 100f
    private var currentFontStyle = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default"
    private var currentFontIntensity = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular") ?: "regular"
    private var currentFontColor = TypographyManager.getConfiguredFontColor(context)

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

    private val appContextMenuHandler = AppContextMenuHandler(
        activity = activity,
        executor = activity.backgroundExecutor,
        labelResolver = { packageName: String, appInfo: ResolveInfo ->
            labelCache["${packageName}|${appInfo.preferredOrder}"] ?: packageName
        },
        onAppModified = {
            notifyItemRangeChanged(0, getCurrentListSize(), PAYLOAD_ICON_VISUAL_STATE)
        },
        openWebApp = { appInfo: ResolveInfo -> openWebApp(appInfo) },
        shareManager = activity.shareManager
    )

    init {
        setHasStableIds(true)
        submitList(ArrayList(appList))
    }

    override fun getItemId(position: Int): Long {
        if (position < 0 || position >= currentList.size) return RecyclerView.NO_ID
        val item = getItem(position)
        val key = "${item.activityInfo.packageName}|${item.activityInfo.name}|${item.preferredOrder}"
        return fnv1a64(key)
    }

    fun updateViewMode(isGridMode: Boolean) {
        if (this.isGridMode != isGridMode) {
            this.isGridMode = isGridMode
            notifyItemRangeChanged(0, currentList.size, PAYLOAD_VIEW_MODE)
        }
    }

    fun setFastScrollingState(isScrolling: Boolean) {
        isFastScrolling = isScrolling
        fastScrollDebounceHandler.removeCallbacksAndMessages(null)
        if (!isScrolling) {
            fastScrollDebounceHandler.postDelayed({
                forceRefreshVisibleIcons()
            }, 100)
        }
    }

    private fun forceRefreshVisibleIcons() {
        notifyItemRangeChanged(0, currentList.size, PAYLOAD_ICON_VISUAL_STATE)
    }

    fun forceRebindViewHolder(holder: ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    fun clearUsageCache() {
        usageCache.clear()
        notifyItemRangeChanged(0, currentList.size, PAYLOAD_USAGE)
    }

    fun clearContactPhotoCache() {
        iconLoader.clearContactPhotoCache()
        notifyItemRangeChanged(0, currentList.size, PAYLOAD_ICON_VISUAL_STATE)
    }

    fun refreshTypography() {
        currentFontScale = prefs.getInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, 100) / 100f
        currentFontStyle = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default"
        currentFontIntensity = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular") ?: "regular"
        currentFontColor = TypographyManager.getConfiguredFontColor(context)
        notifyItemRangeChanged(0, currentList.size, PAYLOAD_TYPOGRAPHY)
    }

    fun cleanup() {
        adapterScope.cancel()
        iconLoader.cleanup()
        fastScrollDebounceHandler.removeCallbacksAndMessages(null)
    }

    fun getCurrentIconStyle(): String = currentIconStyle

    fun getCurrentIconSize(): Int = currentIconSize

    fun updateIconStyle(newStyle: String) {
        if (currentIconStyle != newStyle) {
            currentIconStyle = newStyle
            iconLoader.updateIconStyle(newStyle)
            notifyItemRangeChanged(0, currentList.size, PAYLOAD_ICON_STYLE)
        }
    }

    fun updateIconSize(newSize: Int) {
        if (currentIconSize != newSize) {
            currentIconSize = newSize
            iconLoader.updateIconSize(newSize)
            notifyItemRangeChanged(0, currentList.size, PAYLOAD_ICON_SIZE)
        }
    }

    fun refreshIcons() {
        iconLoader.updateIconStyle(currentIconStyle)
        iconLoader.updateIconSize(currentIconSize)
        adapterScope.launch(Dispatchers.IO) {
            iconLoader.preloadIcons(currentList.filter { it.activityInfo.packageName != SEPARATOR_PACKAGE })
            withContext(Dispatchers.Main) {
                notifyItemRangeChanged(0, currentList.size, PAYLOAD_ICON_STYLE)
            }
        }
    }

    fun updateShowAppNamesInGrid(show: Boolean) {
        if (currentShowAppNamesInGrid != show) {
            currentShowAppNamesInGrid = show
            if (isGridMode) {
                notifyItemRangeChanged(0, currentList.size)
            }
        }
    }

    fun getItemAtPosition(position: Int): ResolveInfo? {
        if (position < 0 || position >= currentList.size) return null
        return getItem(position)
    }

    fun getCurrentListSize(): Int = currentList.size

    fun applyIconVisualState(packageName: String, imageView: ImageView?) {
        if (imageView == null) return
        val isHidden = activity.hiddenAppManager.isAppHidden(packageName)
        if (isHidden) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            imageView.colorFilter = ColorMatrixColorFilter(matrix)
            imageView.alpha = 0.5f
        } else {
            imageView.clearColorFilter()
            imageView.alpha = 1f
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

        adapterScope.launch(Dispatchers.IO) {
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

            withContext(Dispatchers.Main) {
                submitList(newItems) {
                    if (isFirstLoad && newItems.isNotEmpty()) {
                        itemsRendered = newItems.size
                        adapterScope.launch(Dispatchers.IO) {
                            iconLoader.preloadIcons(newItems.filter { it.activityInfo.packageName != SEPARATOR_PACKAGE })
                        }
                    }
                }
            }
        }
    }

    private class AppListDiffCallback : DiffUtil.ItemCallback<ResolveInfo>() {
        override fun areItemsTheSame(oldItem: ResolveInfo, newItem: ResolveInfo): Boolean {
            if (oldItem.activityInfo.packageName == SEPARATOR_PACKAGE && newItem.activityInfo.packageName == SEPARATOR_PACKAGE) {
                return oldItem.activityInfo.name == newItem.activityInfo.name
            }
            return oldItem.activityInfo.packageName == newItem.activityInfo.packageName &&
                    oldItem.activityInfo.name == newItem.activityInfo.name &&
                    oldItem.preferredOrder == newItem.preferredOrder
        }

        override fun areContentsTheSame(oldItem: ResolveInfo, newItem: ResolveInfo): Boolean {
            if (oldItem.activityInfo.packageName == SEPARATOR_PACKAGE) {
                val oldName = oldItem.activityInfo.name
                val newName = newItem.activityInfo.name
                val oldLabel = oldItem.nonLocalizedLabel?.toString()
                val newLabel = newItem.nonLocalizedLabel?.toString()
                return oldName == newName && oldLabel == newLabel
            }
            return oldItem.activityInfo.packageName == newItem.activityInfo.packageName &&
                    oldItem.activityInfo.name == newItem.activityInfo.name &&
                    oldItem.preferredOrder == newItem.preferredOrder &&
                    oldItem.activityInfo.enabled == newItem.activityInfo.enabled
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when (item.activityInfo.packageName) {
            SEPARATOR_PACKAGE -> {
                val name = item.activityInfo.name ?: ""
                if (name == "SMALL" || name.startsWith("letter_separator_")) {
                    VIEW_TYPE_SEPARATOR_SMALL
                } else {
                    VIEW_TYPE_SEPARATOR
                }
            }
            else -> if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            VIEW_TYPE_GRID -> R.layout.app_item_grid
            VIEW_TYPE_SEPARATOR -> R.layout.item_app_separator
            VIEW_TYPE_SEPARATOR_SMALL -> R.layout.item_app_separator_small
            else -> R.layout.app_item
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val appInfo = getItem(position)
        val packageName = appInfo.activityInfo.packageName

        if (payloads.isNotEmpty()) {
            for (payload in payloads) {
                when (payload) {
                    PAYLOAD_ICON_STYLE -> {
                        iconLoader.applyShapeAppearance(holder.appIcon)
                        bindCachedOrAsyncIcon(holder, appInfo, packageName)
                        applyIconVisualState(packageName, holder.appIcon)
                    }
                    PAYLOAD_ICON_SIZE -> {
                        iconLoader.updateIconSize(holder.appIcon)
                        bindCachedOrAsyncIcon(holder, appInfo, packageName)
                    }
                    PAYLOAD_ICON_VISUAL_STATE -> applyIconVisualState(packageName, holder.appIcon)
                    PAYLOAD_VIEW_MODE -> {
                        configureLabelVisibility(holder)
                    }
                    PAYLOAD_USAGE -> {
                        bindUsageTime(holder, packageName)
                    }
                    PAYLOAD_TYPOGRAPHY -> {
                        TypographyManager.applyToViewTree(holder.itemView, currentFontScale, currentFontStyle, currentFontIntensity, currentFontColor)
                    }
                }
            }
            return
        }

        TypographyManager.applyToViewTree(holder.itemView, currentFontScale, currentFontStyle, currentFontIntensity, currentFontColor)

        if (packageName == SEPARATOR_PACKAGE) {
            holder.appName?.text = appInfo.nonLocalizedLabel ?: ""
            return
        }

        if (searchResultBinderRegistry.bind(holder, appInfo, position)) {
            return
        }

        if (WebAppManager.isWebAppPackage(packageName)) {
            bindWebApp(holder, appInfo, packageName)
            return
        }

        val cacheKey = "${packageName}|${appInfo.preferredOrder}"
        holder.itemView.tag = cacheKey

        val appName = labelCache[cacheKey]
        if (appName != null) {
            bindAppLabel(holder, appInfo, packageName, appName)
        } else {
            holder.appName?.text = ""
            loadLabelAsync(holder, appInfo, packageName, cacheKey)
        }

        iconLoader.updateIconSize(holder.appIcon)
        iconLoader.applyShapeAppearance(holder.appIcon)
        bindCachedOrAsyncIcon(holder, appInfo, packageName)
        configureLabelVisibility(holder)
        applyIconVisualState(packageName, holder.appIcon)

        holder.itemView.setOnClickListener {
            appClickHandler.handleAppClick(holder, appInfo, packageName, appInfo.preferredOrder)
        }

        holder.itemView.setOnLongClickListener {
            showAppContextMenu(holder.itemView, packageName, appInfo)
            true
        }
    }

    private fun configureLabelVisibility(holder: ViewHolder) {
        if (isGridMode) {
            holder.appName?.visibility = if (currentShowAppNamesInGrid) View.VISIBLE else View.GONE
        } else {
            holder.appName?.visibility = View.VISIBLE
        }
    }

    private fun bindUsageTime(holder: ViewHolder, packageName: String) {
        val usageTime = usageCache[packageName]
        if (usageTime != null) {
            holder.appUsageTime?.text = usageTime
            holder.appUsageTime?.visibility = View.VISIBLE
        } else {
            holder.appUsageTime?.visibility = View.GONE
        }
    }

    private fun bindAppLabel(holder: ViewHolder, appInfo: ResolveInfo, packageName: String, label: String) {
        holder.appName?.text = label
        bindUsageTime(holder, packageName)
    }

    private fun bindCachedOrAsyncIcon(holder: ViewHolder, appInfo: ResolveInfo, packageName: String) {
        val cacheKey = "${packageName}|${appInfo.preferredOrder}"
        val cachedIcon = iconLoader.getCachedIcon(cacheKey)

        if (cachedIcon != null) {
            iconLoader.setIconDrawable(holder.appIcon, cachedIcon)
        } else {
            iconLoader.setIconResource(holder.appIcon, R.drawable.ic_launcher_foreground)
            if (!isFastScrolling) {
                iconLoader.submitIconLoadTask(appInfo, IconLoader.PRIORITY_MEDIUM, holder) { _, _ ->
                    applyIconVisualState(packageName, holder.appIcon)
                }
            }
        }
    }

    override fun getItemCount(): Int = currentList.size

    private fun loadLabelAsync(holder: ViewHolder, appInfo: ResolveInfo, packageName: String, cacheKey: String) {
        pendingLabelJobs[cacheKey]?.cancel()

        pendingLabelJobs[cacheKey] = adapterScope.launch {
            val label = withContext(Dispatchers.IO) {
                try {
                    val loadedLabel = appInfo.loadLabel(activity.packageManager).toString()
                    labelCache[cacheKey] = loadedLabel
                    try {
                        activity.cacheManager.updateMetadataCache(
                            packageName,
                            AppMetadata(packageName, appInfo.activityInfo.name, loadedLabel, System.currentTimeMillis())
                        )
                    } catch (_: Exception) {
                    }
                    loadedLabel
                } catch (_: Exception) {
                    labelCache[cacheKey] = packageName
                    packageName
                }
            }

            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION && holder.itemView.tag == cacheKey) {
                holder.appName?.text = label
            }
        }

        if (pendingLabelJobs.size > 100) {
            pendingLabelJobs.entries.removeIf { it.value.isCompleted }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
        appContextMenuHandler.showAppContextMenu(view, packageName, appInfo)
    }

    private fun showWebAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
        appContextMenuHandler.showWebAppContextMenu(view, packageName, appInfo)
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
                    iconLoader.setIconDrawable(holder.appIcon, drawable)
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
        adapterScope.launch {
            val (phoneNumber, photoUri) = withContext(Dispatchers.IO) {
                getPhoneNumberForContact(contactName) to getPhotoUriForContact(contactName)
            }
            
            val options = listOf(
                activity.getString(R.string.call_button) to R.drawable.ic_phone,
                activity.getString(R.string.whatsapp) to R.drawable.ic_whatsapp,
                activity.getString(R.string.sms) to R.drawable.ic_message
            )
            val adapter = object : ArrayAdapter<Pair<String, Int>>(activity, R.layout.dialog_contact_item, options) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.dialog_contact_item, parent, false)
                    getItem(position)?.let { item ->
                        view.findViewById<ImageView>(R.id.option_icon).setImageResource(item.second)
                        view.findViewById<TextView>(R.id.option_text).text = item.first
                    }
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

private fun fnv1a64(value: String): Long {
    var hash: ULong = 0xcbf29ce484222325uL
    for (byte in value.toByteArray()) {
        hash = hash xor byte.toULong()
        hash = hash * 0x100000001b3uL
    }
    return (hash and 0x7FFFFFFFFFFFFFFFuL).toLong()
}
