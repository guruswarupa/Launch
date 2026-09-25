package com.guruswarupa.launch.ui

import android.app.Dialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.LrcParser
import com.guruswarupa.launch.managers.LyricsManager
import com.guruswarupa.launch.managers.LyricsResult
import com.guruswarupa.launch.managers.MediaSessionListener
import com.guruswarupa.launch.managers.NowPlaying
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.theme.ThemeManager
import com.guruswarupa.launch.utils.dpToPx

class WallpaperMediaController(
    private val activity: MainActivity,
    rootView: View
) : MediaSessionListener {

    private val controlsContainer: LinearLayout = rootView.findViewById(R.id.wallpaper_media_controls_container)
    private val prevBtn: ImageButton = rootView.findViewById(R.id.wallpaper_media_prev)
    private val playPauseBtn: ImageButton = rootView.findViewById(R.id.wallpaper_media_play_pause)
    private val nextBtn: ImageButton = rootView.findViewById(R.id.wallpaper_media_next)

    private val lyricsContainer: LinearLayout = rootView.findViewById(R.id.wallpaper_lyrics_container)
    private val prevText: TextView = rootView.findViewById(R.id.lyrics_prev)
    private val currentText: TextView = rootView.findViewById(R.id.lyrics_current)
    private val nextText: TextView = rootView.findViewById(R.id.lyrics_next)

    private val permissionPrompt: LinearLayout = rootView.findViewById(R.id.wallpaper_media_permission_prompt)

    private val lyricsManager by lazy { LyricsManager(activity, activity.backgroundExecutor) }
    private val tickHandler = Handler(Looper.getMainLooper())

    private val selectableItemBackgroundResId: Int by lazy {
        val typedValue = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        typedValue.resourceId
    }

    private fun newSelectableItemBackground(): Drawable? =
        androidx.core.content.ContextCompat.getDrawable(activity, selectableItemBackgroundResId)

    private var currentTrack: NowPlaying? = null
    private var lyricsResult: LyricsResult? = null
    private var latestPlaybackState: PlaybackState? = null
    private var lastLineIndex = Int.MIN_VALUE
    private var listenerAttached = false
    private var pageVisible = false
    private var activityResumed = true

    private var fullLyricsDialog: Dialog? = null
    private var fullLyricsScrollView: NestedScrollView? = null
    private val fullLyricsLineViews = mutableListOf<TextView>()
    private var fullLyricsLastIndex = Int.MIN_VALUE

    private val fullLyricsTicker = object : Runnable {
        override fun run() {
            updateFullLyricsHighlight()
            tickHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateCurrentLine()
            tickHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    init {
        prevBtn.setOnClickListener { activity.mediaSessionMonitor.activeController?.transportControls?.skipToPrevious() }
        nextBtn.setOnClickListener { activity.mediaSessionMonitor.activeController?.transportControls?.skipToNext() }
        playPauseBtn.setOnClickListener {
            val controller = activity.mediaSessionMonitor.activeController ?: return@setOnClickListener
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }
        permissionPrompt.setOnClickListener { openNotificationSettings() }
        lyricsContainer.setOnClickListener { showFullLyricsDialog() }
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            Toast.makeText(activity, activity.getString(R.string.toast_enable_launch_in_the_list), Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(activity, activity.getString(R.string.toast_could_not_open_settings), Toast.LENGTH_SHORT).show()
        }
    }

    fun setup() {

    }

    fun onPageShown() {
        pageVisible = true
        updateActiveState()
    }

    fun onPageHidden() {
        pageVisible = false
        updateActiveState()
    }

    fun onActivityResume() {
        activityResumed = true
        updateActiveState()
    }

    fun onActivityPause() {
        activityResumed = false
        updateActiveState()
    }

    fun onActivityDestroy() {
        stopTicker()
        tickHandler.removeCallbacks(fullLyricsTicker)
        fullLyricsDialog?.dismiss()
        fullLyricsDialog = null
        if (listenerAttached) {
            activity.mediaSessionMonitor.removeListener(this)
            listenerAttached = false
        }
    }

    private fun showFullLyricsDialog() {
        if (fullLyricsDialog?.isShowing == true) return
        val result = lyricsResult
        if (result == null || result is LyricsResult.NotFound) return

        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_full_lyrics, null)
        val titleView = dialogView.findViewById<TextView>(R.id.full_lyrics_title)
        val subtitleView = dialogView.findViewById<TextView>(R.id.full_lyrics_subtitle)
        val closeButton = dialogView.findViewById<ImageButton>(R.id.full_lyrics_close)
        val scrollView = dialogView.findViewById<NestedScrollView>(R.id.full_lyrics_scroll)
        val linesContainer = dialogView.findViewById<LinearLayout>(R.id.full_lyrics_lines_container)

        val primaryColor = ThemeManager.color(activity, R.attr.appTextPrimary)
        val secondaryColor = ThemeManager.color(activity, R.attr.appTextSecondary)

        titleView.setTextColor(primaryColor)
        titleView.text = currentTrack?.title.orEmpty()
        subtitleView.setTextColor(secondaryColor)
        subtitleView.text = currentTrack?.artist.orEmpty()

        fullLyricsLineViews.clear()
        fullLyricsLastIndex = Int.MIN_VALUE
        fullLyricsScrollView = scrollView

        when (result) {
            is LyricsResult.Synced -> {
                result.lines.forEachIndexed { index, line ->
                    val lineView = TextView(activity).apply {
                        text = line.text.ifBlank { "♪" }
                        gravity = Gravity.CENTER
                        textSize = 17f
                        setPadding(0, activity.dpToPx(6), 0, activity.dpToPx(6))
                        setTextColor(secondaryColor)
                        alpha = 0.6f
                        isClickable = true
                        isFocusable = true
                        background = newSelectableItemBackground()

                        setOnClickListener {
                            activity.mediaSessionMonitor.activeController?.transportControls?.seekTo(line.timeMs)
                            applyFullLyricsHighlight(index, animateScroll = true)
                        }
                    }
                    fullLyricsLineViews.add(lineView)
                    linesContainer.addView(lineView)
                }
            }
            is LyricsResult.Plain -> {
                val textView = TextView(activity).apply {
                    text = result.text
                    textSize = 16f
                    setTextColor(primaryColor)
                    setLineSpacing(activity.dpToPx(6).toFloat(), 1f)
                }
                linesContainer.addView(textView)
            }
            LyricsResult.NotFound -> return
        }

        val backgroundColor = ThemeManager.color(activity, R.attr.appBackground)
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar)
        dialog.setContentView(dialogView)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(backgroundColor))
            statusBarColor = backgroundColor
            navigationBarColor = backgroundColor
        }
        dialog.window?.let { window ->
            WindowCompat.getInsetsController(window, dialogView).apply {
                isAppearanceLightStatusBars = ThemeManager.isLight(activity)
                isAppearanceLightNavigationBars = ThemeManager.isLight(activity)
            }
        }
        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            tickHandler.removeCallbacks(fullLyricsTicker)
            fullLyricsLineViews.clear()
            fullLyricsScrollView = null
            fullLyricsDialog = null
        }
        fullLyricsDialog = dialog
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        if (result is LyricsResult.Synced) {

            scrollView.post { updateFullLyricsHighlight(animateScroll = false) }
            tickHandler.post(fullLyricsTicker)
        }
    }

    private fun updateFullLyricsHighlight(animateScroll: Boolean = true) {
        val lines = (lyricsResult as? LyricsResult.Synced)?.lines ?: return
        val posMs = activity.mediaSessionMonitor.estimatedPositionMs()
        val index = LrcParser.indexAt(lines, posMs)
        applyFullLyricsHighlight(index, animateScroll)
    }

    private fun applyFullLyricsHighlight(index: Int, animateScroll: Boolean) {
        val scrollView = fullLyricsScrollView ?: return
        if (index == fullLyricsLastIndex) return
        fullLyricsLastIndex = index

        val primaryColor = ThemeManager.color(activity, R.attr.appTextPrimary)
        val secondaryColor = ThemeManager.color(activity, R.attr.appTextSecondary)

        fullLyricsLineViews.forEachIndexed { i, view ->
            val isCurrent = i == index
            view.setTextColor(if (isCurrent) primaryColor else secondaryColor)
            view.alpha = if (isCurrent) 1f else 0.6f
            view.setTypeface(view.typeface, if (isCurrent) Typeface.BOLD else Typeface.NORMAL)
        }

        val target = fullLyricsLineViews.getOrNull(index) ?: return
        val targetY = (target.top - scrollView.height / 2 + target.height / 2).coerceAtLeast(0)
        if (animateScroll) {
            scrollView.smoothScrollTo(0, targetY)
        } else {
            scrollView.post { scrollView.scrollTo(0, targetY) }
        }
    }

    fun onSettingsUpdated() {
        updateLyricsVisibility()
    }

    private fun isLyricsFeatureEnabled(): Boolean =
        activity.sharedPreferences.getBoolean(Constants.Prefs.WALLPAPER_LYRICS_ENABLED, true)

    private fun updateActiveState() {
        val shouldListen = pageVisible && activityResumed

        val hasPermission = activity.mediaSessionMonitor.isNotificationListenerEnabled()
        permissionPrompt.visibility = if (shouldListen && !hasPermission) View.VISIBLE else View.GONE

        val shouldListenNow = shouldListen && hasPermission
        if (shouldListenNow && !listenerAttached) {
            activity.mediaSessionMonitor.addListener(this)
            listenerAttached = true
        } else if (!shouldListenNow && listenerAttached) {
            activity.mediaSessionMonitor.removeListener(this)
            listenerAttached = false

            currentTrack = null
            lyricsResult = null
            latestPlaybackState = null
            lastLineIndex = Int.MIN_VALUE
            hideLyrics()
            updateControlsVisibility()
        }
        updateTickerState()
    }

    override fun onTrackChanged(track: NowPlaying?) {
        updateControlsVisibility(track)
        if (track == currentTrack) return
        currentTrack = track
        lyricsResult = null
        lastLineIndex = Int.MIN_VALUE
        hideLyrics()

        if (track != null && track.title.isNotBlank() && track.artist.isNotBlank()) {
            lyricsManager.fetch(track) { result ->
                if (track == currentTrack) {
                    lyricsResult = result
                    renderLyrics()
                }
            }
        }
        updateTickerState()
    }

    override fun onPlaybackStateChanged(state: PlaybackState?) {
        latestPlaybackState = state
        playPauseBtn.setImageResource(
            if (state?.state == PlaybackState.STATE_PLAYING) R.drawable.ic_pause else R.drawable.ic_play
        )
        updateTickerState()
    }

    private fun updateControlsVisibility(track: NowPlaying? = currentTrack) {
        controlsContainer.visibility = if (track != null) View.VISIBLE else View.GONE
    }

    private fun updateLyricsVisibility() {
        if (!isLyricsFeatureEnabled()) {
            hideLyrics()
        } else {
            renderLyrics()
        }
    }

    private fun renderLyrics() {
        if (!isLyricsFeatureEnabled()) {
            hideLyrics()
            return
        }
        when (val result = lyricsResult) {
            is LyricsResult.Synced -> {
                lyricsContainer.visibility = View.VISIBLE
                lastLineIndex = Int.MIN_VALUE
                updateCurrentLine()
            }
            is LyricsResult.Plain -> {
                lyricsContainer.visibility = View.VISIBLE
                prevText.text = ""
                nextText.text = ""
                currentText.text = result.text
            }
            LyricsResult.NotFound, null -> hideLyrics()
        }
        updateTickerState()
    }

    private fun updateCurrentLine() {
        val lines = (lyricsResult as? LyricsResult.Synced)?.lines ?: return
        val posMs = activity.mediaSessionMonitor.estimatedPositionMs()
        val index = LrcParser.indexAt(lines, posMs)
        if (index == lastLineIndex) return
        lastLineIndex = index

        prevText.text = lines.getOrNull(index - 1)?.text.orEmpty()
        currentText.text = lines.getOrNull(index)?.text.orEmpty()
        nextText.text = lines.getOrNull(index + 1)?.text.orEmpty()

        currentText.alpha = 0f
        currentText.animate().alpha(1f).setDuration(CROSSFADE_MS).start()
    }

    private fun hideLyrics() {
        lyricsContainer.visibility = View.GONE
    }

    private fun updateTickerState() {
        val hasSyncedLyrics = isLyricsFeatureEnabled() && lyricsResult is LyricsResult.Synced
        val isPlaying = latestPlaybackState?.state == PlaybackState.STATE_PLAYING
        if (listenerAttached && hasSyncedLyrics && isPlaying) startTicker() else stopTicker()
    }

    private fun startTicker() {
        tickHandler.removeCallbacks(ticker)
        tickHandler.post(ticker)
    }

    private fun stopTicker() {
        tickHandler.removeCallbacks(ticker)
    }

    companion object {
        private const val TICK_INTERVAL_MS = 250L
        private const val CROSSFADE_MS = 150L
    }
}
