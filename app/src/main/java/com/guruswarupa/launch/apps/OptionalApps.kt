package com.guruswarupa.launch.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.guruswarupa.launch.R

/** The apps that ship inside Launch. Each one can be switched off, which hides it from the app list. */
data class SuiteApp(
    val id: String,
    /** Simple class name of the launcher activity, used to match entries in the app list. */
    val activity: String,
    val labelRes: Int,
    val descRes: Int,
    val iconRes: Int,
    val className: String
)

object OptionalApps {
    private const val PREFS = "optional_apps"

    val all = listOf(
        SuiteApp("files", "FilesActivity", R.string.app_files, R.string.suite_files_desc, R.drawable.ic_app_files, "com.guruswarupa.launch.ui.activities.FilesActivity"),
        SuiteApp("camera", "CameraActivity", R.string.app_camera, R.string.suite_camera_desc, R.drawable.ic_app_camera, "com.guruswarupa.launch.ui.activities.CameraActivity"),
        SuiteApp("gallery", "GalleryActivity", R.string.app_gallery, R.string.suite_gallery_desc, R.drawable.ic_app_gallery, "com.guruswarupa.launch.ui.activities.GalleryActivity"),
        SuiteApp("player", "PlayerActivity", R.string.app_video_player, R.string.suite_player_desc, R.drawable.ic_app_player, "com.guruswarupa.launch.ui.activities.PlayerActivity"),
        SuiteApp("calculator", "CalculatorActivity", R.string.app_calculator, R.string.suite_calculator_desc, R.drawable.ic_app_calculator, "com.guruswarupa.launch.apps.calculator.CalculatorActivity"),
        SuiteApp("calendar", "CalendarActivity", R.string.app_calendar, R.string.suite_calendar_desc, R.drawable.ic_app_calendar, "com.guruswarupa.launch.apps.calendar.CalendarActivity"),
        SuiteApp("clock", "ClockActivity", R.string.app_clock, R.string.suite_clock_desc, R.drawable.ic_app_clock, "com.guruswarupa.launch.apps.clock.ClockActivity")
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun disabled(ctx: Context): Set<String> = prefs(ctx).getStringSet("disabled", emptySet()) ?: emptySet()

    /** Apps are on by default so nothing disappears for existing users. */
    fun isEnabled(ctx: Context, id: String) = id !in disabled(ctx)

    fun isActivityEnabled(ctx: Context, activityClassName: String): Boolean {
        val app = all.firstOrNull { activityClassName.endsWith(it.activity) } ?: return true
        return isEnabled(ctx, app.id)
    }

    fun setEnabled(ctx: Context, id: String, enabled: Boolean) {
        val set = disabled(ctx).toMutableSet()
        if (enabled) set.remove(id) else set.add(id)
        prefs(ctx).edit().putStringSet("disabled", set).putLong("rev", System.currentTimeMillis()).putBoolean("chosen", true).apply()
    }

    fun hasChosen(ctx: Context) = prefs(ctx).getBoolean("chosen", false)

    /** Changes whenever the selection changes, so the home screen knows to rebuild its app list. */
    fun revision(ctx: Context): Long = prefs(ctx).getLong("rev", 0L)

    fun launchIntent(app: SuiteApp, ctx: Context): Intent =
        Intent().setComponent(ComponentName(ctx.packageName, app.className)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
