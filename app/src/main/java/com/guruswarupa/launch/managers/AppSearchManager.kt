package com.guruswarupa.launch.managers

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Environment
import android.widget.AutoCompleteTextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.models.AppMetadata
import com.guruswarupa.launch.utils.AndroidSettingsHelper
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.objecthunter.exp4j.ExpressionBuilder
import java.io.File
import javax.inject.Inject

@ActivityScoped
class AppSearchManager @Inject constructor(
    @ActivityContext private val context: Context
) {
    private val packageManager: PackageManager = context.packageManager
    private val fullAppList = mutableListOf<ResolveInfo>()
    private var homeAppList: List<ResolveInfo> = emptyList()
    private var adapter: AppAdapter? = null
    private var searchBox: AutoCompleteTextView? = null
    private var contactsList: List<String> = emptyList()
    private var appMetadataCache: Map<String, AppMetadata>? = null
    private var isAppFiltered: ((String) -> Boolean)? = null
    private var isFocusModeActive: (() -> Boolean)? = null
    private val lifecycleScope = (context as LifecycleOwner).lifecycleScope
    private var searchJob: Job? = null
    private var searchListenerAttached = false


    var onSearchQueryChanged: ((String) -> Unit)? = null

    private val appLabelCache = mutableMapOf<String, String>()
    private val searchCache = mutableMapOf<String, List<ResolveInfo>>()
    private val dataLock = Any()

    fun configure(
        fullAppList: MutableList<ResolveInfo>,
        homeAppList: List<ResolveInfo>,
        adapter: AppAdapter?,
        searchBox: AutoCompleteTextView,
        contactsList: List<String>,
        appMetadataCache: Map<String, AppMetadata>?,
        isAppFiltered: ((String) -> Boolean)?,
        isFocusModeActive: (() -> Boolean)?
    ) {
        this.appMetadataCache = appMetadataCache
        this.isAppFiltered = isAppFiltered
        this.isFocusModeActive = isFocusModeActive
        this.searchBox = searchBox
        this.adapter = adapter
        updateData(fullAppList, homeAppList, contactsList)
        attachSearchListener(searchBox)
    }

    fun isConfigured(): Boolean = searchListenerAttached

    private fun attachSearchListener(searchBox: AutoCompleteTextView) {
        if (searchListenerAttached) {
            return
        }
        searchListenerAttached = true
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                onSearchQueryChanged?.invoke(query)
                scheduleSearch(debounceMs = 10)
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    fun setAdapter(adapter: AppAdapter) {
        synchronized(dataLock) {
            this.adapter = adapter
        }
    }

    fun updateContactsList() {
        synchronized(dataLock) {
            searchCache.clear()
        }
        adapter?.clearContactPhotoCache()
    }

    fun getFullAppList(): List<ResolveInfo> {
        synchronized(dataLock) {
            return ArrayList(fullAppList)
        }
    }

    fun getHomeAppList(): List<ResolveInfo> {
        synchronized(dataLock) {
            return ArrayList(homeAppList)
        }
    }

    private fun getAppLabel(info: ResolveInfo): String {
        val packageName = info.activityInfo.packageName
        val serial = info.preferredOrder
        val cacheKey = "${packageName}|$serial"
        
        return appMetadataCache?.get(cacheKey)?.label?.lowercase()
            ?: appLabelCache.getOrPut(cacheKey) {
                try {
                    info.loadLabel(packageManager).toString().lowercase()
                } catch (_: Exception) {
                    packageName.lowercase()
                }
            }
    }

    private fun getSortKey(label: String): String {
        if (label.isEmpty()) return label
        val firstChar = label[0]
        return if (!firstChar.isLetter()) {
            "\uFFFF$label"
        } else {
            label
        }
    }

    enum class SearchMode {
        ALL, APPS, CONTACTS, FILES, MAPS, WEB, PLAYSTORE, YOUTUBE
    }

    private var currentSearchMode = SearchMode.ALL

    fun setSearchMode(mode: SearchMode) {
        currentSearchMode = mode
        refreshSearch()
    }

    fun updateData(newFullAppList: List<ResolveInfo>, newHomeAppList: List<ResolveInfo>, newContactsList: List<String>) {
        synchronized(dataLock) {

            val deduplicatedFullAppList = newFullAppList.distinctBy { "${it.activityInfo.packageName}|${it.activityInfo.name}" }

            if (deduplicatedFullAppList !== fullAppList) {
                fullAppList.clear()
                fullAppList.addAll(deduplicatedFullAppList)
            }
            homeAppList = ArrayList(newHomeAppList)
            contactsList = newContactsList
            searchCache.clear()
        }
        adapter?.clearContactPhotoCache()
        refreshSearch()
    }

    private fun refreshSearch() {
        if (searchBox == null) {
            return
        }
        scheduleSearch()
    }

    fun filterAppsAndContacts(query: String) {
        scheduleSearch(query = query)
    }

    private fun scheduleSearch(query: String? = null, debounceMs: Long = 0L) {
        val configuredSearchBox = searchBox ?: return
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            if (debounceMs > 0) {
                delay(debounceMs)
            }
            val currentQuery = query ?: configuredSearchBox.text?.toString().orEmpty()
            val newFilteredList = withContext(Dispatchers.IO) {
                buildFilteredList(currentQuery)
            }
            if (configuredSearchBox.text.toString() == currentQuery) {
                adapter?.updateAppList(newFilteredList)
            }
        }
    }

    private fun buildFilteredList(query: String): ArrayList<ResolveInfo> {
        val queryLower = query.lowercase().trim()
        val newFilteredList = ArrayList<ResolveInfo>()

        val fullAppListSnapshot: List<ResolveInfo>
        val homeAppListSnapshot: List<ResolveInfo>
        val contactsListSnapshot: List<String>

        synchronized(dataLock) {
            fullAppListSnapshot = ArrayList(fullAppList)
            homeAppListSnapshot = ArrayList(homeAppList)
            contactsListSnapshot = ArrayList(contactsList)
        }

        if (queryLower.isNotEmpty()) {
            evaluateMathExpression(query)?.let { result ->
                newFilteredList.add(createMathResultOption(query, result))
            } ?: run {
                val exactMatches = ArrayList<ResolveInfo>()
                val partialMatches = ArrayList<ResolveInfo>()

                if (currentSearchMode == SearchMode.ALL || currentSearchMode == SearchMode.APPS) {
                    fullAppListSnapshot.forEach { info ->
                        val packageName = info.activityInfo.packageName

                        if (packageName == context.packageName) {
                            val activityName = info.activityInfo.name
                            val isAllowedInternalActivity = activityName.contains("SettingsActivity") ||
                                                          activityName.contains("EncryptedVaultActivity")
                            if (!isAllowedInternalActivity || activityName.contains("MainActivity")) {
                                return@forEach
                            }
                        }

                        if (isAppFiltered?.invoke(packageName) == true) return@forEach

                        val label = getAppLabel(info)
                        when {
                            label == queryLower -> exactMatches.add(info)
                            label.startsWith(queryLower) -> partialMatches.add(0, info)
                            label.contains(queryLower) -> partialMatches.add(info)
                        }
                    }

                    val sortedExact = exactMatches.sortedBy { getSortKey(getAppLabel(it)) }
                    val sortedPartial = partialMatches.sortedBy { getSortKey(getAppLabel(it)) }

                    newFilteredList.addAll(sortedExact)
                    newFilteredList.addAll(sortedPartial)

                    getSettingsMatches(queryLower).forEach { setting ->
                        val settingLower = setting.lowercase()
                        val isDuplicate = settingLower == "launch settings" ||
                                         settingLower == "vault" ||
                                         settingLower == "launch"
                        if (!isDuplicate) {
                            newFilteredList.add(createSettingsOption(setting))
                        }
                    }

                    if (currentSearchMode == SearchMode.APPS) return@run
                }

                if (currentSearchMode == SearchMode.ALL || currentSearchMode == SearchMode.CONTACTS) {
                    contactsListSnapshot.asSequence()
                        .filter { it.contains(query, ignoreCase = true) }
                        .take(if (currentSearchMode == SearchMode.ALL) 5 else 20)
                        .forEach { contact ->
                            newFilteredList.add(createUnifiedContactOption(contact))
                        }

                    if (currentSearchMode == SearchMode.CONTACTS) return@run
                }

                if (currentSearchMode == SearchMode.ALL || currentSearchMode == SearchMode.FILES) {
                    getFileMatches(queryLower).forEach { file ->
                        newFilteredList.add(createFileOption(file))
                    }

                    if (currentSearchMode == SearchMode.FILES) return@run
                }

                if (currentSearchMode == SearchMode.MAPS) {
                    newFilteredList.add(createGoogleMapsSearchOption(query))
                    return@run
                }

                if (currentSearchMode == SearchMode.PLAYSTORE) {
                    newFilteredList.add(createPlayStoreSearchOption(query))
                    return@run
                }

                if (currentSearchMode == SearchMode.YOUTUBE) {
                    newFilteredList.add(createYoutubeSearchOption(query))
                    return@run
                }

                if (currentSearchMode == SearchMode.WEB) {
                    newFilteredList.add(createBrowserSearchOption(query))
                    return@run
                }

                if (currentSearchMode == SearchMode.ALL) {
                    if (!(isFocusModeActive?.invoke() == true)) {
                        newFilteredList.add(createGoogleMapsSearchOption(query))
                        newFilteredList.add(createPlayStoreSearchOption(query))
                        newFilteredList.add(createYoutubeSearchOption(query))
                    }
                    newFilteredList.add(createBrowserSearchOption(query))
                }
            }
        } else {
            when (currentSearchMode) {
                SearchMode.ALL -> newFilteredList.addAll(homeAppListSnapshot)
                SearchMode.APPS -> newFilteredList.addAll(homeAppListSnapshot)
                SearchMode.CONTACTS -> {
                    contactsListSnapshot.forEach { contact ->
                        newFilteredList.add(createUnifiedContactOption(contact))
                    }
                }
                SearchMode.FILES -> {}
                SearchMode.MAPS -> newFilteredList.add(createGoogleMapsSearchOption(""))
                SearchMode.WEB -> newFilteredList.add(createBrowserSearchOption(""))
                SearchMode.PLAYSTORE -> newFilteredList.add(createPlayStoreSearchOption(""))
                SearchMode.YOUTUBE -> newFilteredList.add(createYoutubeSearchOption(""))
            }
        }
        return newFilteredList
    }


    private val cachedResolveInfos = object : LinkedHashMap<String, ResolveInfo>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ResolveInfo>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 100
    }

    private fun evaluateMathExpression(expression: String): String? {
        return try {
            val result = ExpressionBuilder(expression).build().evaluate()
            result.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun getSettingsMatches(query: String): List<String> {
        return AndroidSettingsHelper.searchSettings(query).take(8).map { it.title }
    }

    private fun createSettingsOption(setting: String): ResolveInfo {
        val systemSetting = AndroidSettingsHelper.getAllSystemSettings().find { it.title == setting }

        return cachedResolveInfos.getOrPut("settings_result_$setting") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "system_settings_result"
                    name = setting
                    if (systemSetting != null) {
                        nonLocalizedLabel = systemSetting.action
                    }
                }
            }
        }
    }

    private fun getFileMatches(query: String): List<File> {
        val results = mutableListOf<File>()
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Files.FileColumns.DATA,
                android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME
            )

            val selection = "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")

            val queryUri = android.provider.MediaStore.Files.getContentUri("external")

            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATA)
                while (cursor.moveToNext() && results.size < 10) {
                    val path = cursor.getString(dataColumn)
                    if (path != null) {
                        val file = File(path)
                        if (file.exists() && !file.isDirectory) {
                            results.add(file)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            try {
                val dirsToSearch = listOf(
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_DOCUMENTS,
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_PICTURES
                )
                for (dirName in dirsToSearch) {
                    val dir = Environment.getExternalStoragePublicDirectory(dirName)
                    if (dir.exists() && dir.isDirectory) {
                        searchFilesRecursively(dir, query, results, 0)
                        if (results.size >= 10) break
                    }
                }
            } catch (_: Exception) {}
        }
        return results
    }

    private fun searchFilesRecursively(dir: File, query: String, results: MutableList<File>, depth: Int) {
        if (depth > 2 || results.size >= 10) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                searchFilesRecursively(file, query, results, depth + 1)
            } else if (file.name.contains(query, ignoreCase = true)) {
                results.add(file)
            }
            if (results.size >= 10) return
        }
    }

    private fun createFileOption(file: File): ResolveInfo {
        return cachedResolveInfos.getOrPut("file_result_${file.absolutePath}") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "file_result"
                    name = file.name
                    nonLocalizedLabel = file.absolutePath
                }
            }
        }
    }

    private fun createMathResultOption(expression: String, result: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("math_result_$expression") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "math_result"
                    name = "$expression = $result"
                }
            }
        }
    }

    private fun createUnifiedContactOption(contact: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("contact_unified_$contact") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "contact_unified"
                    name = contact
                }
            }
        }
    }

    private fun createGoogleMapsSearchOption(query: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("maps_search_$query") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "maps_search"
                    name = query
                }
            }
        }
    }

    private fun createYoutubeSearchOption(query: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("yt_search_$query") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "yt_search"
                    name = query
                }
            }
        }
    }

    private fun createPlayStoreSearchOption(query: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("play_store_search_$query") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "play_store_search"
                    name = query
                }
            }
        }
    }

    private fun createBrowserSearchOption(query: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("browser_search_$query") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "browser_search"
                    name = query
                }
            }
        }
    }

    fun cleanup() {
        searchJob?.cancel()
    }
}
