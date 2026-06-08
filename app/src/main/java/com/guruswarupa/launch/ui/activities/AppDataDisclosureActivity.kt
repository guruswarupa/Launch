package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.WallpaperManagerHelper
import com.guruswarupa.launch.models.Constants
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream





class AppDataDisclosureActivity : AppCompatActivity() {
    private data class FeatureOnboardingPage(
        val stepLabel: String,
        val title: String,
        val description: String,
        val hint: String,
        val visualCaption: String,
        val startIconRes: Int,
        val centerIconRes: Int,
        val endIconRes: Int
    )

    private val featurePages = listOf(
        FeatureOnboardingPage(
            stepLabel = "SEARCH",
            title = "Search apps, contacts, and the web from one bar",
            description = "The home search bar is your fastest way to open apps, find people, jump to web results, and run quick actions without leaving the launcher.",
            hint = "Typing can also act like an instant calculator.",
            visualCaption = "One search bar for apps, people, actions, and results.",
            startIconRes = R.drawable.ic_search,
            centerIconRes = R.drawable.ic_mic,
            endIconRes = R.drawable.ic_browser
        ),
        FeatureOnboardingPage(
            stepLabel = "APP ACTIONS",
            title = "Long press apps to reveal useful actions",
            description = "From the app list you can favorite apps, hide them, open app info, share APKs, uninstall, and set quick timers without digging through settings.",
            hint = "This is one of the easiest power features to miss on day one.",
            visualCaption = "Long press turns the app list into an action hub.",
            startIconRes = R.drawable.ic_apps_grid,
            centerIconRes = R.drawable.ic_share,
            endIconRes = R.drawable.ic_settings
        ),
        FeatureOnboardingPage(
            stepLabel = "VOICE",
            title = "Use voice for actions, calls, and shortcuts",
            description = "Voice search can launch apps, search the web, call contacts, message people, and trigger fast launcher actions when typing is slower.",
            hint = "Contacts and microphone permissions unlock the best voice flows.",
            visualCaption = "Speak to search, open, call, and message.",
            startIconRes = R.drawable.ic_mic,
            centerIconRes = R.drawable.ic_phone,
            endIconRes = R.drawable.ic_message
        ),
        FeatureOnboardingPage(
            stepLabel = "FOCUS MODE",
            title = "Focus Mode can reduce what appears when you need to work",
            description = "Choose allowed apps, start a session, and let Launch simplify the home experience when you want fewer distractions around you.",
            hint = "Long press the focus icon later to configure your allowed app list.",
            visualCaption = "Focus Mode helps the launcher stay quiet when you need it to.",
            startIconRes = R.drawable.ic_focus_mode,
            centerIconRes = R.drawable.ic_timer,
            endIconRes = R.drawable.ic_notifications
        ),
        FeatureOnboardingPage(
            stepLabel = "APP LIMITS",
            title = "App limits and timers help you control usage",
            description = "You can add daily limits, quick timers, and time-based control to apps so the launcher does more than just open them.",
            hint = "Limits can also return you to home when time is over.",
            visualCaption = "Launch can enforce time boundaries, not just track them.",
            startIconRes = R.drawable.ic_timer,
            centerIconRes = R.drawable.ic_focus_mode,
            endIconRes = R.drawable.ic_apps_grid
        ),
        FeatureOnboardingPage(
            stepLabel = "TODOS AND NOTES",
            title = "Keep lightweight planning directly on the launcher",
            description = "Use built-in notes and todo tools for quick capture, reminders, recurring tasks, and simple planning without opening separate apps first.",
            hint = "These work especially well as always-visible home widgets.",
            visualCaption = "Write it down and act on it from home.",
            startIconRes = R.drawable.ic_note,
            centerIconRes = R.drawable.ic_add,
            endIconRes = R.drawable.ic_save
        ),
        FeatureOnboardingPage(
            stepLabel = "WIDGETS",
            title = "Widgets in Launch are meant to be used, not ignored",
            description = "Weather, calendar, notifications, media, calculator, countdowns, finance, GitHub, battery, network, workouts, and more can live right inside your launcher.",
            hint = "You can also mix Launch widgets with normal Android widgets.",
            visualCaption = "Useful widgets can become part of your everyday home screen.",
            startIconRes = R.drawable.ic_calendar,
            centerIconRes = R.drawable.ic_notifications,
            endIconRes = R.drawable.ic_weather_sunny
        ),
        FeatureOnboardingPage(
            stepLabel = "PRIVACY",
            title = "Hide apps and keep sensitive things out of sight",
            description = "Launch can hide apps from the main list and keep private tools tucked away while still making them accessible when you need them.",
            hint = "This is useful even if you never use app lock or the vault.",
            visualCaption = "Not every app needs to stay visible on your main home screen.",
            startIconRes = R.drawable.ic_apps_grid,
            centerIconRes = R.drawable.ic_vault,
            endIconRes = R.drawable.ic_settings
        ),
        FeatureOnboardingPage(
            stepLabel = "VAULT",
            title = "The encrypted vault gives you a private place inside Launch",
            description = "Use the vault for sensitive files and protected content, with a launcher flow that stays separate from your normal app browsing.",
            hint = "Vault and app lock settings are available later when you want them.",
            visualCaption = "A private space is built into the launcher itself.",
            startIconRes = R.drawable.ic_vault,
            centerIconRes = R.drawable.ic_archive,
            endIconRes = R.drawable.ic_settings
        ),
        FeatureOnboardingPage(
            stepLabel = "WEB APPS",
            title = "Save websites as fast web apps with their own space",
            description = "Launch supports web apps with icon fetching, dedicated settings, and built-in ad blocking support so useful sites can behave more like lightweight apps.",
            hint = "Great for services you use often but do not want as full apps.",
            visualCaption = "Turn websites into clean, launcher-native shortcuts.",
            startIconRes = R.drawable.ic_browser,
            centerIconRes = R.drawable.ic_image,
            endIconRes = R.drawable.ic_settings
        ),
        FeatureOnboardingPage(
            stepLabel = "RSS AND DOCUMENTS",
            title = "Launch can also read news feeds and open documents",
            description = "You can browse RSS feeds, open PDFs and common documents, and keep useful reading workflows close to home.",
            hint = "This is one of the areas most users never discover unless it is surfaced early.",
            visualCaption = "News feeds and documents are part of the launcher too.",
            startIconRes = R.drawable.ic_browser,
            centerIconRes = R.drawable.ic_pdf,
            endIconRes = R.drawable.ic_file
        ),
        FeatureOnboardingPage(
            stepLabel = "WORKSPACES",
            title = "Use workspaces and work profile tools to separate contexts",
            description = "Switch between personal and work setups, control work apps, and keep the home screen adapted to different parts of your day.",
            hint = "The dock includes quick controls for these modes.",
            visualCaption = "Work and personal space can stay organized without changing launchers.",
            startIconRes = R.drawable.ic_workspace_active,
            centerIconRes = R.drawable.ic_work_profile_active,
            endIconRes = R.drawable.ic_apps_grid
        ),
        FeatureOnboardingPage(
            stepLabel = "GESTURES",
            title = "Some features are triggered by motion and gestures",
            description = "Shake for torch, back tap, flip-to-DND, and other gesture-based shortcuts let Launch react faster than a normal home screen.",
            hint = "These are optional, but they make the launcher feel much more alive.",
            visualCaption = "Gestures turn the launcher into something you can feel, not just tap.",
            startIconRes = R.drawable.ic_focus_mode,
            centerIconRes = R.drawable.ic_refresh,
            endIconRes = R.drawable.ic_settings
        ),
        FeatureOnboardingPage(
            stepLabel = "CUSTOMIZATION",
            title = "Wallpaper, typography, icon style, and layout are yours to shape",
            description = "Tune grid size, list mode, fonts, translucency, widget styling, wallpaper feel, and other visual details until Launch feels like your own setup.",
            hint = "You do not need to change everything at once. Start small and evolve it.",
            visualCaption = "Minimal does not have to mean generic.",
            startIconRes = R.drawable.ic_image,
            centerIconRes = R.drawable.ic_apps_grid,
            endIconRes = R.drawable.ic_archive
        ),
        FeatureOnboardingPage(
            stepLabel = "PERMISSIONS",
            title = "Optional permissions unlock the deeper parts of Launch",
            description = "Contacts, notifications, usage stats, calendar, microphone, camera, storage, biometrics, and activity recognition each enable different advanced features.",
            hint = "You can start minimal and unlock more only when you want it.",
            visualCaption = "Launch grows as you allow more capabilities.",
            startIconRes = R.drawable.ic_person,
            centerIconRes = R.drawable.ic_notifications,
            endIconRes = R.drawable.ic_settings
        )
    )

    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var currentFeaturePageIndex = 0

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                importSettingsFromFile(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_data_disclosure)

        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)


        if (prefs.getBoolean(Constants.Prefs.APP_DATA_CONSENT_GIVEN, false)) {

            startMainActivity()
            return
        }

        setupViews()
        setupWallpaper()
        startWelcomeAnimation()


        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val importRoot = findViewById<LinearLayout>(R.id.import_root)
                val viewModeRoot = findViewById<LinearLayout>(R.id.view_mode_selection_root)
                val disclosureRoot = findViewById<LinearLayout>(R.id.disclosure_root)

                if (viewModeRoot.visibility == View.VISIBLE) {
                    viewModeRoot.visibility = View.GONE
                    importRoot.visibility = View.VISIBLE
                    importRoot.alpha = 1f
                    importRoot.scaleX = 1f
                    importRoot.scaleY = 1f
                } else if (importRoot.visibility == View.VISIBLE) {
                    importRoot.visibility = View.GONE
                    disclosureRoot.visibility = View.VISIBLE
                    disclosureRoot.alpha = 1f
                    disclosureRoot.scaleX = 1f
                    disclosureRoot.scaleY = 1f
                }
            }
        })
    }

    private fun setupViews() {
        val titleText = findViewById<TextView>(R.id.disclosure_title)
        val messageText = findViewById<TextView>(R.id.disclosure_message)
        val linksText = findViewById<TextView>(R.id.disclosure_links)
        val acceptButton = findViewById<Button>(R.id.accept_button)
        val declineButton = findViewById<Button>(R.id.decline_button)

        val importRoot = findViewById<LinearLayout>(R.id.import_root)
        val disclosureRoot = findViewById<LinearLayout>(R.id.disclosure_root)
        val importDataButton = findViewById<Button>(R.id.import_data_button)
        val skipImportButton = findViewById<Button>(R.id.skip_import_button)

        titleText.text = getString(R.string.app_data_disclosure_title)


        messageText.text = Html.fromHtml(getString(R.string.app_data_disclosure_message), Html.FROM_HTML_MODE_COMPACT)

        linksText.text = Html.fromHtml(getString(R.string.data_disclosure_links), Html.FROM_HTML_MODE_COMPACT)
        linksText.movementMethod = LinkMovementMethod.getInstance()

        acceptButton.setOnClickListener {

            val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
            prefs.edit { putBoolean(Constants.Prefs.APP_DATA_CONSENT_GIVEN, true) }


            disclosureRoot.animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(400)
                .withEndAction {
                    disclosureRoot.visibility = View.GONE
                    importRoot.visibility = View.VISIBLE
                    importRoot.alpha = 0f
                    importRoot.scaleX = 1.05f
                    importRoot.scaleY = 1.05f
                    importRoot.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(450)
                        .setInterpolator(OvershootInterpolator())
                        .start()
                }
                .start()
        }

        declineButton.setOnClickListener {

            finishAffinity()
        }

        importDataButton.setOnClickListener {
            importLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            })
        }

        skipImportButton.setOnClickListener {
            showViewModeSelection()
        }

        setupViewModeSelection()
    }

    private fun importSettingsFromFile(uri: Uri) {
        try {
            val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
            contentResolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(ins).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "settings.json") {
                            val p = JSONObject(zis.bufferedReader().readText()).optJSONObject("main_preferences")
                            if (p != null) {
                                prefs.edit {
                                    val stringSetKeys = setOf("favorite_apps", "hidden_apps", "focus_mode_allowed_apps", "locked_apps")
                                    p.keys().forEach { k ->
                                        val v = p.get(k)

                                        if (k in stringSetKeys) {
                                            val set = when (v) {
                                                is JSONArray -> {
                                                    val s = mutableSetOf<String>()
                                                    for (i in 0 until v.length()) s.add(v.getString(i))
                                                    s
                                                }
                                                is String -> {
                                                    if (v.startsWith("[") && v.endsWith("]")) {
                                                        v.substring(1, v.length - 1)
                                                            .split(",")
                                                            .map { it.trim() }
                                                            .filter { it.isNotEmpty() }
                                                            .toSet()
                                                    } else {
                                                        setOf(v)
                                                    }
                                                }
                                                else -> emptySet<String>()
                                            }
                                            putStringSet(k, set)
                                        } else {
                                            when (v) {
                                                is String -> putString(k, v)
                                                is Boolean -> putBoolean(k, v)
                                                is Int -> putInt(k, v)
                                                is Long -> putLong(k, v)
                                                is Double -> putFloat(k, v.toFloat())
                                                is JSONArray -> putString(k, v.toString())
                                            }
                                        }
                                    }

                                    putBoolean(Constants.Prefs.APP_DATA_CONSENT_GIVEN, true)

                                    putBoolean(Constants.Prefs.CONTACTS_PERMISSION_DENIED, false)
                                    putBoolean(Constants.Prefs.USAGE_STATS_PERMISSION_DENIED, false)

                                    putBoolean(Constants.Prefs.INITIAL_PERMISSIONS_ASKED, false)

                                    putBoolean(Constants.Prefs.WAITING_FOR_USAGE_STATS_RETURN, false)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            Toast.makeText(this, "Settings imported successfully", Toast.LENGTH_SHORT).show()
            startMainActivity(requestPermissions = true)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to import settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startWelcomeAnimation() {
        val welcomeContainer = findViewById<LinearLayout>(R.id.welcome_container)
        val welcomeText = findViewById<TextView>(R.id.welcome_text)
        val welcomeSubtitle = findViewById<TextView>(R.id.welcome_subtitle)
        val disclosureRoot = findViewById<LinearLayout>(R.id.disclosure_root)


        welcomeText.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(1200)
            .setInterpolator(OvershootInterpolator(0.8f))
            .setStartDelay(400)
            .start()


        welcomeSubtitle.translationY = 20f
        welcomeSubtitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(1000)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setStartDelay(1000)
            .start()


        handler.postDelayed({

            welcomeContainer.animate()
                .alpha(0f)
                .scaleX(1.5f)
                .scaleY(1.5f)
                .setDuration(800)
                .setInterpolator(AnticipateInterpolator())
                .withEndAction {
                    welcomeContainer.visibility = View.GONE


                    disclosureRoot.visibility = View.VISIBLE
                    disclosureRoot.alpha = 0f
                    disclosureRoot.scaleX = 0.95f
                    disclosureRoot.scaleY = 0.95f

                    disclosureRoot.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(1000)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                .start()
        }, 3800)
    }

    private fun setupWallpaper() {
        val wallpaperView = findViewById<ImageView>(R.id.disclosure_wallpaper)
        val wallpaperHelper = WallpaperManagerHelper(this, wallpaperView, null, backgroundExecutor)
        wallpaperHelper.setWallpaperBackground()
    }

    private fun initializeFeatureIndicators() {
        val indicators = findViewById<LinearLayout>(R.id.feature_page_indicators)
        indicators.removeAllViews()
        repeat(featurePages.size) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(20, 20).also { params ->
                    params.marginStart = 8
                    params.marginEnd = 8
                }
                background = getDrawable(R.drawable.circular_background)
                alpha = 0.25f
            }
            indicators.addView(dot)
        }
    }

    private fun showFeaturePage(index: Int, animate: Boolean) {
        currentFeaturePageIndex = index
        val page = featurePages[index]
        val stepLabel = findViewById<TextView>(R.id.feature_onboarding_step_label)
        val visualCaption = findViewById<TextView>(R.id.feature_visual_caption)
        val title = findViewById<TextView>(R.id.feature_onboarding_title)
        val description = findViewById<TextView>(R.id.feature_onboarding_description)
        val hint = findViewById<TextView>(R.id.feature_onboarding_hint)
        val startIcon = findViewById<ImageView>(R.id.feature_icon_start)
        val centerIcon = findViewById<ImageView>(R.id.feature_icon_center)
        val endIcon = findViewById<ImageView>(R.id.feature_icon_end)
        val content = findViewById<LinearLayout>(R.id.feature_onboarding_content)
        val backButton = findViewById<Button>(R.id.feature_onboarding_back_button)
        val nextButton = findViewById<Button>(R.id.feature_onboarding_next_button)

        stepLabel.text = "${index + 1} / ${featurePages.size}  ${page.stepLabel}"
        visualCaption.text = page.visualCaption
        title.text = page.title
        description.text = page.description
        hint.text = page.hint
        startIcon.setImageResource(page.startIconRes)
        centerIcon.setImageResource(page.centerIconRes)
        endIcon.setImageResource(page.endIconRes)

        backButton.alpha = if (index == 0) 0.55f else 1f
        nextButton.text = if (index == featurePages.lastIndex) "Continue to Import" else "Next"
        updateFeatureIndicators(index)

        if (animate) {
            content.animate()
                .alpha(0f)
                .translationY(24f)
                .setDuration(140)
                .withEndAction {
                    content.translationY = 32f
                    content.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(360)
                        .setInterpolator(OvershootInterpolator(0.75f))
                        .start()
                }
                .start()
        } else {
            content.alpha = 1f
            content.translationY = 0f
        }
    }

    private fun updateFeatureIndicators(activeIndex: Int) {
        val indicators = findViewById<LinearLayout>(R.id.feature_page_indicators)
        for (index in 0 until indicators.childCount) {
            indicators.getChildAt(index).alpha = if (index == activeIndex) 0.95f else 0.25f
        }
    }

    private fun completeFeatureOnboarding(featureRoot: LinearLayout, importRoot: LinearLayout) {
        getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean("feature_discovery_guide_shown", true)
        }

        featureRoot.animate()
            .alpha(0f)
            .scaleX(0.97f)
            .scaleY(0.97f)
            .setDuration(280)
            .withEndAction {
                featureRoot.visibility = View.GONE
                importRoot.visibility = View.VISIBLE
                importRoot.alpha = 0f
                importRoot.scaleX = 1.05f
                importRoot.scaleY = 1.05f
                importRoot.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(450)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
            .start()
    }

    private fun startMainActivity(requestPermissions: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (requestPermissions) {
                putExtra("request_permissions_after_disclosure", true)
            }
        }
        startActivity(intent)
        finish()
    }

    private fun setupViewModeSelection() {
        val listModeOption = findViewById<LinearLayout>(R.id.list_mode_option)
        val gridModeOption = findViewById<LinearLayout>(R.id.grid_mode_option)

        listModeOption.setOnClickListener {
            selectViewMode(Constants.Prefs.VIEW_PREFERENCE_LIST)
        }

        gridModeOption.setOnClickListener {
            selectViewMode(Constants.Prefs.VIEW_PREFERENCE_GRID)
        }
    }

    private fun showViewModeSelection() {
        val importRoot = findViewById<LinearLayout>(R.id.import_root)
        val viewModeRoot = findViewById<LinearLayout>(R.id.view_mode_selection_root)

        importRoot.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(300)
            .withEndAction {
                importRoot.visibility = View.GONE
                viewModeRoot.visibility = View.VISIBLE
                viewModeRoot.alpha = 0f
                viewModeRoot.scaleX = 1.05f
                viewModeRoot.scaleY = 1.05f
                viewModeRoot.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
            .start()
    }

    private fun selectViewMode(mode: String) {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        prefs.edit { putString(Constants.Prefs.VIEW_PREFERENCE, mode) }

        val viewModeRoot = findViewById<LinearLayout>(R.id.view_mode_selection_root)
        val selectedOption = if (mode == Constants.Prefs.VIEW_PREFERENCE_LIST) {
            findViewById<LinearLayout>(R.id.list_mode_option)
        } else {
            findViewById<LinearLayout>(R.id.grid_mode_option)
        }

        selectedOption.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(150)
            .withEndAction {
                selectedOption.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .withEndAction {
                        viewModeRoot.animate()
                            .alpha(0f)
                            .scaleX(0.97f)
                            .scaleY(0.97f)
                            .setDuration(300)
                            .withEndAction {
                                startMainActivity(requestPermissions = true)
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
        handler.removeCallbacksAndMessages(null)
    }
}
