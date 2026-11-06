package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.theme.ThemeManager

class OnboardingTourActivity : AppCompatActivity() {

    private class TourAction(val labelRes: Int, val run: (AppCompatActivity) -> Unit)

    private class Perm(val icon: Int, val titleRes: Int, val whyRes: Int, val granted: () -> Boolean, val request: () -> Unit)

    private class Page(val icon: Int, val color: Int, val titleRes: Int, val bodyRes: Int, val actions: List<TourAction> = emptyList(), val perms: List<Perm>? = null)

    private var permissionsShown = false
    private val askedPrefs by lazy { getSharedPreferences("tour_permission_asks", MODE_PRIVATE) }

    private val runtimeLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPerms() }
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshPerms() }

    private fun refreshPerms() { if (::pager.isInitialized) pager.adapter?.notifyDataSetChanged() }

    private fun runtimePerm(icon: Int, title: Int, why: Int, permission: String, minSdk: Int = 0) = Perm(icon, title, why,
        granted = { Build.VERSION.SDK_INT < minSdk || ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED },
        request = {
            val askedBefore = askedPrefs.getBoolean(permission, false)
            if (askedBefore && !shouldShowRequestPermissionRationale(permission)) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } else {
                askedPrefs.edit { putBoolean(permission, true) }
                runtimeLauncher.launch(permission)
            }
        })

    private fun settingsPerm(icon: Int, title: Int, why: Int, granted: () -> Boolean, intent: () -> Intent) = Perm(icon, title, why, granted) {
        try { startActivity(intent()) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun flatContains(setting: String): Boolean =
        Settings.Secure.getString(contentResolver, setting)?.contains(packageName) == true

    private fun homePerm() = Perm(R.drawable.ic_apps_grid, R.string.perm_home_title, R.string.perm_home_why,
            granted = {
                val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                home?.activityInfo?.packageName == packageName
            },
            request = {
                if (Build.VERSION.SDK_INT >= 29) {
                    val rm = getSystemService(RoleManager::class.java)
                    if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    else startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } else startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            })

    private fun essentialPerms() = listOf(
        runtimePerm(R.drawable.ic_person, R.string.perm_contacts_title, R.string.perm_contacts_why, Manifest.permission.READ_CONTACTS),
        settingsPerm(R.drawable.ic_timer, R.string.perm_usage_title, R.string.perm_usage_why, {
            val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            mode == AppOpsManager.MODE_ALLOWED
        }) { Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
        settingsPerm(R.drawable.ic_notifications, R.string.perm_notif_access_title, R.string.perm_notif_access_why,
            { flatContains("enabled_notification_listeners") }) { Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
        runtimePerm(R.drawable.ic_alert_circle, R.string.perm_notifications_title, R.string.perm_notifications_why, "android.permission.POST_NOTIFICATIONS", 33)
    )

    private fun optionalPerms() = listOf(
        runtimePerm(R.drawable.ic_mic, R.string.perm_mic_title, R.string.perm_mic_why, Manifest.permission.RECORD_AUDIO),
        runtimePerm(R.drawable.ic_cam_camera, R.string.perm_camera_title, R.string.perm_camera_why, Manifest.permission.CAMERA),
        runtimePerm(R.drawable.ic_calendar, R.string.perm_calendar_title, R.string.perm_calendar_why, Manifest.permission.READ_CALENDAR),
        runtimePerm(R.drawable.ic_steps, R.string.perm_activity_title, R.string.perm_activity_why, Manifest.permission.ACTIVITY_RECOGNITION),
        settingsPerm(R.drawable.ic_dimmer, R.string.perm_overlay_title, R.string.perm_overlay_why, { Settings.canDrawOverlays(this) }) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        },
        settingsPerm(R.drawable.ic_muted_stat, R.string.perm_dnd_title, R.string.perm_dnd_why, {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
        }) { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
        settingsPerm(R.drawable.ic_settings, R.string.perm_a11y_title, R.string.perm_a11y_why,
            { flatContains("enabled_accessibility_services") }) { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        settingsPerm(R.drawable.ic_f_folder, R.string.perm_files_title, R.string.perm_files_why, {
            if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
            else ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }) {
            if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
    )

    private val pages: List<Page> by lazy {
        fun act(label: Int, cls: Class<*>) = TourAction(label) { a -> a.startActivity(Intent(a, cls)) }
        listOf(
            Page(R.mipmap.ic_launcher, 0xFFFFC107.toInt(), R.string.tour_p1_title, R.string.tour_p1_body),
            Page(R.drawable.ic_apps_grid, 0xFF42A5F5.toInt(), R.string.tour_p2_title, R.string.tour_p2_body, listOf(act(R.string.tour_action_settings, SettingsActivity::class.java))),
            Page(R.drawable.ic_arrow_right, 0xFF26A69A.toInt(), R.string.tour_p3_title, R.string.tour_p3_body),
            Page(R.drawable.ic_search, 0xFFAB47BC.toInt(), R.string.tour_p4_title, R.string.tour_p4_body),
            Page(R.drawable.ic_lock, 0xFFEF5350.toInt(), R.string.tour_p5_title, R.string.tour_p5_body, listOf(act(R.string.tour_action_app_lock, AppLockSettingsActivity::class.java))),
            Page(R.drawable.ic_focus_mode, 0xFF66BB6A.toInt(), R.string.tour_p6_title, R.string.tour_p6_body, listOf(act(R.string.tour_action_focus, FocusModeConfigActivity::class.java))),
            Page(R.drawable.ic_widget_placeholder, 0xFFFFA726.toInt(), R.string.tour_p7_title, R.string.tour_p7_body),
            Page(R.drawable.ic_ai_assistant, 0xFF7E57C2.toInt(), R.string.tour_p8_title, R.string.tour_p8_body, listOf(act(R.string.tour_action_web_apps, WebAppSettingsActivity::class.java))),
            Page(R.drawable.ic_f_folder, 0xFFFFC107.toInt(), R.string.tour_p9_title, R.string.tour_p9_body, listOf(act(R.string.tour_action_files, FilesActivity::class.java))),
            Page(R.drawable.ic_vault, 0xFF5C6BC0.toInt(), R.string.tour_p10_title, R.string.tour_p10_body),
            Page(R.drawable.ic_settings, 0xFF26C6DA.toInt(), R.string.tour_p11_title, R.string.tour_p11_body),
            Page(R.drawable.ic_image, 0xFFEC407A.toInt(), R.string.tour_p12_title, R.string.tour_p12_body),
            Page(R.drawable.ic_check_circle, 0xFF66BB6A.toInt(), R.string.tour_pp1_title, R.string.tour_pp1_body, perms = essentialPerms()),
            Page(R.drawable.ic_settings, 0xFF26C6DA.toInt(), R.string.tour_pp2_title, R.string.tour_pp2_body, perms = optionalPerms()),
            Page(R.drawable.ic_heart_filled, 0xFFEF5350.toInt(), R.string.tour_p14_title, R.string.tour_p14_body, perms = listOf(homePerm()))
        )
    }

    private lateinit var pager: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var next: TextView
    private lateinit var skip: TextView
    private lateinit var step: TextView
    private val accent by lazy { ThemeManager.color(this, R.attr.appAccent) }
    private val onAccent by lazy { ThemeManager.color(this, R.attr.appOnAccent) }
    private val textPrimary by lazy { ThemeManager.color(this, R.attr.appTextPrimary) }
    private val textSecondary by lazy { ThemeManager.color(this, R.attr.appTextSecondary) }
    private val fromSettings by lazy { intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false) }

    companion object {
        const val EXTRA_FROM_SETTINGS = "tour_from_settings"
        const val EXTRA_REQUEST_PERMISSIONS = "request_permissions_after_disclosure"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(buildUi())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (pager.currentItem > 0) pager.currentItem -= 1 else finishTour() }
        })
        update(0)
    }

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(0xCC000000.toInt()) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val top = FrameLayout(this).apply { setPadding(dp(20), dp(12), dp(20), 0) }
        step = TextView(this).apply { setTextColor(textSecondary); textSize = 13f }
        top.addView(step, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))
        skip = TextView(this).apply {
            text = getString(R.string.tour_skip); setTextColor(textSecondary); textSize = 15f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { finishTour() }
        }
        top.addView(skip, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
        column.addView(top, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))

        pager = ViewPager2(this).apply {
            adapter = PageAdapter()
            offscreenPageLimit = 1
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) { if (pages[position].perms != null) permissionsShown = true; update(position) }
            })
        }
        column.addView(pager, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(24))
        }
        dots = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        bottom.addView(dots, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        next = TextView(this).apply {
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(onAccent)
            background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(26).toFloat() }
            setPadding(dp(30), dp(13), dp(30), dp(13))
            setOnClickListener { if (pager.currentItem < pages.lastIndex) pager.currentItem += 1 else finishTour() }
        }
        bottom.addView(next, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        column.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        repeat(pages.size) { dots.addView(View(this), LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(6) }) }
        root.addView(column, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            column.setPadding(0, b.top, 0, b.bottom)
            insets
        }
        return root
    }

    private fun update(position: Int) {
        step.text = getString(R.string.tour_step, position + 1, pages.size)
        val last = position == pages.lastIndex
        next.text = getString(if (last) R.string.tour_done else R.string.tour_next)
        skip.visibility = if (last) View.INVISIBLE else View.VISIBLE
        for (i in 0 until dots.childCount) {
            val on = i == position
            dots.getChildAt(i).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (on) accent else 0x55FFFFFF)
            }
            dots.getChildAt(i).layoutParams = (dots.getChildAt(i).layoutParams as LinearLayout.LayoutParams).apply {
                width = dp(if (on) 18 else 7); height = dp(7)
            }
            if (on) dots.getChildAt(i).background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(4).toFloat() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!fromSettings) {
            getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE).edit {
                putLong(Constants.Prefs.ONBOARDING_ACTIVE_UNTIL, System.currentTimeMillis() + 30 * 60 * 1000L)
            }
        }
        refreshPerms()
    }

    private fun finishTour() {
        getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean(Constants.Prefs.ONBOARDING_TOUR_DONE, true)
            putLong(Constants.Prefs.ONBOARDING_ACTIVE_UNTIL, 0L)
        }
        if (!fromSettings) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (!permissionsShown && intent.getBooleanExtra(EXTRA_REQUEST_PERMISSIONS, false)) putExtra(EXTRA_REQUEST_PERMISSIONS, true)
            })
        }
        finish()
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(view: View, val tile: ImageView, val title: TextView, val body: TextView, val actions: LinearLayout) : RecyclerView.ViewHolder(view)

        override fun getItemCount() = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val scroll = android.widget.ScrollView(this@OnboardingTourActivity).apply {
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                isFillViewport = true
                isVerticalScrollBarEnabled = false
            }
            val col = LinearLayout(this@OnboardingTourActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(28), dp(24), dp(28), dp(16))
            }
            val tile = ImageView(this@OnboardingTourActivity).apply { val pd = dp(28); setPadding(pd, pd, pd, pd) }
            col.addView(tile, LinearLayout.LayoutParams(dp(120), dp(120)).apply { topMargin = dp(24); bottomMargin = dp(28) })
            val title = TextView(this@OnboardingTourActivity).apply {
                textSize = 28f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(textPrimary); gravity = Gravity.CENTER
            }
            col.addView(title, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            val body = TextView(this@OnboardingTourActivity).apply {
                textSize = 16f; setTextColor(textSecondary); setLineSpacing(dp(4).toFloat(), 1f)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            col.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
            val actions = LinearLayout(this@OnboardingTourActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            col.addView(actions, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(20) })
            scroll.addView(col)
            return VH(scroll, tile, title, body, actions)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val p = pages[position]
            val compact = (p.perms?.size ?: 0) > 1
            (h.tile.layoutParams as LinearLayout.LayoutParams).apply {
                width = dp(if (compact) 72 else 120); height = dp(if (compact) 72 else 120)
                topMargin = dp(if (compact) 4 else 24); bottomMargin = dp(if (compact) 14 else 28)
            }
            h.tile.setImageResource(p.icon)
            if (p.icon == R.mipmap.ic_launcher) {
                h.tile.clearColorFilter()
                h.tile.setPadding(0, 0, 0, 0)
                h.tile.background = null
            } else {
                h.tile.setColorFilter(accent)
                val pd = dp(if (compact) 18 else 30)
                h.tile.setPadding(pd, pd, pd, pd)
                h.tile.background = GradientDrawable().apply {
                    setColor((accent and 0x00FFFFFF) or 0x26000000)
                    cornerRadius = dp(36).toFloat()
                    setStroke(dp(1), (accent and 0x00FFFFFF) or 0x66000000)
                }
            }
            h.title.text = getString(p.titleRes)
            h.body.text = getString(p.bodyRes)
            h.body.gravity = if (position == 0 || position == pages.lastIndex) Gravity.CENTER else Gravity.START
            h.actions.removeAllViews()
            p.perms?.forEach { perm -> h.actions.addView(permRow(perm), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }) }
            p.actions.forEach { a ->
                h.actions.addView(TextView(this@OnboardingTourActivity).apply {
                    text = getString(a.labelRes)
                    textSize = 15f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                    setTextColor(textPrimary)
                    background = GradientDrawable().apply { setColor(0x22FFFFFF); cornerRadius = dp(24).toFloat(); setStroke(dp(1), accent) }
                    setPadding(dp(24), dp(11), dp(24), dp(11))
                    setOnClickListener { a.run(this@OnboardingTourActivity) }
                }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            }
        }

        private fun permRow(perm: Perm): View {
            val ctx = this@OnboardingTourActivity
            val granted = perm.granted()
            val row = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(0x22FFFFFF); cornerRadius = dp(18).toFloat()
                    setStroke(dp(1), if (granted) (accent and 0x00FFFFFF) or 0x88000000.toInt() else 0x22FFFFFF)
                }
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            row.addView(ImageView(ctx).apply {
                setImageResource(perm.icon); setColorFilter(accent)
                val pd = dp(8); setPadding(pd, pd, pd, pd)
                background = GradientDrawable().apply { setColor((accent and 0x00FFFFFF) or 0x26000000); cornerRadius = dp(12).toFloat() }
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(ctx).apply { text = getString(perm.titleRes); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(textPrimary) })
            texts.addView(TextView(ctx).apply { text = getString(perm.whyRes); textSize = 12.5f; setTextColor(textSecondary) })
            row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = dp(12); marginEnd = dp(10) })
            row.addView(TextView(ctx).apply {
                textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                if (granted) {
                    text = "✓  " + getString(R.string.perm_allowed)
                    setTextColor(accent)
                } else {
                    text = getString(R.string.perm_allow)
                    setTextColor(onAccent)
                    background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(18).toFloat() }
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    setOnClickListener { perm.request() }
                }
            })
            return row
        }
    }
}
