package com.guruswarupa.launch.ui

import android.content.Intent
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.LrcParser
import com.guruswarupa.launch.managers.LyricsManager
import com.guruswarupa.launch.managers.LyricsResult
import com.guruswarupa.launch.managers.MediaSessionListener
import com.guruswarupa.launch.managers.NowPlaying
import com.guruswarupa.launch.models.Constants

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

    private var currentTrack: NowPlaying? = null
    private var lyricsResult: LyricsResult? = null
    private var latestPlaybackState: PlaybackState? = null
    private var lastLineIndex = Int.MIN_VALUE
    private var listenerAttached = false
    private var pageVisible = false
    private var activityResumed = true

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
        if (listenerAttached) {
            activity.mediaSessionMonitor.removeListener(this)
            listenerAttached = false
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
