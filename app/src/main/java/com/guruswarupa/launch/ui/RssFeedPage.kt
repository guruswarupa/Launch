package com.guruswarupa.launch.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.adapters.RssFeedAdapter
import com.guruswarupa.launch.managers.RssArticle
import com.guruswarupa.launch.managers.RssFeedManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.theme.ThemeManager

class RssFeedPage(
    private val activity: MainActivity,
    private val rootView: View
) {
    private val swipeRefreshLayout: SwipeRefreshLayout = rootView.findViewById(R.id.rss_swipe_refresh)
    private val recyclerView: RecyclerView = rootView.findViewById(R.id.rss_recycler_view)
    private val emptyState: View = rootView.findViewById(R.id.rss_empty_state)
    private val emptyStateTitle: TextView = rootView.findViewById(R.id.rss_empty_title)
    private val emptyStateMessage: TextView = rootView.findViewById(R.id.rss_empty_message)
    private val refreshButton: View = rootView.findViewById(R.id.rss_refresh_button)
    private val manageButton: View = rootView.findViewById(R.id.rss_manage_button)
    private val headerButton: View = rootView.findViewById(R.id.rss_header)
    private val topicChips: ChipGroup = rootView.findViewById(R.id.rss_topic_chips)
    private val adapter = RssFeedAdapter()
    private var allArticles: List<RssArticle> = emptyList()
    private var selectedCategory: String? = null

    fun setup() {
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        updateTypography()


        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null

        swipeRefreshLayout.isEnabled = true
        swipeRefreshLayout.setOnRefreshListener {
            refresh()
        }
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            recyclerView.canScrollVertically(-1)
        }

        refreshButton.setOnClickListener {
            refresh()
        }

        manageButton.setOnClickListener {
            openSettings(activity)
        }

        headerButton.setOnClickListener {
            manageButton.performClick()
        }

        activity.backgroundExecutor.execute {
            val cachedArticles = activity.rssFeedManager.getCachedArticles()
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    renderArticles(cachedArticles)
                }
            }
        }
    }

    fun updateTypography() {
        val prefs = activity.sharedPreferences
        val scale = prefs.getInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, 100) / 100f
        val style = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default"
        val intensity = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular") ?: "regular"
        val color = TypographyManager.getConfiguredFontColor(activity)
        adapter.updateTypography(scale, style, intensity, color)
        
        TypographyManager.applyToViewTree(rootView.findViewById(R.id.rss_header), scale, style, intensity, color)
        TypographyManager.applyToViewTree(emptyState, scale, style, intensity, color)
    }

    fun refresh() {
        if (!activity.sharedPreferences.getBoolean(Constants.Prefs.RSS_PAGE_ENABLED, true)) {
            renderArticles(emptyList())
            swipeRefreshLayout.isRefreshing = false
            return
        }

        swipeRefreshLayout.isRefreshing = true
        val callback: (List<com.guruswarupa.launch.managers.RssArticle>) -> Unit = { articles ->
            swipeRefreshLayout.isRefreshing = false
            renderArticles(articles)
        }

        activity.rssFeedManager.refreshFeeds(callback)
    }

    private fun renderArticles(articles: List<RssArticle>) {
        allArticles = articles
        activity.backgroundExecutor.execute {
            val categories = activity.rssFeedManager.getEnabledCategories()
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    updateTopicChipsWithCategories(categories)
                    val filteredArticles = filterArticles()
                    adapter.submitArticles(filteredArticles)
                    applyEmptyState(filteredArticles)
                }
            }
        }
    }

    private fun applyEmptyState(filteredArticles: List<RssArticle>) {
        val hasFeeds = activity.rssFeedManager.getFeedUrls().isNotEmpty()
        recyclerView.isVisible = filteredArticles.isNotEmpty()
        emptyState.isVisible = filteredArticles.isEmpty()
        when {
            !activity.sharedPreferences.getBoolean(Constants.Prefs.RSS_PAGE_ENABLED, true) -> {
                emptyStateTitle.text = activity.getString(R.string.lbl_news_feed_is_disabled)
                emptyStateMessage.text = activity.getString(R.string.lbl_turn_it_back_on_from_settings_whenever_you_want)
            }
            !hasFeeds -> {
                emptyStateTitle.text = activity.getString(R.string.no_news_sources_enabled)
                emptyStateMessage.text = activity.getString(R.string.lbl_use_the_settings_gear_above_to_switch_on_the_sou)
            }
            selectedCategory != null -> {
                emptyStateTitle.text = activity.getString(R.string.lbl_no_articles, selectedCategory.orEmpty())
                emptyStateMessage.text = activity.getString(R.string.lbl_try_another_topic_refresh_the_page_or_check_your)
                return
            }
            else -> {
                emptyStateTitle.text = activity.getString(R.string.lbl_no_articles_right_now)
                emptyStateMessage.text = activity.getString(R.string.lbl_refresh_the_page_or_use_the_settings_gear_above)
                return
            }
        }
    }

    private fun applySelectedCategory(category: String?) {
        selectedCategory = category
        val filteredArticles = filterArticles()
        adapter.submitArticles(filteredArticles)
        applyEmptyState(filteredArticles)
    }

    private fun updateTopicChipsWithCategories(categories: List<String>) {
        topicChips.isVisible = categories.isNotEmpty()
        if (categories.isEmpty()) {
            selectedCategory = null
            topicChips.removeAllViews()
            return
        }
        if (selectedCategory !in categories) {
            selectedCategory = categories.firstOrNull()
        }
        topicChips.setOnCheckedStateChangeListener(null)
        topicChips.removeAllViews()
        categories.forEachIndexed { index, category ->
            val chip = Chip(activity).apply {
                id = View.generateViewId()
                text = category
                isCheckable = true
                isCheckedIconVisible = false
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(ThemeManager.color(activity, R.attr.appSurfaceVariant))
                setTextColor(android.content.res.ColorStateList.valueOf(ThemeManager.color(activity, R.attr.appTextPrimary)))
                chipStrokeWidth = activity.resources.displayMetrics.density
                chipStrokeColor = android.content.res.ColorStateList.valueOf(ThemeManager.color(activity, R.attr.appOutline))
                if (index > 0) {
                    (layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart = 0
                }
                setOnClickListener {
                    if (selectedCategory != category) {
                        applySelectedCategory(category)
                    } else {
                        applySelectedCategory(selectedCategory)
                    }
                    if (!isChecked) {
                        topicChips.check(id)
                    }
                }
            }
            topicChips.addView(chip)
            if (category == selectedCategory) {
                topicChips.check(chip.id)
            }
        }
        topicChips.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedId) ?: return@setOnCheckedStateChangeListener
            applySelectedCategory(chip.text.toString())
        }
    }

    private fun filterArticles(): List<RssArticle> {
        val category = selectedCategory ?: return allArticles
        return allArticles.filter { it.category == category }
    }

    companion object {
        fun openSettings(context: Context) {
            val intent = Intent(context, com.guruswarupa.launch.ui.activities.RssFeedSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun showManageFeedsDialog(
            context: Context,
            sharedPreferences: SharedPreferences,
            rssFeedManager: RssFeedManager? = null,
            onFeedsChanged: (() -> Unit)? = null
        ) {
            val urls = RssFeedManager.getStoredFeedUrls(sharedPreferences).toMutableList()
            val listView = ListView(context).apply {
                divider = null
                setPadding(0, 16, 0, 16)
            }
            val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, urls)
            listView.adapter = adapter

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 20, 40, 8)
            }

            val title = TextView(context).apply {
                text = "Manage RSS Feeds"
                textSize = 18f
                setTextColor(ThemeManager.color(context, R.attr.appTextPrimary))
            }
            val subtitle = TextView(context).apply {
                text = "Tap a source to remove it, or add a new RSS or Atom URL."
                textSize = 13f
                setTextColor(ThemeManager.color(context, R.attr.appTextSecondary))
                setPadding(0, 8, 0, 20)
            }

            container.addView(title)
            container.addView(subtitle)
            container.addView(
                listView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (context.resources.displayMetrics.density * 220).toInt()
                )
            )

            val dialog = AlertDialog.Builder(context, R.style.Theme_Launch_Settings)
                .setView(container)
                .setPositiveButton(context.getString(R.string.add_button)) { _, _ -> }
                .setNegativeButton(context.getString(R.string.support_thank_you_close), null)
                .create()

            listView.setOnItemClickListener { _, _, position, _ ->
                val selectedUrl = urls[position]
                AlertDialog.Builder(context, R.style.Theme_Launch_Settings)
                    .setTitle(context.getString(R.string.dlg_remove_feed))
                    .setMessage(selectedUrl)
                    .setPositiveButton(context.getString(R.string.font_remove)) { _, _ ->
                        urls.removeAt(position)
                        RssFeedManager.storeFeedUrls(sharedPreferences, urls)
                        rssFeedManager?.removeFeedUrl(selectedUrl)
                        onFeedsChanged?.invoke()
                    }
                    .setNegativeButton(context.getString(R.string.cancel_button), null)
                    .show()
                dialog.dismiss()
            }

            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val input = EditText(context).apply {
                        hint = "https://example.com/feed.xml"
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                        setSingleLine()
                    }
                    AlertDialog.Builder(context, R.style.Theme_Launch_Settings)
                        .setTitle(context.getString(R.string.dlg_add_rss_feed))
                        .setView(input)
                        .setPositiveButton(context.getString(R.string.save_button)) { _, _ ->
                            val success = rssFeedManager?.addFeedUrl(input.text.toString())
                                ?: run {
                                    val newUrl = input.text.toString().trim()
                                    if (newUrl.isBlank() || !(newUrl.startsWith("http://") || newUrl.startsWith("https://"))) {
                                        false
                                    } else {
                                        val updatedUrls = RssFeedManager.getStoredFeedUrls(sharedPreferences).toMutableList()
                                        if (updatedUrls.any { it.equals(newUrl, ignoreCase = true) }) {
                                            false
                                        } else {
                                            updatedUrls.add(newUrl)
                                            RssFeedManager.storeFeedUrls(sharedPreferences, updatedUrls)
                                            true
                                        }
                                    }
                                }
                            if (success) {
                                onFeedsChanged?.invoke()
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_enter_a_valid_unique_feed_url), Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton(context.getString(R.string.cancel_button), null)
                        .show()
                }
            }

            dialog.show()
        }
    }
}
