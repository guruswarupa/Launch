package com.guruswarupa.launch

import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.guruswarupa.launch.models.Constants
import java.io.File

interface SearchResultBinder {
    val packageName: String
    fun bind(holder: AppAdapter.ViewHolder, appInfo: ResolveInfo, position: Int)
}

class SearchResultBinderRegistry(
    private val binders: Map<String, SearchResultBinder>
) {
    fun bind(holder: AppAdapter.ViewHolder, appInfo: ResolveInfo, position: Int): Boolean {
        val binder = binders[appInfo.activityInfo.packageName] ?: return false
        binder.bind(holder, appInfo, position)
        return true
    }
}

class ContactSearchResultBinder(
    private val activity: MainActivity,
    private val searchBox: AutoCompleteTextView,
    private val iconLoader: IconLoader,
    private val applyIconVisualState: (String, AppAdapter.ViewHolder) -> Unit,
    private val showContactChoiceDialog: (String) -> Unit,
    private val getPhotoUriForContact: (String) -> String?
) : SearchResultBinder {
    override val packageName: String = "contact_unified"

    override fun bind(holder: AppAdapter.ViewHolder, appInfo: ResolveInfo, position: Int) {
        holder.itemView.tag = packageName
        val contactName = appInfo.activityInfo.name
        val cacheKey = "${packageName}|${appInfo.preferredOrder}"
        iconLoader.loadContactPhoto(
            holder = holder,
            cacheKey = cacheKey,
            contactName = contactName,
            fallbackResId = R.drawable.ic_person,
            getPhotoUriForContact = getPhotoUriForContact
        ) {
            applyIconVisualState(packageName, holder)
        }
        holder.appName?.text = contactName
        holder.itemView.setOnClickListener {
            showContactChoiceDialog(contactName)
            searchBox.text.clear()
        }
    }
}

class PackageIconSearchResultBinder(
    override val packageName: String,
    private val activity: MainActivity,
    private val searchBox: AutoCompleteTextView,
    private val iconLoader: IconLoader,
    private val iconCacheId: String,
    private val iconPackages: List<String>,
    private val labelProvider: (ResolveInfo) -> String,
    private val clickAction: (ResolveInfo) -> Unit,
    private val applyIconVisualState: (String, AppAdapter.ViewHolder) -> Unit
) : SearchResultBinder {
    override fun bind(holder: AppAdapter.ViewHolder, appInfo: ResolveInfo, position: Int) {
        holder.itemView.tag = packageName
        val cacheKey = "${packageName}|${appInfo.preferredOrder}"
        iconLoader.loadSpecialAppIcon(
            holder = holder,
            cacheKey = cacheKey,
            cacheId = iconCacheId,
            fallbackResId = R.drawable.ic_default_app_icon,
            candidatePackages = iconPackages
        ) {
            applyIconVisualState(packageName, holder)
        }
        holder.appName?.text = labelProvider(appInfo)
        holder.itemView.setOnClickListener {
            clickAction(appInfo)
            searchBox.text.clear()
        }
    }
}

class ResourceIconSearchResultBinder(
    override val packageName: String,
    private val searchBox: AutoCompleteTextView,
    private val iconLoader: IconLoader,
    private val iconResId: Int,
    private val labelProvider: (ResolveInfo) -> String,
    private val clickAction: ((ResolveInfo) -> Unit)? = null,
    private val applyIconVisualState: (String, AppAdapter.ViewHolder) -> Unit
) : SearchResultBinder {
    override fun bind(holder: AppAdapter.ViewHolder, appInfo: ResolveInfo, position: Int) {
        holder.itemView.tag = packageName
        iconLoader.setIconResource(holder.appIcon, iconResId)
        holder.appName?.text = labelProvider(appInfo)
        applyIconVisualState(packageName, holder)
        holder.itemView.setOnClickListener {
            clickAction?.invoke(appInfo)
            searchBox.text.clear()
        }
    }
}

