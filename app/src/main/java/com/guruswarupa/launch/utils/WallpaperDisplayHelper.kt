package com.guruswarupa.launch.utils

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.ThemeOption

object WallpaperDisplayHelper {

    private fun isMinimalMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.Prefs.MINIMAL_MODE_ENABLED, false)
    }

    fun applySystemWallpaper(target: ImageView, fallbackRes: Int = R.drawable.wallpaper_background) {
        val context = target.context
        if (isMinimalMode(context)) {
            target.setImageDrawable(null)
            target.setBackgroundColor(Color.BLACK)
            return
        }

        val wallpaperDrawable = try {
            WallpaperManager.getInstance(context).drawable
        } catch (_: Exception) {
            null
        }

        if (wallpaperDrawable != null) {
            target.setImageDrawable(wallpaperDrawable)
        } else {
            target.setImageResource(fallbackRes)
        }
    }

    fun applyThemeWallpaper(target: ImageView, themeId: String, fallbackRes: Int = R.drawable.wallpaper_background) {
        val context = target.context
        if (isMinimalMode(context)) {
            target.setImageDrawable(null)
            target.setBackgroundColor(Color.BLACK)
            return
        }

        val theme = ThemeOption.PREDEFINED_THEMES.find { it.id == themeId }

        if (theme != null) {
            val options = RequestOptions()
                .format(DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(Target.SIZE_ORIGINAL)
                .centerCrop()

            Glide.with(context)
                .asDrawable()
                .load(theme.wallpaperUrl)
                .apply(options)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.wallpaper_background)
                .error(fallbackRes)
                .into(target)
        } else {
            applySystemWallpaper(target, fallbackRes)
        }
    }

    fun applyThemePreview(target: ImageView, themeId: String) {
        val context = target.context
        if (isMinimalMode(context)) {
            target.setImageDrawable(null)
            target.setBackgroundColor(Color.BLACK)
            return
        }

        if (themeId == "system_default") {
            applySystemWallpaper(target)
        } else {
            val theme = ThemeOption.PREDEFINED_THEMES.find { it.id == themeId }
            if (theme != null) {
                Glide.with(context)
                    .load(theme.wallpaperUrl)
                    .placeholder(R.drawable.wallpaper_background)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(target)
            }
        }
    }
}
