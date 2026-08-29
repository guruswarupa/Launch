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
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

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
            Toast.makeText(this, this.getString(R.string.toast_settings_imported_successfully), Toast.LENGTH_SHORT).show()
            startMainActivity(requestPermissions = true)
        } catch (e: Exception) {
            Toast.makeText(this, this.getString(R.string.toast_failed_to_import_settings, e.message), Toast.LENGTH_SHORT).show()
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
