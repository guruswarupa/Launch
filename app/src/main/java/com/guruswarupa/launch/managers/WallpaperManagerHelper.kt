package com.guruswarupa.launch.managers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import java.util.concurrent.RejectedExecutionException




class WallpaperManagerHelper(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val wallpaperBackground: ImageView,
    private val drawerWallpaperBackground: ImageView?,
    private val backgroundExecutor: java.util.concurrent.ExecutorService,
    private val rssWallpaperBackground: ImageView? = null,
    private val aiChatWallpaperBackground: ImageView? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentWallpaperBitmap: Bitmap? = null
    private var lastWallpaperId: Int = -1




    fun setWallpaperBackground(forceReload: Boolean = false) {
        val prefs = activity.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Constants.Prefs.MINIMAL_MODE_ENABLED, false)) {
            wallpaperBackground.setImageDrawable(null)
            wallpaperBackground.setBackgroundColor(Color.BLACK)
            drawerWallpaperBackground?.setImageDrawable(null)
            drawerWallpaperBackground?.setBackgroundColor(Color.BLACK)
            rssWallpaperBackground?.setImageDrawable(null)
            rssWallpaperBackground?.setBackgroundColor(Color.BLACK)
            aiChatWallpaperBackground?.setImageDrawable(null)
            aiChatWallpaperBackground?.setBackgroundColor(Color.BLACK)
            return
        }

        applyBlurToViews()

        val wallpaperManager = WallpaperManager.getInstance(activity)
        val wallpaperId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        } else {
            -1
        }


        val bitmap = currentWallpaperBitmap
        val needsReload = forceReload ||
                         bitmap == null ||
                         bitmap.isRecycled ||
                         (wallpaperId != -1 && wallpaperId != lastWallpaperId)

        if (!needsReload) {
            return
        }

        lastWallpaperId = wallpaperId


        if ((backgroundExecutor as? java.util.concurrent.ExecutorService)?.isShutdown == true) {
            Log.w("WallpaperManagerHelper", "Background executor is shut down, skipping wallpaper load")
            setDefaultWallpaper()
            return
        }

        try {
            backgroundExecutor.execute {
            try {
                val drawable = wallpaperManager.drawable

                if (drawable == null) {
                    handler.post { setDefaultWallpaper() }
                    return@execute
                }


                val bitmap = if (drawable is BitmapDrawable) {
                    val sourceBitmap = drawable.bitmap
                    if (sourceBitmap != null && !sourceBitmap.isRecycled) {

                        sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        null
                    }
                } else {

                    val metrics = activity.resources.displayMetrics
                    val width = metrics.widthPixels / 2
                    val height = metrics.heightPixels / 2

                    val bm = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
                    val canvas = android.graphics.Canvas(bm)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bm
                }

                if (bitmap != null && !bitmap.isRecycled) {
                    handler.post {
                        updateWallpaperViews(bitmap)
                    }
                } else {
                    handler.post { setDefaultWallpaper() }
                }
            } catch (e: SecurityException) {
                Log.w("WallpaperManagerHelper", "No permission to read wallpaper", e)
                handler.post { setDefaultWallpaper() }
            } catch (e: Exception) {
                Log.e("WallpaperManagerHelper", "Error loading wallpaper", e)
                handler.post { setDefaultWallpaper() }
            }
        }
        } catch (e: RejectedExecutionException) {
            Log.w("WallpaperManagerHelper", "Wallpaper load task rejected", e)
            setDefaultWallpaper()
        }
    }

    private fun updateWallpaperViews(newBitmap: Bitmap) {
        val oldBitmap = currentWallpaperBitmap

        currentWallpaperBitmap = newBitmap
        if (!newBitmap.isRecycled) {
            wallpaperBackground.setBackgroundColor(Color.TRANSPARENT)
            wallpaperBackground.setImageBitmap(newBitmap)
            drawerWallpaperBackground?.setBackgroundColor(Color.TRANSPARENT)
            drawerWallpaperBackground?.setImageBitmap(newBitmap)
            rssWallpaperBackground?.setBackgroundColor(Color.TRANSPARENT)
            rssWallpaperBackground?.setImageBitmap(newBitmap)
            aiChatWallpaperBackground?.setBackgroundColor(Color.TRANSPARENT)
            aiChatWallpaperBackground?.setImageBitmap(newBitmap)
        }
    }

    private fun setDefaultWallpaper() {
        val prefs = activity.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Constants.Prefs.MINIMAL_MODE_ENABLED, false)) {
            wallpaperBackground.setImageDrawable(null)
            wallpaperBackground.setBackgroundColor(Color.BLACK)
            drawerWallpaperBackground?.setImageDrawable(null)
            drawerWallpaperBackground?.setBackgroundColor(Color.BLACK)
            rssWallpaperBackground?.setImageDrawable(null)
            rssWallpaperBackground?.setBackgroundColor(Color.BLACK)
            aiChatWallpaperBackground?.setImageDrawable(null)
            aiChatWallpaperBackground?.setBackgroundColor(Color.BLACK)
            return
        }

        currentWallpaperBitmap = null

        wallpaperBackground.setBackgroundColor(Color.TRANSPARENT)
        wallpaperBackground.setImageResource(R.drawable.wallpaper_background)
        drawerWallpaperBackground?.setBackgroundColor(Color.TRANSPARENT)
        drawerWallpaperBackground?.setImageResource(R.drawable.wallpaper_background)
        rssWallpaperBackground?.setBackgroundColor(Color.TRANSPARENT)
        rssWallpaperBackground?.setImageResource(R.drawable.wallpaper_background)
        aiChatWallpaperBackground?.setBackgroundColor(Color.TRANSPARENT)
        aiChatWallpaperBackground?.setImageResource(R.drawable.wallpaper_background)
    }




    fun applyBlurToViews() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wallpaperBackground.setRenderEffect(null)
            drawerWallpaperBackground?.setRenderEffect(null)
            rssWallpaperBackground?.setRenderEffect(null)
            aiChatWallpaperBackground?.setRenderEffect(null)
            activity.findViewById<ImageView>(R.id.right_drawer_wallpaper)?.setRenderEffect(null)
        }
    }




    fun clearCache() {


        currentWallpaperBitmap = null
        lastWallpaperId = -1



    }




    fun cleanup() {
        wallpaperBackground.setImageDrawable(null)
        drawerWallpaperBackground?.setImageDrawable(null)
        rssWallpaperBackground?.setImageDrawable(null)
        aiChatWallpaperBackground?.setImageDrawable(null)



        currentWallpaperBitmap = null
        lastWallpaperId = -1
    }
}
