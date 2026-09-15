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

/**
 * A now-playing presence: a small floating transport-control pill plus, just below it,
 * auto-scrolling synced lyrics - used both on the wallpaper page (see
 * ScreenPagerManager.Page.WALLPAPER) and, as a second instance, inside Stock's home top widget
 * (see MainActivity.stockTopWidgetMediaController). Both pieces share one MediaSessionMonitor
 * subscription. The controls pill shows whenever a media session exists; the lyrics block
 * additionally requires the Settings toggle (on by default) and a lyrics match. When
 * notification listener access - what the whole media session depends on - isn't granted, a
 * tappable prompt takes this spot instead of it just staying silently blank.
 */
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
    // A resource id (not a Drawable) - Drawables are stateful (ripple bounds/state), so this is
    // resolved once but a fresh instance is created per line view via newSelectableItemBackground().
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

    /** Same notification-listener request flow used elsewhere in the app (e.g. PermissionManager). */
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
        // Listening starts lazily from onPageShown/onActivityResume so an idle launcher never
        // registers a media-session listener for a page that isn't displayed.
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

    /** Opens the complete lyrics: synced lines with the current one highlighted and
     *  auto-scrolled into view, or the plain text as one readable scrollable paragraph. */
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
                        // Tapping a line seeks the song there - only meaningful because each line
                        // here has a real timestamp (LyricsResult.Synced); plain/unsynced lyrics
                        // have no per-line timing to seek to, so they get no click handling at all.
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

        // A plain fullscreen Dialog rather than the app's usual floating CustomDialogTheme card -
        // this needs to cover the whole screen with an opaque background, not a dimmed home
        // screen behind a small centered popup. Theme_Black_NoTitleBar (not the _Fullscreen
        // variant) keeps the status bar showing rather than hiding it outright, so it can be
        // recolored to match instead of just disappearing.
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
            // Deferred until after the dialog is shown/attached, so scrollView.height and each
            // line's measured position are real numbers rather than the pre-layout zeroes an
            // un-attached view would report.
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

    /** Shared by the position ticker and by tapping a line to seek - both just need to move the
     *  highlight/scroll to a given line index, only the source of that index differs. */
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
        // Notification listener access is what backs the whole media session - without it this
        // spot used to just stay permanently blank with nothing telling the user why. Surface a
        // tappable prompt in its place instead whenever this spot would otherwise be showing.
        val hasPermission = activity.mediaSessionMonitor.isNotificationListenerEnabled()
        permissionPrompt.visibility = if (shouldListen && !hasPermission) View.VISIBLE else View.GONE

        val shouldListenNow = shouldListen && hasPermission
        if (shouldListenNow && !listenerAttached) {
            activity.mediaSessionMonitor.addListener(this)
            listenerAttached = true
        } else if (!shouldListenNow && listenerAttached) {
            activity.mediaSessionMonitor.removeListener(this)
            listenerAttached = false
            // Reset so re-attaching later (song unchanged) still re-fetches/re-renders instead
            // of short-circuiting on an unchanged NowPlaying and leaving the view hidden.
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
