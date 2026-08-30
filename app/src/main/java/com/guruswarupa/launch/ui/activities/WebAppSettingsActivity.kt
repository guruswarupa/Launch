package com.guruswarupa.launch.ui.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.managers.WebAppIconFetcher
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.WebAppManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.WebAppEntry
import com.guruswarupa.launch.ui.theme.ThemeManager
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import com.guruswarupa.launch.utils.WebAppSearchHelper
import com.guruswarupa.launch.utils.SearchSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebAppSettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }
    private val webAppManager by lazy { WebAppManager(prefs) }

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var scrollView: View
    private lateinit var overlayView: View

    private var isAnimating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_web_app_settings)

        val mainContent = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + 16.toPx(),
                view.paddingRight,
                systemBars.bottom + 16.toPx()
            )
            insets
        }

        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.web_apps_wallpaper))

        listContainer = findViewById(R.id.web_apps_list)
        emptyView = findViewById(R.id.web_apps_empty)
        scrollView = findViewById(R.id.web_apps_scroll_view)
        overlayView = findViewById(R.id.web_apps_overlay)

        applyBackgroundTranslucency()

        findViewById<Button>(R.id.add_web_app_button).setOnClickListener {
            animateButtonClick(it)
            showEditorDialog()
        }

        renderWebApps()
    }

    override fun onBackPressed() {
        animateFinish()
    }

    private fun renderWebApps() {
        val webApps = webAppManager.getWebApps()
        listContainer.removeAllViews()


        if (webApps.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.alpha = 0f
            ObjectAnimator.ofFloat(emptyView, "alpha", 0f, 1f).apply {
                duration = 300
                start()
            }
        } else {
            emptyView.visibility = View.GONE
        }

        val inflater = LayoutInflater.from(this)
        webApps.forEachIndexed { index, entry ->
            val itemView = inflater.inflate(R.layout.item_web_app, listContainer, false)
            val iconView = itemView.findViewById<android.widget.ImageView>(R.id.web_app_item_icon)
            itemView.findViewById<TextView>(R.id.web_app_item_name).text = entry.name
            itemView.findViewById<TextView>(R.id.web_app_item_url).text = entry.url


            WebAppIconFetcher.loadIcon(this, entry.url) { drawable ->
                if (drawable != null) {
                    iconView.setImageDrawable(drawable)
                    iconView.alpha = 0f
                    ObjectAnimator.ofFloat(iconView, "alpha", 0f, 1f).apply {
                        duration = 300
                        startDelay = index * 50L
                        start()
                    }
                }
            }

            itemView.findViewById<ImageButton>(R.id.web_app_item_open).setOnClickListener {
                val intent = Intent(this, WebAppActivity::class.java).apply {
                    putExtra(WebAppActivity.EXTRA_WEB_APP_NAME, entry.name)
                    putExtra(WebAppActivity.EXTRA_WEB_APP_URL, entry.url)
                    putExtra(WebAppActivity.EXTRA_BLOCK_REDIRECTS, entry.blockRedirects)

                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                                Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    )
                }
                val options = ActivityOptions.makeCustomAnimation(this, R.anim.slide_in_right, R.anim.slide_out_left)
                startActivity(intent, options.toBundle())
            }

            itemView.findViewById<ImageButton>(R.id.web_app_item_edit).setOnClickListener {
                showEditorDialog(entry)
            }

            itemView.findViewById<ImageButton>(R.id.web_app_item_delete).setOnClickListener {
                confirmDelete(entry)
            }

            listContainer.addView(itemView)


            if (!isAnimating) {
                itemView.alpha = 0f
                itemView.translationY = 50f
                ObjectAnimator.ofFloat(itemView, "alpha", 0f, 1f).apply {
                    duration = 300
                    startDelay = index * 80L
                    interpolator = OvershootInterpolator(1.2f)
                    start()
                }
                ObjectAnimator.ofFloat(itemView, "translationY", 50f, 0f).apply {
                    duration = 300
                    startDelay = index * 80L
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }
        }
    }

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val scrimBase = ThemeManager.color(this, R.attr.appScrim)
        val color = Color.argb(alpha, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase))
        overlayView.setBackgroundColor(color)
    }

    private fun animateButtonClick(view: View) {
        ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun animateFinish() {
        if (isAnimating) return
        isAnimating = true

        val root = findViewById<View>(R.id.web_apps_root)
        ObjectAnimator.ofFloat(root, "alpha", 1f, 0f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(
                            OVERRIDE_TRANSITION_CLOSE,
                            R.anim.slide_in_left,
                            R.anim.slide_out_right
                        )
                    }
                    finish()
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    }
                    isAnimating = false
                }
            })
            start()
        }
    }

    private var searchJob: Job? = null

    private fun showEditorDialog(existing: WebAppEntry? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_web_app_editor, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.web_app_name_input)
        val urlInput = dialogView.findViewById<EditText>(R.id.web_app_url_input)
        val searchButton = dialogView.findViewById<ImageButton>(R.id.web_app_search_button)
        val suggestionsContainer = dialogView.findViewById<LinearLayout>(R.id.web_app_suggestions_container)
        val suggestionsList = dialogView.findViewById<LinearLayout>(R.id.web_app_suggestions_list)
        val searchProgress = dialogView.findViewById<ProgressBar>(R.id.web_app_search_progress)
        val blockRedirectsSwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.web_app_block_redirects_switch)
        val allowHttpCheckbox = dialogView.findViewById<CheckBox>(R.id.web_app_allow_http_checkbox)

        nameInput.setText(existing?.name.orEmpty())
        urlInput.setText(existing?.url.orEmpty())
        urlInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI

        // Set checkbox based on existing URL
        allowHttpCheckbox.isChecked = existing?.url?.startsWith("http://", ignoreCase = true) == true


        blockRedirectsSwitch.isChecked = existing?.blockRedirects ?: true


        val enabledColor = Color.rgb(72, 191, 145)
        val disabledColor = ThemeManager.color(this, R.attr.appTextPrimary)
        fun applySwitchColors(isChecked: Boolean) {
            blockRedirectsSwitch.thumbTintList = android.content.res.ColorStateList.valueOf(
                if (isChecked) enabledColor else disabledColor
            )
            blockRedirectsSwitch.trackTintList = android.content.res.ColorStateList.valueOf(
                if (isChecked) enabledColor else disabledColor
            )
        }
        applySwitchColors(blockRedirectsSwitch.isChecked)


        if (existing == null) {
            suggestionsContainer.visibility = View.VISIBLE
            setupPopularSuggestions(suggestionsList, nameInput, urlInput)
        }


        searchButton.setOnClickListener {
            showSearchDialog(nameInput, urlInput, searchProgress)
        }

        nameInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && nameInput.text.toString().trim().isBlank()) {
                nameInput.error = getString(R.string.web_app_name_required)
            }
        }

        urlInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val url = urlInput.text.toString().trim()
                if (url.isBlank()) {
                    urlInput.error = getString(R.string.web_app_url_required)
                } else if (!isSupportedWebUrl(url, allowHttpCheckbox.isChecked)) {
                    urlInput.error = if (allowHttpCheckbox.isChecked) {
                        "Invalid URL format"
                    } else {
                        getString(R.string.web_app_https_required)
                    }
                }
            }
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(if (existing == null) R.string.add_web_app else R.string.edit_web_app)
            .setView(dialogView)
            .setPositiveButton(
                if (existing == null) R.string.add_button else R.string.save_button,
                null
            )
            .setNegativeButton(R.string.cancel_button, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val url = urlInput.text.toString().trim()
            val blockRedirects = blockRedirectsSwitch.isChecked
            val allowHttp = allowHttpCheckbox.isChecked

            when {
                name.isBlank() -> {
                    nameInput.requestFocus()
                    Toast.makeText(
                        this@WebAppSettingsActivity,
                        R.string.web_app_name_required,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                url.isBlank() -> {
                    urlInput.requestFocus()
                    Toast.makeText(
                        this@WebAppSettingsActivity,
                        R.string.web_app_url_required,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                !isSupportedWebUrl(url, allowHttp) -> {
                    urlInput.requestFocus()
                    Toast.makeText(
                        this@WebAppSettingsActivity,
                        if (allowHttp) getString(R.string.web_app_invalid_url_format) else getString(R.string.web_app_https_required),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                else -> {
                    // Show warning for HTTP URLs
                    val normalizedUrl = webAppManager.normalizeUrl(url)
                    if (normalizedUrl.startsWith("http://", ignoreCase = true) &&
                        !isLocalhostOrPrivateIp(normalizedUrl)) {
                        AlertDialog.Builder(this@WebAppSettingsActivity, R.style.CustomDialogTheme)
                            .setTitle(getString(R.string.dlg_security_warning))
                            .setMessage(getString(R.string.dlg_this_web_app_uses_http_unencrypted_connection_yo))
                            .setPositiveButton(getString(R.string.dlg_add_anyway)) { _, _ ->
                                saveWebApp(existing, name, url, blockRedirects)
                                dialog.dismiss()
                            }
                            .setNegativeButton(getString(R.string.cancel_button), null)
                            .show()
                    } else {
                        saveWebApp(existing, name, url, blockRedirects)
                        dialog.dismiss()
                    }
                }
            }
        }
    }

    private fun setupPopularSuggestions(
        suggestionsList: LinearLayout,
        nameInput: EditText,
        urlInput: EditText
    ) {
        suggestionsList.removeAllViews()

        WebAppSearchHelper.POPULAR_WEBSITES.take(10).forEach { suggestion ->
            val chip = TextView(this).apply {
                text = suggestion.title
                setPadding(24, 12, 24, 12)
                setBackgroundResource(R.drawable.dialog_input_background)
                setTextColor(ThemeManager.color(context, R.attr.appTextPrimary))
                textSize = 13f
                isSingleLine = true
                maxLines = 1
                setOnClickListener {
                    nameInput.setText(suggestion.title)
                    urlInput.setText(suggestion.url)
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 12, 8)
            }
            chip.layoutParams = params

            suggestionsList.addView(chip)
        }
    }

    private fun showSearchDialog(
        nameInput: EditText,
        urlInput: EditText,
        searchProgress: ProgressBar
    ) {
        val searchView = LayoutInflater.from(this).inflate(R.layout.dialog_web_app_search, null)
        val searchInput = searchView.findViewById<EditText>(R.id.search_input)
        val searchProgressLocal = searchView.findViewById<ProgressBar>(R.id.search_progress)
        val searchResultsList = searchView.findViewById<LinearLayout>(R.id.search_results_list)
        val searchEmptyText = searchView.findViewById<TextView>(R.id.search_empty_text)

        val searchDialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(R.string.web_app_search_title)
            .setView(searchView)
            .setPositiveButton(R.string.cancel_button, null)
            .show()


        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.text.toString(), searchResultsList, searchProgressLocal, searchEmptyText, nameInput, urlInput, searchDialog)
                true
            } else {
                false
            }
        }
    }

    private fun performSearch(
        query: String,
        searchResultsList: LinearLayout,
        searchProgress: ProgressBar,
        searchEmptyText: TextView,
        nameInput: EditText,
        urlInput: EditText,
        searchDialog: AlertDialog
    ) {
        if (query.isBlank()) return

        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.Main).launch {
            searchProgress.visibility = View.VISIBLE
            searchEmptyText.visibility = View.GONE
            searchResultsList.removeAllViews()

            val results = try {
                WebAppSearchHelper.searchGoogle(query)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    searchProgress.visibility = View.GONE
                    searchEmptyText.text = getString(R.string.lbl_search_failed_try_again)
                    searchEmptyText.visibility = View.VISIBLE
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                searchProgress.visibility = View.GONE

                if (results.isEmpty()) {
                    searchEmptyText.visibility = View.VISIBLE
                } else {
                    searchEmptyText.visibility = View.GONE
                    results.forEach { result ->
                        addSearchResultItem(searchResultsList, result, nameInput, urlInput, searchDialog)
                    }
                }
            }
        }
    }

    private fun addSearchResultItem(
        container: LinearLayout,
        result: SearchSuggestion,
        nameInput: EditText,
        urlInput: EditText,
        searchDialog: AlertDialog
    ) {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_web_app_search_result, container, false)
        val titleText = itemView.findViewById<TextView>(R.id.search_result_title)
        val urlText = itemView.findViewById<TextView>(R.id.search_result_url)
        val descText = itemView.findViewById<TextView>(R.id.search_result_description)

        titleText.text = result.title
        urlText.text = result.url
        descText.text = result.description

        itemView.setOnClickListener {
            nameInput.setText(result.title)
            urlInput.setText(result.url)
            searchDialog.dismiss()
            Toast.makeText(this, this.getString(R.string.toast_selected, result.title), Toast.LENGTH_SHORT).show()
        }

        container.addView(itemView)
    }

    private fun confirmDelete(entry: WebAppEntry) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(R.string.remove_web_app)
            .setMessage(getString(R.string.remove_web_app_message, entry.name))
            .setPositiveButton(R.string.delete_button) { _, _ ->
                webAppManager.removeWebApp(entry.id)
                notifySettingsChanged()
                renderWebApps()


                Toast.makeText(
                    this,
                    getString(R.string.web_app_removed, entry.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun isSupportedWebUrl(rawUrl: String, allowHttp: Boolean = false): Boolean {
        val normalized = try {
            webAppManager.normalizeUrl(rawUrl)
        } catch (_: IllegalArgumentException) {
            return false
        }

        // Always allow HTTPS
        if (normalized.startsWith("https://", ignoreCase = true)) {
            return true
        }

        // Allow HTTP if explicitly allowed or if it's localhost/intranet
        if (normalized.startsWith("http://", ignoreCase = true)) {
            if (allowHttp) {
                return true
            }

            // Automatically allow for localhost and private IPs
            val host = try {
                android.net.Uri.parse(normalized).host
            } catch (e: Exception) {
                null
            }

            if (host != null) {
                // Allow localhost variants
                if (host.equals("localhost", ignoreCase = true) ||
                    host.equals("127.0.0.1") ||
                    host.equals("::1") ||
                    host.startsWith("127.") ||
                    host.endsWith(".local")) {
                    return true
                }

                // Allow private IP ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
                if (host.matches(Regex("^10\\..+")) ||
                    host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..+")) ||
                    host.matches(Regex("^192\\.168\\..+"))) {
                    return true
                }
            }
        }

        return false
    }

    private fun isLocalhostOrPrivateIp(url: String): Boolean {
        val host = try {
            android.net.Uri.parse(url).host
        } catch (e: Exception) {
            return false
        }

        if (host == null) return false

        // Check for localhost variants
        if (host.equals("localhost", ignoreCase = true) ||
            host.equals("127.0.0.1") ||
            host.equals("::1") ||
            host.startsWith("127.") ||
            host.endsWith(".local")) {
            return true
        }

        // Check for private IP ranges
        if (host.matches(Regex("^10\\..+")) ||
            host.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..+")) ||
            host.matches(Regex("^192\\.168\\..+"))) {
            return true
        }

        return false
    }

    private fun saveWebApp(existing: WebAppEntry?, name: String, url: String, blockRedirects: Boolean) {
        if (existing == null) {
            webAppManager.addWebApp(name, url, blockRedirects)
        } else {
            webAppManager.updateWebApp(existing.id, name, url, blockRedirects)
        }
        notifySettingsChanged()
        renderWebApps()

        Toast.makeText(
            this,
            if (existing == null) R.string.web_app_added else R.string.web_app_updated,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply {
            setPackage(
                packageName
            )
        })
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()

        WebAppIconFetcher.clearCache()
    }
}
