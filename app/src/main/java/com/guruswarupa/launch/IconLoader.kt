package com.guruswarupa.launch

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
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
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
import kotlin.time.Duration.Companion.milliseconds

class IconLoader(
    private val activity: MainActivity,
    private val context: Context,
    private val separatorPackage: String,
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
            recycleDrawableBitmap()
        }
    }

    private val specialAppIconCache = object : LruCache<String, Drawable>(maxCacheSize / 2) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Drawable, newValue: Drawable?) {
            recycleDrawableBitmap()
        }
    }

    private val contactPhotoCache = object : LruCache<String, Drawable>(maxCacheSize / 2) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Drawable, newValue: Drawable?) {
            recycleDrawableBitmap()
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

        currentIconPackPackage = IconPackManager.getSelectedIconPack(sharedPreferences)

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
                ResourcesCompat.getDrawable(iconPackResources, resourceId, context.theme)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun recycleDrawableBitmap() {

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

        if (oldStyle != style) {
            clearIconCaches(clearDiskCache = true)
        } else {
            clearIconCaches(clearDiskCache = false)
        }

        cacheManager.setIconCacheVersion(cacheManager.getIconCacheKey(currentIconStyle, currentIconSize))
    }

    fun updateIconSize(size: Int) {
        val oldSize = currentIconSize
        currentIconSize = size

        if (oldSize != size) {
            clearIconCaches(clearDiskCache = true)
        } else {
            clearIconCaches(clearDiskCache = false)
        }

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

    fun invalidatePackage(packageName: String) {
        val prefix = "$packageName|"
        val staleKeys = iconCache.snapshot().keys.filter { it.startsWith(prefix) }
        staleKeys.forEach { iconCache.remove(it) }
        val staleSpecialKeys = specialAppIconCache.snapshot().keys.filter { it.startsWith(prefix) }
        staleSpecialKeys.forEach { specialAppIconCache.remove(it) }
        cacheManager.removeIconsForPackage(packageName)
    }

    fun cleanup() {

        pendingIconTasks.values.forEach { it.cancel(mayInterruptIfRunning = true) }
        pendingIconTasks.clear()

        iconLoadExecutor.shutdown()
        iconPreloadExecutor.shutdown()

        iconLoadScope.cancel()

        iconCache.evictAll()
        specialAppIconCache.evictAll()
        contactPhotoCache.evictAll()
    }

    fun getCachedIcon(cacheKey: String): Drawable? {

        return iconCache[cacheKey]
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

    fun setIconDrawable(imageView: ImageView?, drawable: Drawable?) {
        imageView?.setImageDrawable(drawable?.let { shapeIconDrawable(it) })
    }

    fun setIconResource(imageView: ImageView?, resId: Int) {
        setIconDrawable(imageView, ContextCompat.getDrawable(context, resId))
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
                delay(Constants.Timeouts.ICON_PRELOAD_DELAY_MS.milliseconds)
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
        val cachedIcon = iconCache[cacheKey]
        if (cachedIcon != null) {
            if (holder?.appIcon != null) {
                updateHolderIcon(holder, cacheKey, cachedIcon, onIconReady)
            }
            return
        }

        pendingIconTasks.remove(cacheKey)?.cancel(mayInterruptIfRunning = true)
        val priorityRunnable = PriorityRunnable(priority) {
            try {
                if (iconCache[cacheKey] == null) {

                    var shapedIcon: Drawable? = null
                    if (cacheManager.isIconCacheValid(currentIconStyle, currentIconSize)) {
                        shapedIcon = cacheManager.getCachedIcon(cacheKey, cacheManager.getIconCacheKey(currentIconStyle, currentIconSize))
                    }

                    if (shapedIcon == null) {

                        var icon = loadIconFromPack(packageName, app.activityInfo.name)

                        if (icon == null) {
                            icon = app.loadIcon(activity.packageManager)
                        }

                        if (app.preferredOrder != mainUserSerial) {
                            val userHandle = userManager.getUserForSerialNumber(app.preferredOrder.toLong())
                            if (userHandle != null) {
                                icon = activity.packageManager.getUserBadgedIcon(icon, userHandle)
                            }
                        }
                        shapedIcon = shapeIconDrawable(icon)

                        cacheManager.cacheIcon(cacheKey, shapedIcon, cacheManager.getIconCacheKey(currentIconStyle, currentIconSize))
                    }

                    iconCache.put(cacheKey, shapedIcon)
                }

                val readyIcon = iconCache[cacheKey] ?: return@PriorityRunnable
                if (holder != null) {
                    updateHolderIcon(holder, cacheKey, readyIcon, onIconReady)
                }
            } catch (e: Exception) {
            }
        }

        val trackedTask = TrackedTask(priorityRunnable)
        try {
            iconPreloadExecutor.execute(trackedTask)
            pendingIconTasks[cacheKey] = trackedTask
        } catch (e: java.util.concurrent.RejectedExecutionException) {
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

        val cachedIcon = specialAppIconCache[cacheId]
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
                    }
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
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

        val cachedPhoto = contactPhotoCache[contactName]
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
                    val targetSizePx = (currentIconSize * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    val drawable = decodeSampledContactPhoto(photoUri, targetSizePx) ?: return@execute
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
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
        }
    }

    private fun decodeSampledContactPhoto(uriString: String, targetSizePx: Int): Drawable? {
        val uri = uriString.toUri()
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        activity.contentResolver.openInputStream(uri)?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        var sampleSize = 1
        if (bounds.outWidth > targetSizePx || bounds.outHeight > targetSizePx) {
            val halfWidth = bounds.outWidth / 2
            val halfHeight = bounds.outHeight / 2
            while ((halfWidth / sampleSize) >= targetSizePx && (halfHeight / sampleSize) >= targetSizePx) {
                sampleSize *= 2
            }
        }

        val decodeOptions = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = activity.contentResolver.openInputStream(uri)?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null
        return BitmapDrawable(context.resources, bitmap)
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

    private fun shapeIconDrawable(drawable: Drawable): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (currentIconSize * density).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bounds = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val path = createIconMaskPath(bounds)

        bitmap.applyCanvas {
            save()
            clipPath(path)

            if (drawable is AdaptiveIconDrawable) {
                val background = drawable.background?.constantState?.newDrawable(context.resources)?.mutate()
                    ?: drawable.background?.mutate()
                val foreground = drawable.foreground?.constantState?.newDrawable(context.resources)?.mutate()
                    ?: drawable.foreground?.mutate()
                background?.setBounds(0, 0, size, size)
                foreground?.setBounds(0, 0, size, size)
                background?.draw(this)
                foreground?.draw(this)
            } else {
                val copy = drawable.constantState?.newDrawable(context.resources)?.mutate() ?: drawable.mutate()
                copy.setBounds(0, 0, size, size)
                copy.draw(this)
            }

            restore()
        }
        return bitmap.toDrawable(context.resources)
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
