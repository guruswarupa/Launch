package com.guruswarupa.launch

import android.app.Activity
import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserManager
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.CornerSize
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.utils.IconPackManager
import com.guruswarupa.launch.core.CacheManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class IconLoader(
    private val activity: MainActivity,
    private val context: Context,
    private val separatorPackage: String,
    private val specialPackageNames: Set<String>,
    private val sharedPreferences: android.content.SharedPreferences,
    private val cacheManager: CacheManager
) {
    companion object {
        const val PRIORITY_HIGH = 100
        const val PRIORITY_MEDIUM = 50
        const val PRIORITY_LOW = 10
        const val PRIORITY_BACKGROUND = 0
    }

    private fun safeRunOnUiThread(block: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        activity.runOnUiThread(block)
    }

    private val maxCacheSize = Constants.Dimensions.ICON_CACHE_MAX_SIZE
    private val iconCache = object : LruCache<String, Drawable>(maxCacheSize) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Drawable, newValue: Drawable?) {
            recycleDrawableBitmap(oldValue)
        }
    }

    private val specialAppIconCache = object : LruCache<String, Drawable>(maxCacheSize / 2) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Drawable, newValue: Drawable?) {
            recycleDrawableBitmap(oldValue)
        }
    }

    private val contactPhotoCache = object : LruCache<String, Drawable>(maxCacheSize / 2) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Drawable, newValue: Drawable?) {
            recycleDrawableBitmap(oldValue)
        }
    }
    private val pendingIconTasks = ConcurrentHashMap<String, TrackedTask>()

    private val iconLoadExecutor = ThreadPoolExecutor(
        3, 6, 60L, TimeUnit.SECONDS,
        LinkedBlockingQueue()
    )
    private val iconPreloadExecutor = ThreadPoolExecutor(
        2, 4, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue()
    )
    private val iconLoadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val mainUserSerial = userManager.getSerialNumberForUser(Process.myUserHandle()).toInt()

    var currentIconStyle: String = sharedPreferences.getString(Constants.Prefs.ICON_STYLE, "squircle") ?: "round"
        private set

    var currentIconSize: Int = sharedPreferences.getInt(Constants.Prefs.ICON_SIZE, 40)
        private set
    
    private var currentIconPackPackage: String? = null

    init {
        // Initialize current icon pack package
        currentIconPackPackage = IconPackManager.getSelectedIconPack(sharedPreferences)
        
        // Set icon cache version if not already set
        if (!cacheManager.isIconCacheValid(currentIconStyle, currentIconSize)) {
            cacheManager.setIconCacheVersion(cacheManager.getIconCacheKey(currentIconStyle, currentIconSize))
        }
    }

    private fun loadIconFromPack(packageName: String, activityName: String? = null): Drawable? {
        val iconPackPackage = IconPackManager.getSelectedIconPack(sharedPreferences)
        val isIconPackEnabled = IconPackManager.isIconPackEnabled(sharedPreferences)
        
        if (!isIconPackEnabled || iconPackPackage == null) return null
        
        return try {
            val pm = context.packageManager
            val iconPackResources = pm.getResourcesForApplication(iconPackPackage)
            
            val drawableName = IconPackManager.getDrawableName(context, packageName, activityName, sharedPreferences)
            var resourceId = 0
            if (drawableName != null) {
                resourceId = iconPackResources.getIdentifier(drawableName, "drawable", iconPackPackage)
            }
            
            if (resourceId == 0) {
                resourceId = iconPackResources.getIdentifier(
                    packageName.replace(".", "_").lowercase(),
                    "drawable",
                    iconPackPackage
                )
            }
            
            if (resourceId != 0) {
                iconPackResources.getDrawable(resourceId, context.theme)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("IconLoader", "Failed to load icon from pack for $packageName", e)
            null
        }
    }

    private fun recycleDrawableBitmap(drawable: Drawable?) {
        // Don't manually recycle bitmaps from cache eviction.
        // The drawable might still be in use by an ImageView.
        // Let the system's garbage collector handle bitmap memory.
        // Manual recycling here causes "Canvas: trying to use a recycled bitmap" crashes.
    }

    private class PriorityRunnable(val priority: Int, val action: Runnable) : Runnable, Comparable<PriorityRunnable> {
        override fun run() = action.run()
        override fun compareTo(other: PriorityRunnable): Int = other.priority.compareTo(this.priority)
    }

    private class TrackedTask(
        private val priorityRunnable: PriorityRunnable
    ) : Runnable, Comparable<TrackedTask>, Future<Boolean> {
        @Volatile private var isDone = false
        @Volatile private var isCancelled = false

        override fun run() {
            try {
                priorityRunnable.run()
            } finally {
                isDone = true
            }
        }

        override fun compareTo(other: TrackedTask): Int = priorityRunnable.compareTo(other.priorityRunnable)

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

    fun updateIconStyle(style: String) {
        val oldStyle = currentIconStyle
        currentIconStyle = style
        
        // Only clear disk cache if style actually changed
        if (oldStyle != style) {
            clearIconCaches(clearDiskCache = true)
        } else {
            clearIconCaches(clearDiskCache = false)
        }
        
        // Update disk cache version
        cacheManager.setIconCacheVersion(cacheManager.getIconCacheKey(currentIconStyle, currentIconSize))
    }

    fun updateIconSize(size: Int) {
        val oldSize = currentIconSize
        currentIconSize = size
        
        // Only clear disk cache if size actually changed
        if (oldSize != size) {
            clearIconCaches(clearDiskCache = true)
        } else {
            clearIconCaches(clearDiskCache = false)
        }
        
        // Update disk cache version
        cacheManager.setIconCacheVersion(cacheManager.getIconCacheKey(currentIconStyle, currentIconSize))
    }

    fun clearIconCaches(clearDiskCache: Boolean = false) {
        iconCache.evictAll()
        specialAppIconCache.evictAll()
        if (clearDiskCache) {
            cacheManager.clearIconCache()
        }
    }
    
    fun onIconPackChanged() {
        val iconPackPackage = IconPackManager.getSelectedIconPack(sharedPreferences)
        val isIconPackEnabled = IconPackManager.isIconPackEnabled(sharedPreferences)
        
        // Clear disk cache if icon pack changed or icon pack enabled/disabled
        if (currentIconPackPackage != iconPackPackage || 
            (currentIconPackPackage == null && isIconPackEnabled) ||
            (currentIconPackPackage != null && !isIconPackEnabled)) {
            clearIconCaches(clearDiskCache = true)
            currentIconPackPackage = iconPackPackage
        }
    }

    fun clearContactPhotoCache() {
        contactPhotoCache.evictAll()
    }

    fun cleanup() {
        // Cancel all pending tasks
        pendingIconTasks.values.forEach { it.cancel(true) }
        pendingIconTasks.clear()
        
        // Shutdown executors
        iconLoadExecutor.shutdown()
        iconPreloadExecutor.shutdown()
        
        // Cancel coroutine scope
        iconLoadScope.cancel()
        
        // Clear caches
        iconCache.evictAll()
        specialAppIconCache.evictAll()
        contactPhotoCache.evictAll()
    }

    fun getCachedIcon(cacheKey: String): Drawable? {
        // Check in-memory cache first
        val memCached = iconCache.get(cacheKey)
        if (memCached != null) {
            return memCached
        }

        // Check disk cache if in-memory cache miss
        if (cacheManager.isIconCacheValid(currentIconStyle, currentIconSize)) {
            val version = cacheManager.getIconCacheKey(currentIconStyle, currentIconSize)
            val diskCached = cacheManager.getCachedIcon(cacheKey, version)
            if (diskCached != null) {
                // Put into in-memory cache for faster access next time
                iconCache.put(cacheKey, diskCached)
                return diskCached
            }
        }

        return null
    }

    fun getShapeAppearanceModel(): ShapeAppearanceModel {
        val density = context.resources.displayMetrics.density
        val builder = ShapeAppearanceModel.builder()

        when (currentIconStyle) {
            "round" -> builder.setAllCornerSizes(RelativeCornerSize(0.5f))
            "squircle" -> builder.setAllCornerSizes(RelativeCornerSize(0.28f))
            "squared" -> builder.setAllCornerSizes(RelativeCornerSize(0.08f))
            "teardrop" -> {
                builder.setTopLeftCornerSize(RelativeCornerSize(0.5f))
                builder.setTopRightCornerSize(RelativeCornerSize(0.5f))
                builder.setBottomLeftCornerSize(RelativeCornerSize(0.5f))
                builder.setBottomRightCornerSize(RelativeCornerSize(0.18f))
            }
            "vortex" -> {
                builder.setAllCorners(CornerFamily.CUT, 0f)
                builder.setAllCornerSizes(RelativeCornerSize(0.2f))
            }
            "overlay" -> builder.setAllCornerSizes(RelativeCornerSize(0.18f))
            else -> builder.setAllCornerSizes(RelativeCornerSize(0.5f))
        }
        return builder.build()
    }

    fun applyShapeAppearance(imageView: ShapeableImageView?) {
        imageView?.shapeAppearanceModel = getShapeAppearanceModel()
        imageView?.invalidate()
    }

    fun updateIconSize(imageView: ImageView?) {
        val sizeInPx = (currentIconSize * context.resources.displayMetrics.density).toInt()
        val currentParams = imageView?.layoutParams ?: return
        if (currentParams.width != sizeInPx || currentParams.height != sizeInPx) {
            currentParams.width = sizeInPx
            currentParams.height = sizeInPx
            imageView.layoutParams = currentParams
            imageView.requestLayout()
        }
    }

    fun setIconDrawable(imageView: ImageView?, drawable: Drawable?, useLegacyIconPlate: Boolean = false) {
        imageView?.setImageDrawable(drawable?.let { shapeIconDrawable(it, useLegacyIconPlate) })
    }

    fun setIconResource(imageView: ImageView?, resId: Int, useLegacyIconPlate: Boolean = false) {
        setIconDrawable(imageView, ContextCompat.getDrawable(context, resId), useLegacyIconPlate)
    }

    fun preloadIcons(apps: List<ResolveInfo>) {
        val immediateLoad = apps.take(Constants.Dimensions.ICON_IMMEDIATE_LOAD_COUNT)
        for (app in immediateLoad) {
            submitIconLoadTask(app, PRIORITY_LOW)
        }

        val remainingApps = apps.drop(Constants.Dimensions.ICON_IMMEDIATE_LOAD_COUNT)
        iconLoadScope.launch {
            for (batch in remainingApps.chunked(Constants.Dimensions.ICON_PRELOAD_BATCH_SIZE)) {
                for (app in batch) {
                    submitIconLoadTask(app, PRIORITY_BACKGROUND)
                }
                delay(Constants.Timeouts.ICON_PRELOAD_DELAY_MS)
            }
        }
    }

    fun preloadNextIcons(appList: List<ResolveInfo>, startPosition: Int, endPosition: Int) {
        val size = appList.size
        if (startPosition >= size) return
        val appsToPreload = try {
            ArrayList(appList.subList(startPosition, minOf(endPosition, size)))
        } catch (_: Exception) {
            return
        }
        for (app in appsToPreload) {
            if (app.activityInfo.packageName != separatorPackage) {
                submitIconLoadTask(app, PRIORITY_MEDIUM)
            }
        }
    }

    fun submitIconLoadTask(
        app: ResolveInfo,
        priority: Int,
        holder: AppAdapter.ViewHolder? = null,
        onIconReady: ((String, AppAdapter.ViewHolder) -> Unit)? = null
    ) {
        if (iconPreloadExecutor.isShutdown || iconPreloadExecutor.isTerminated) return
        
        val packageName = app.activityInfo.packageName
        if (packageName == separatorPackage) return

        val cacheKey = "${packageName}|${app.preferredOrder}"
        val cachedIcon = iconCache.get(cacheKey)
        if (packageName in specialPackageNames || cachedIcon != null) {
            if (cachedIcon != null && holder?.appIcon != null) {
                updateHolderIcon(holder, cacheKey, cachedIcon, onIconReady)
            }
            return
        }

        pendingIconTasks.remove(cacheKey)?.cancel(true)
        val priorityRunnable = PriorityRunnable(priority) {
            try {
                if (iconCache.get(cacheKey) == null) {
                    // Try to load from icon pack first
                    var icon = loadIconFromPack(packageName, app.activityInfo.name)
                    
                    // Fallback to default icon if pack doesn't have it
                    if (icon == null) {
                        icon = app.loadIcon(activity.packageManager)
                    }
                    
                    if (app.preferredOrder != mainUserSerial) {
                        val userHandle = userManager.getUserForSerialNumber(app.preferredOrder.toLong())
                        if (userHandle != null) {
                            icon = activity.packageManager.getUserBadgedIcon(icon, userHandle)
                        }
                    }
                    val shapedIcon = shapeIconDrawable(icon, useLegacyIconPlate = true)
                    iconCache.put(cacheKey, shapedIcon)
                    
                    // Save to disk cache for persistence
                    cacheManager.cacheIcon(cacheKey, shapedIcon, cacheManager.getIconCacheKey(currentIconStyle, currentIconSize))
                }

                val readyIcon = iconCache.get(cacheKey) ?: return@PriorityRunnable
                if (holder != null) {
                    updateHolderIcon(holder, cacheKey, readyIcon, onIconReady)
                }
            } catch (e: Exception) {
                android.util.Log.w("IconLoader", "Error loading icon for $packageName", e)
            }
        }

        val trackedTask = TrackedTask(priorityRunnable)
        try {
            iconPreloadExecutor.execute(trackedTask)
            pendingIconTasks[cacheKey] = trackedTask
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            android.util.Log.e("IconLoader", "Failed to submit icon task", e)
        }
        
        if (pendingIconTasks.size > Constants.Dimensions.PENDING_TASKS_CLEANUP_THRESHOLD) {
            pendingIconTasks.entries.removeIf { it.value.isDone }
        }
    }

    fun loadSpecialAppIcon(
        holder: AppAdapter.ViewHolder,
        cacheKey: String,
        cacheId: String,
        fallbackResId: Int,
        candidatePackages: List<String>,
        onLoaded: (() -> Unit)? = null
    ) {
        if (iconLoadExecutor.isShutdown || iconLoadExecutor.isTerminated) return
        
        val cachedIcon = specialAppIconCache.get(cacheId)
        if (cachedIcon != null) {

            if (!(cachedIcon is BitmapDrawable && cachedIcon.bitmap.isRecycled)) {
                holder.appIcon?.setImageDrawable(cachedIcon)
                onLoaded?.invoke()
            }
            return
        }

        setIconResource(holder.appIcon, fallbackResId)
        try {
            iconLoadExecutor.execute {
                for (candidatePackage in candidatePackages) {
                    try {
                        val icon = shapeIconDrawable(activity.packageManager.getApplicationIcon(candidatePackage))
                        specialAppIconCache.put(cacheId, icon)
                        safeRunOnUiThread {
                            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION && holder.itemView.tag == cacheKey) {

                                if (!(icon is BitmapDrawable && icon.bitmap.isRecycled)) {
                                    holder.appIcon?.setImageDrawable(icon)
                                    onLoaded?.invoke()
                                }
                            }
                        }
                        return@execute
                    } catch (e: Exception) {
                        android.util.Log.w("IconLoader", "Error loading special app icon for $candidatePackage", e)
                    }
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            android.util.Log.e("IconLoader", "Failed to submit special icon task", e)
        }
    }

    fun loadContactPhoto(
        holder: AppAdapter.ViewHolder,
        cacheKey: String,
        contactName: String,
        fallbackResId: Int,
        getPhotoUriForContact: (String) -> String?,
        onLoaded: (() -> Unit)? = null
    ) {
        if (iconLoadExecutor.isShutdown || iconLoadExecutor.isTerminated) return

        val cachedPhoto = contactPhotoCache.get(contactName)
        if (cachedPhoto != null) {

            if (!(cachedPhoto is BitmapDrawable && cachedPhoto.bitmap.isRecycled)) {
                setIconDrawable(holder.appIcon, cachedPhoto)
                onLoaded?.invoke()
            }
            return
        }

        setIconResource(holder.appIcon, fallbackResId)
        try {
            iconLoadExecutor.execute {
                try {
                    val photoUri = getPhotoUriForContact(contactName) ?: return@execute
                    val drawable = activity.contentResolver.openInputStream(photoUri.toUri())?.use { inputStream ->
                        Drawable.createFromStream(inputStream, photoUri)
                    } ?: return@execute
                    contactPhotoCache.put(contactName, drawable)
                    safeRunOnUiThread {
                        if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION && holder.itemView.tag == cacheKey) {

                            if (!(drawable is BitmapDrawable && drawable.bitmap.isRecycled)) {
                                setIconDrawable(holder.appIcon, drawable)
                                onLoaded?.invoke()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("IconLoader", "Error loading contact photo for $contactName", e)
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            android.util.Log.e("IconLoader", "Failed to submit contact photo task", e)
        }
    }

    private fun updateHolderIcon(
        holder: AppAdapter.ViewHolder,
        cacheKey: String,
        drawable: Drawable,
        onIconReady: ((String, AppAdapter.ViewHolder) -> Unit)?
    ) {
        safeRunOnUiThread {
            val currentPosition = holder.bindingAdapterPosition
            val currentTag = holder.itemView.tag
            if (currentPosition != RecyclerView.NO_POSITION && currentTag == cacheKey) {
                if (drawable is BitmapDrawable && drawable.bitmap.isRecycled) {
                    return@safeRunOnUiThread
                }
                holder.appIcon?.setImageDrawable(drawable)
                onIconReady?.invoke(cacheKey.substringBefore('|'), holder)
            }
        }
    }

    private fun shapeIconDrawable(drawable: Drawable, useLegacyIconPlate: Boolean = false): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (currentIconSize * density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val path = createIconMaskPath(bounds)

        canvas.save()
        canvas.clipPath(path)

        if (drawable is AdaptiveIconDrawable) {
            val background = drawable.background?.constantState?.newDrawable(context.resources)?.mutate()
                ?: drawable.background?.mutate()
            val foreground = drawable.foreground?.constantState?.newDrawable(context.resources)?.mutate()
                ?: drawable.foreground?.mutate()
            background?.setBounds(0, 0, size, size)
            foreground?.setBounds(0, 0, size, size)
            background?.draw(canvas)
            foreground?.draw(canvas)
        } else {
            val copy = drawable.constantState?.newDrawable(context.resources)?.mutate() ?: drawable.mutate()
            copy.setBounds(0, 0, size, size)
            copy.draw(canvas)
        }
        
        canvas.restore()
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun createIconMaskPath(bounds: RectF): Path {
        val width = bounds.width()
        val height = bounds.height()
        val minSize = minOf(width, height)
        val path = Path()

        when (currentIconStyle) {
            "round" -> path.addOval(bounds, Path.Direction.CW)
            "squircle" -> path.addRoundRect(bounds, minSize * 0.28f, minSize * 0.28f, Path.Direction.CW)
            "squared" -> path.addRoundRect(bounds, minSize * 0.08f, minSize * 0.08f, Path.Direction.CW)
            "teardrop" -> {
                path.addRoundRect(
                    bounds,
                    floatArrayOf(
                        minSize * 0.5f, minSize * 0.5f,
                        minSize * 0.5f, minSize * 0.5f,
                        minSize * 0.18f, minSize * 0.18f,
                        minSize * 0.5f, minSize * 0.5f
                    ),
                    Path.Direction.CW
                )
            }
            "vortex" -> {
                path.moveTo(bounds.left + minSize * 0.2f, bounds.top)
                path.lineTo(bounds.right - minSize * 0.2f, bounds.top)
                path.lineTo(bounds.right, bounds.top + minSize * 0.2f)
                path.lineTo(bounds.right, bounds.bottom - minSize * 0.2f)
                path.lineTo(bounds.right - minSize * 0.2f, bounds.bottom)
                path.lineTo(bounds.left + minSize * 0.2f, bounds.bottom)
                path.lineTo(bounds.left, bounds.bottom - minSize * 0.2f)
                path.lineTo(bounds.left, bounds.top + minSize * 0.2f)
                path.close()
            }
            "overlay" -> path.addRoundRect(bounds, minSize * 0.18f, minSize * 0.18f, Path.Direction.CW)
            else -> path.addOval(bounds, Path.Direction.CW)
        }

        return path
    }
}
