package com.guruswarupa.launch.managers

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import com.guruswarupa.launch.services.LaunchNotificationListenerService

data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long
)

interface MediaSessionListener {
    fun onTrackChanged(track: NowPlaying?)
    fun onPlaybackStateChanged(state: PlaybackState?)
}

/**
 * Single shared observer of the device's active media session, backed by the notification-
 * listener access the app already requires for media controls. Multiple consumers (the media
 * controller widget, the wallpaper-page lyrics view) can subscribe without each registering
 * their own MediaSessionManager listener.
 */
class MediaSessionMonitor(private val context: Context) {
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val componentName = ComponentName(context, LaunchNotificationListenerService::class.java)
    private var sessionListenerRegistered = false

    var activeController: MediaController? = null
        private set

    private val listeners = mutableListOf<MediaSessionListener>()

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            listeners.forEach { it.onPlaybackStateChanged(state) }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val track = metadata.toNowPlaying()
            listeners.forEach { it.onTrackChanged(track) }
        }
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        adopt(controllers?.firstOrNull())
    }

    fun addListener(listener: MediaSessionListener) {
        if (listeners.isEmpty()) {
            registerSessionListener()
            refresh()
        }
        listeners.add(listener)
        listener.onTrackChanged(activeController?.metadata.toNowPlaying())
        listener.onPlaybackStateChanged(activeController?.playbackState)
    }

    fun removeListener(listener: MediaSessionListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            unregisterSessionListener()
        }
    }

    private fun registerSessionListener() {
        if (!sessionListenerRegistered && isNotificationListenerEnabled()) {
            try {
                mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, componentName)
                sessionListenerRegistered = true
            } catch (_: SecurityException) {
                sessionListenerRegistered = false
            }
        }
    }

    private fun unregisterSessionListener() {
        if (sessionListenerRegistered) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener)
            } catch (_: Exception) {
            }
            sessionListenerRegistered = false
        }
    }

    /** Re-pick the active session. Safe to call repeatedly (e.g. on every onResume). */
    fun refresh() {
        if (!isNotificationListenerEnabled()) return
        try {
            if (!sessionListenerRegistered) {
                registerSessionListener()
            }
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            adopt(controllers?.firstOrNull())
        } catch (_: SecurityException) {
        }
    }

    private fun adopt(newController: MediaController?) {
        if (newController == activeController) return
        activeController?.unregisterCallback(callback)
        activeController = newController
        activeController?.registerCallback(callback)

        val track = activeController?.metadata.toNowPlaying()
        val state = activeController?.playbackState
        listeners.forEach {
            it.onTrackChanged(track)
            it.onPlaybackStateChanged(state)
        }
    }

    /**
     * PlaybackState.getPosition() is a snapshot taken at getLastPositionUpdateTime() (on the
     * elapsedRealtime clock) - extrapolate forward while playing so a UI ticker can stay smooth
     * without polling the system for position on every frame.
     */
    fun estimatedPositionMs(): Long {
        val state = activeController?.playbackState ?: return 0L
        if (state.state != PlaybackState.STATE_PLAYING) return state.position
        val delta = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return (state.position + (delta * state.playbackSpeed).toLong()).coerceAtLeast(0L)
    }

    fun isNotificationListenerEnabled(): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (flat.isNullOrEmpty()) return false
        return flat.split(":").any { name ->
            ComponentName.unflattenFromString(name)?.packageName == packageName
        }
    }

    fun cleanup() {
        activeController?.unregisterCallback(callback)
        activeController = null
        unregisterSessionListener()
        listeners.clear()
    }

    private fun MediaMetadata?.toNowPlaying(): NowPlaying? {
        this ?: return null
        return NowPlaying(
            title = getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            album = getString(MediaMetadata.METADATA_KEY_ALBUM),
            durationMs = getLong(MediaMetadata.METADATA_KEY_DURATION)
        )
    }
}