class FileSearchResultBinder(
    private val activity: MainActivity,
    private val searchBox: AutoCompleteTextView,
    private val iconLoader: IconLoader,
    private val applyIconVisualState: (String, AppAdapter.ViewHolder) -> Unit
) : SearchResultBinder {
    override val packageName: String = "file_result"

    override fun bind(holder: AppAdapter.ViewHolder, appInfo: ResolveInfo, position: Int) {
        holder.itemView.tag = packageName
        val fileName = appInfo.activityInfo.name
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val fileIconRes = when {
            ext == "pdf" -> R.drawable.ic_pdf
            ext in listOf("doc", "docx") -> R.drawable.ic_word
            ext in listOf("ppt", "pptx") -> R.drawable.ic_presentation
            ext in listOf("xls", "xlsx") -> R.drawable.ic_spreadsheet
            else -> R.drawable.ic_file
        }
        iconLoader.setIconResource(holder.appIcon, fileIconRes)
        holder.appName?.text = fileName
        applyIconVisualState(packageName, holder)
        holder.itemView.setOnClickListener {
            val filePath = appInfo.activityInfo.nonLocalizedLabel.toString()
            val file = File(filePath)
            if (file.exists()) {
                if (com.guruswarupa.launch.ui.activities.DocumentViewerActivity.isSupported(fileName)) {
                    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
                    val intent = com.guruswarupa.launch.ui.activities.DocumentViewerActivity.createFileIntent(activity, uri, fileName)
                    activity.startActivity(intent)
                } else {
                    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, activity.contentResolver.getType(uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        activity.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(activity, "No app found to open this file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            searchBox.text.clear()
        }
    }
}

fun createSearchResultBinderRegistry(
    activity: MainActivity,
    context: android.content.Context,
    searchBox: AutoCompleteTextView,
    iconLoader: IconLoader,
    showContactChoiceDialog: (String) -> Unit,
    getPhotoUriForContact: (String) -> String?,
    applyIconVisualState: (String, AppAdapter.ViewHolder) -> Unit
): SearchResultBinderRegistry {
    val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val binders = listOf<SearchResultBinder>(
        ContactSearchResultBinder(
            activity = activity,
            searchBox = searchBox,
            iconLoader = iconLoader,
            applyIconVisualState = applyIconVisualState,
            showContactChoiceDialog = showContactChoiceDialog,
            getPhotoUriForContact = getPhotoUriForContact
        ),
        PackageIconSearchResultBinder(
            packageName = "play_store_search",
            activity = activity,
            searchBox = searchBox,
            iconLoader = iconLoader,
            iconCacheId = "com.android.vending",
            iconPackages = listOf("com.android.vending"),
            labelProvider = { info -> activity.getString(R.string.search_on_play_store, info.activityInfo.name) },
            clickAction = { info ->
                val encodedQuery = Uri.encode(info.activityInfo.name)
                activity.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/search?q=$encodedQuery".toUri()))
            },
            applyIconVisualState = applyIconVisualState
        ),
        PackageIconSearchResultBinder(
            packageName = "maps_search",
            activity = activity,
            searchBox = searchBox,
            iconLoader = iconLoader,
            iconCacheId = "com.google.android.apps.maps",
            iconPackages = listOf("com.google.android.apps.maps"),
            labelProvider = { info -> activity.getString(R.string.search_in_google_maps, info.activityInfo.name) },
            clickAction = { info ->
                val gmmIntentUri = "geo:0,0?q=${Uri.encode(info.activityInfo.name)}".toUri()
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                try {
                    activity.startActivity(mapIntent)
                } catch (_: Exception) {
                    Toast.makeText(activity, activity.getString(R.string.google_maps_not_installed), Toast.LENGTH_SHORT).show()
                }
            },
            applyIconVisualState = applyIconVisualState
        ),
        PackageIconSearchResultBinder(
            packageName = "yt_search",
            activity = activity,
            searchBox = searchBox,
            iconLoader = iconLoader,
            iconCacheId = "youtube_search",
            iconPackages = listOf("app.revanced.android.youtube", "com.google.android.youtube"),
            labelProvider = { info -> activity.getString(R.string.search_on_youtube, info.activityInfo.name) },
            clickAction = { info ->
                val ytIntentUri = "https://www.youtube.com/results?search_query=${Uri.encode(info.activityInfo.name)}".toUri()
                val ytIntent = Intent(Intent.ACTION_VIEW, ytIntentUri)
                var appOpened = false
                try {
                    ytIntent.setPackage("app.revanced.android.youtube")
                    activity.startActivity(ytIntent)
                    appOpened = true
                } catch (_: Exception) {
                    try {
                        ytIntent.setPackage("com.google.android.youtube")
                        activity.startActivity(ytIntent)
                        appOpened = true
                    } catch (_: Exception) {
                    }
                }
                if (!appOpened) {
                    Toast.makeText(activity, activity.getString(R.string.youtube_not_installed_opening_browser), Toast.LENGTH_SHORT).show()
                    activity.startActivity(Intent(Intent.ACTION_VIEW, ytIntentUri))
                }
            },
            applyIconVisualState = applyIconVisualState
        ),
        ResourceIconSearchResultBinder(
            packageName = "browser_search",
            searchBox = searchBox,
            iconLoader = iconLoader,
            iconResId = R.drawable.ic_browser,
            labelProvider = { info -> activity.getString(R.string.search_in_browser, info.activityInfo.name) },
            clickAction = { info ->
                val engine = prefs.getString(Constants.Prefs.SEARCH_ENGINE, "Google")
                val baseUrl = when (engine) {
                    "Bing" -> "https://www.bing.com/search?q="
                    "DuckDuckGo" -> "https://duckduckgo.com/?q="
                    "Ecosia" -> "https://www.ecosia.org/search?q="
                    "Brave" -> "https://search.brave.com/search?q="
                    "Startpage" -> "https://www.startpage.com/sp/search?query="
                    "Yahoo" -> "https://search.yahoo.com/search?p="
                    "Qwant" -> "https://www.qwant.com/?q="
                    else -> "https://www.google.com/search?q="
                }
                activity.startActivity(Intent(Intent.ACTION_VIEW, "$baseUrl${Uri.encode(info.activityInfo.name)}".toUri()))
            },
            applyIconVisualState = applyIconVisualState
        ),
        ResourceIconSearchResultBinder(
            packageName = "settings_result",
            searchBox = searchBox,
            iconLoader = iconLoader,
            iconResId = R.drawable.ic_settings,
            labelProvider = { info -> info.activityInfo.name },
            clickAction = { info ->
                val settingAction = info.activityInfo.nonLocalizedLabel?.toString() ?: ""
                activity.startActivity(com.guruswarupa.launch.utils.AndroidSettingsHelper.createSettingsIntent(settingAction))
            },
            applyIconVisualState = applyIconVisualState
        ),
        ResourceIconSearchResultBinder(
            packageName = "system_settings_result",
            searchBox = searchBox,
            iconLoader = iconLoader,
            iconResId = R.drawable.ic_settings,
            labelProvider = { info -> info.activityInfo.name },
            clickAction = { info ->
                val settingAction = info.activityInfo.nonLocalizedLabel?.toString() ?: ""
                val intent = com.guruswarupa.launch.utils.AndroidSettingsHelper.createSettingsIntent(settingAction)
                try {
                    if (intent.resolveActivity(activity.packageManager) != null) {
                        activity.startActivity(intent)
                    } else {
                        activity.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                } catch (e: Exception) {
                    activity.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            },
            applyIconVisualState = applyIconVisualState
        ),
        FileSearchResultBinder(
            activity = activity,
            searchBox = searchBox,
            iconLoader = iconLoader,
            applyIconVisualState = applyIconVisualState
        ),
        ResourceIconSearchResultBinder(
            packageName = "math_result",
            searchBox = searchBox,
            iconLoader = iconLoader,
            iconResId = R.drawable.ic_calculator,
            labelProvider = { info -> info.activityInfo.name },
            clickAction = null,
            applyIconVisualState = applyIconVisualState
        ),
        ResourceIconSearchResultBinder(
            packageName = "launcher_settings_shortcut",
            searchBox = searchBox,
            iconLoader = iconLoader,
            iconResId = R.drawable.ic_settings,
            labelProvider = { activity.getString(R.string.settings_app_name) },
            clickAction = {
                val intent = Intent(activity, com.guruswarupa.launch.ui.activities.SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                activity.startActivity(intent)
            },
            applyIconVisualState = applyIconVisualState
        ),
        ResourceIconSearchResultBinder(
            packageName = "launcher_vault_shortcut",
            searchBox = searchBox,
            iconLoader = iconLoader,
            iconResId = R.drawable.ic_vault,
            labelProvider = { activity.getString(R.string.vault_app_name) },
            clickAction = {
                val intent = Intent(activity, com.guruswarupa.launch.ui.activities.EncryptedVaultActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                activity.startActivity(intent)
            },
            applyIconVisualState = applyIconVisualState
        )
    ).associateBy { it.packageName }

    return SearchResultBinderRegistry(binders)
}
