package com.guruswarupa.launch.ui.activities

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.RssFeedManager
import com.guruswarupa.launch.managers.RssFeedSource
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.utils.WallpaperDisplayHelper

class RssFeedSettingsActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }
    private val backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var overlayView: View
    private lateinit var rssFeedManager: RssFeedManager
    private val collapsedSections = linkedSetOf<String>()
    private var sectionStateInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_rss_feed_settings)

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

        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.rss_sources_wallpaper))

        rssFeedManager = RssFeedManager(this, prefs, backgroundExecutor)
        listContainer = findViewById(R.id.rss_sources_list)
        emptyView = findViewById(R.id.rss_sources_empty)
        overlayView = findViewById(R.id.rss_sources_overlay)

        applyBackgroundTranslucency()

        findViewById<Button>(R.id.add_rss_source_button).setOnClickListener { showSourceEditor() }

        renderSources()
    }

    override fun onDestroy() {
        backgroundExecutor.shutdown()
        super.onDestroy()
    }

    private fun renderSources() {
        listContainer.removeAllViews()
        emptyView.visibility = View.GONE

        val groupedPresets = RssFeedManager.getPresetSources().groupBy { it.category }
        RssFeedManager.getPresetCategories().forEach { category ->
            val sources = groupedPresets[category].orEmpty()
            listContainer.addView(
                createSectionCard(
                    title = category,
                    subtitle = getSectionSubtitle(category),
                    sources = sources,
                    sectionKey = "preset_$category"
                )
            )
        }

        val customUrls = RssFeedManager.getCustomFeedUrls(prefs)
        val customSources = customUrls.map { url ->
            RssFeedSource(
                title = url.hostLabel(),
                url = url,
                category = "Custom Links",
                isCustom = true
            )
        }
        listContainer.addView(
            createSectionCard(
                title = "Custom Links",
                subtitle = "Add your own RSS or Atom links from blogs, magazines, or niche publishers.",
                sources = customSources,
                sectionKey = "custom_links",
                emptyMessage = "No custom RSS links yet."
            )
        )
        sectionStateInitialized = true
    }

    private fun getSectionSubtitle(category: String): String {
        return when (category) {
            "News" -> "Top headlines and general daily coverage from major outlets."
            "India" -> "Coverage focused on India from major Indian publishers."
            "Technology" -> "Product launches, startups, software, AI, and the wider tech industry."
            "Finance" -> "Markets, business moves, companies, money, and the economy."
            "Sports" -> "Scores, analysis, leagues, athletes, and major sporting events."
            "Entertainment" -> "Film, TV, music, celebrities, and pop culture updates."
            "Health" -> "Wellness, medicine, public health, and practical health reporting."
            "Science" -> "Research discoveries, space, nature, and science explainers."
            "Travel" -> "Destinations, tips, aviation, hotels, and travel inspiration."
            "Politics" -> "Government, elections, policy, diplomacy, and political analysis."
            "Games" -> "Gaming news, reviews, releases, and industry coverage."
            "Environment" -> "Climate, sustainability, conservation, and environmental reporting."
            "Education" -> "Schools, universities, policy changes, and learning trends."
            "Automotive" -> "Cars, EVs, motorsport, reviews, and auto industry news."
            "Fitness" -> "Training, running, recovery, and active lifestyle content."
            "Arts" -> "Design, visual culture, museums, creativity, and cultural commentary."
            else -> "Popular sources in this category."
        }
    }

    private fun createSourceItem(source: RssFeedSource): View {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_rss_feed_toggle, listContainer, false)
        itemView.findViewById<TextView>(R.id.rss_feed_title).text = source.title
        itemView.findViewById<TextView>(R.id.rss_feed_url).text = source.url

        val toggle = itemView.findViewById<SwitchCompat>(R.id.rss_feed_switch)
        val disabledColor = Color.WHITE
        val enabledColor = getColor(R.color.nord8)
        fun applyToggleColors(isEnabled: Boolean) {
            val color = if (isEnabled) enabledColor else disabledColor
            toggle.thumbTintList = ColorStateList.valueOf(color)
            toggle.trackTintList = ColorStateList.valueOf(color)
        }

        toggle.isChecked = rssFeedManager.isFeedEnabled(source.url)
        applyToggleColors(toggle.isChecked)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            applyToggleColors(isChecked)
            rssFeedManager.setFeedEnabled(source.url, isChecked)
            notifySettingsChanged()
        }

        val editButton = itemView.findViewById<ImageButton>(R.id.rss_feed_edit)
        val deleteButton = itemView.findViewById<ImageButton>(R.id.rss_feed_delete)
        if (source.isCustom) {
            editButton.visibility = View.VISIBLE
            deleteButton.visibility = View.VISIBLE
            editButton.setOnClickListener { showSourceEditor(source.url) }
            deleteButton.setOnClickListener {
                rssFeedManager.removeFeedUrl(source.url)
                notifySettingsChanged()
                renderSources()
            }
        } else {
            editButton.visibility = View.GONE
            deleteButton.visibility = View.GONE
        }

        itemView.setOnClickListener {
            toggle.isChecked = !toggle.isChecked
        }

        return itemView
    }

    private fun createSectionCard(
        title: String,
        subtitle: String,
        sources: List<RssFeedSource>,
        sectionKey: String,
        emptyMessage: String = "No sources in this section yet."
    ): View {
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(getColor(R.color.card_background))
            strokeColor = getColor(R.color.outlineColor)
            strokeWidth = dp(1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.color.transparent)
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textColumn.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        textColumn.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(0xCCFFFFFF.toInt())
            setPadding(0, dp(4), 0, 0)
        })

        val arrowView = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.WHITE)
            alpha = 0.4f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }

        if (sources.isEmpty()) {
            content.addView(TextView(this).apply {
                text = emptyMessage
                textSize = 13f
                setTextColor(0xCCFFFFFF.toInt())
                setPadding(0, 0, 0, dp(4))
            })
        } else {
            sources.forEach { source ->
                content.addView(createSourceItem(source))
            }
        }

        if (!sectionStateInitialized) {
            collapsedSections.add(sectionKey)
        }
        updateSectionVisibility(sectionKey, content, arrowView)

        header.setOnClickListener {
            if (collapsedSections.contains(sectionKey)) {
                collapsedSections.remove(sectionKey)
            } else {
                collapsedSections.add(sectionKey)
            }
            updateSectionVisibility(sectionKey, content, arrowView)
        }

        header.addView(textColumn)
        header.addView(arrowView)
        wrapper.addView(header)
        wrapper.addView(content)
        card.addView(wrapper)
        return card
    }

    private fun updateSectionVisibility(sectionKey: String, content: View, arrowView: TextView) {
        val collapsed = collapsedSections.contains(sectionKey)
        content.visibility = if (collapsed) View.GONE else View.VISIBLE
        arrowView.text = if (collapsed) "▼" else "▲"
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun showSourceEditor(existingUrl: String? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_rss_source, null)
        val input = dialogView.findViewById<EditText>(R.id.rss_source_url_input)
        input.setText(existingUrl.orEmpty())
        input.setSelection(input.text.length)

        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(if (existingUrl == null) "Add Custom RSS Link" else "Edit Custom RSS Link")
            .setView(dialogView)
            .setPositiveButton(if (existingUrl == null) "Add" else "Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newUrl = input.text.toString().trim()
                if (existingUrl != null && newUrl.equals(existingUrl, ignoreCase = true)) {
                    dialog.dismiss()
                    return@setOnClickListener
                }

                if (existingUrl != null) {
                    rssFeedManager.removeFeedUrl(existingUrl)
                }

                if (!rssFeedManager.addFeedUrl(newUrl)) {
                    if (existingUrl != null) {
                        rssFeedManager.addFeedUrl(existingUrl)
                    }
                    Toast.makeText(this, "Enter a valid, unique feed URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                notifySettingsChanged()
                renderSources()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        overlayView.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply { setPackage(packageName) })
    }

    private fun String.hostLabel(): String {
        return runCatching { android.net.Uri.parse(this).host.orEmpty() }
            .getOrDefault("")
            .removePrefix("www.")
            .ifBlank { "Custom RSS Link" }
    }
}
