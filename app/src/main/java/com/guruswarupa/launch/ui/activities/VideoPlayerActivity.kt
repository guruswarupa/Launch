package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.document.BinaryConverters
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class VideoPlayerActivity : VaultBaseActivity() {

    private lateinit var root: View
    private lateinit var playerView: PlayerView
    private lateinit var controlsTop: View
    private lateinit var controlsCenter: View
    private lateinit var controlsBottom: View
    private lateinit var btnPlay: ImageView
    private lateinit var btnLock: ImageView
    private lateinit var btnUnlock: ImageView
    private lateinit var btnFullscreen: ImageView
    private lateinit var btnLoop: ImageView
    private lateinit var btnSpeed: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var timeCurrent: TextView
    private lateinit var timeTotal: TextView
    private lateinit var buffering: View
    private lateinit var indicator: View
    private lateinit var indicatorIcon: ImageView
    private lateinit var indicatorText: TextView
    private lateinit var seekPreview: TextView
    private lateinit var flashLeft: TextView
    private lateinit var flashRight: TextView
    private lateinit var audioPanel: View

    private var player: ExoPlayer? = null
    private var file: File? = null
    private var sourceUri: Uri? = null
    private var displayName = ""
    private var deleteOnExit = false
    private var isAudio = false

    private var controlsVisible = true
    private var locked = false
    private var landscapeForced = false
    private var userSeeking = false
    private var loopEnabled = false
    private var speed = 1f
    private var zoomFill = false
    private var longPressBoost = false

    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { setControlsVisible(false) }
    private val hideIndicator = Runnable { indicator.animate().alpha(0f).setDuration(200).start() }
    private val progressTick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 250)
        }
    }

    private lateinit var audioManager: AudioManager
    private var gestureMode = 0
    private var startBrightness = 0.5f
    private var startVolume = 0
    private var seekStartMs = 0L
    private var seekTargetMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_video_player)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        bindViews()

        val viaAlias = intent.component?.className != VideoPlayerActivity::class.java.name
        val data = intent.data
        val target: File?
        if (viaAlias || data != null && intent.getStringExtra(EXTRA_PATH) == null) {
            if (data == null) {
                Toast.makeText(this, R.string.media_unable_to_play, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            sourceUri = data
            target = null
            displayName = queryDisplayName(data)
            deleteOnExit = false
            val type = contentResolver.getType(data).orEmpty()
            isAudio = type.startsWith("audio/") || displayName.substringAfterLast('.', "").lowercase() in BinaryConverters.AUDIO_EXTENSIONS
        } else {
            val path = intent.getStringExtra(EXTRA_PATH)
            displayName = intent.getStringExtra(EXTRA_NAME).orEmpty()
            deleteOnExit = intent.getBooleanExtra(EXTRA_DELETE, false)
            target = path?.let { File(it) }
            if (target == null || !target.exists()) {
                Toast.makeText(this, R.string.media_unable_to_play, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            file = target
            sourceUri = Uri.fromFile(target)
            if (displayName.isEmpty()) displayName = target.name
            isAudio = target.extension.lowercase() in BinaryConverters.AUDIO_EXTENSIONS
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            controlsTop.setPadding(bars.left + dp(8), bars.top + dp(8), bars.right + dp(12), controlsTop.paddingBottom)
            controlsBottom.setPadding(bars.left, controlsBottom.paddingTop, bars.right, bars.bottom + dp(8))
            insets
        }

        findViewById<TextView>(R.id.video_title).text = displayName
        setupPlayer(sourceUri!!)
        setupButtons()
        setupGestures()
        if (isAudio) setupAudioPanel(sourceUri!!)
        handler.post(progressTick)
        scheduleHide()
    }

    private fun bindViews() {
        root = findViewById(R.id.player_root)
        playerView = findViewById(R.id.player_view)
        controlsTop = findViewById(R.id.controls_top)
        controlsCenter = findViewById(R.id.controls_center)
        controlsBottom = findViewById(R.id.controls_bottom)
        btnPlay = findViewById(R.id.btn_play)
        btnLock = findViewById(R.id.btn_lock)
        btnUnlock = findViewById(R.id.btn_unlock)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        btnLoop = findViewById(R.id.btn_loop)
        btnSpeed = findViewById(R.id.btn_speed)
        seekBar = findViewById(R.id.seek_bar)
        timeCurrent = findViewById(R.id.time_current)
        timeTotal = findViewById(R.id.time_total)
        buffering = findViewById(R.id.buffering)
        indicator = findViewById(R.id.indicator)
        indicatorIcon = findViewById(R.id.indicator_icon)
        indicatorText = findViewById(R.id.indicator_text)
        seekPreview = findViewById(R.id.seek_preview)
        flashLeft = findViewById(R.id.seek_flash_left)
        flashRight = findViewById(R.id.seek_flash_right)
        audioPanel = findViewById(R.id.audio_panel)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun queryDisplayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "video" }
    }

    private fun setupPlayer(source: Uri) {
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo
        exo.setMediaItem(MediaItem.fromUri(source))
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Player.STATE_ENDED) {
                    setControlsVisible(true)
                    handler.removeCallbacks(hideControls)
                }
                updatePlayIcon()
                updateProgress()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayIcon()
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    scheduleHide()
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@VideoPlayerActivity, R.string.media_unable_to_play, Toast.LENGTH_LONG).show()
                openWith()
                finish()
            }
        })
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        btnPlay.setOnClickListener { togglePlay() }
        findViewById<View>(R.id.btn_rewind).setOnClickListener { seekBy(-10_000) }
        findViewById<View>(R.id.btn_forward).setOnClickListener { seekBy(10_000) }
        btnLock.setOnClickListener { setLocked(true) }
        btnUnlock.setOnClickListener { setLocked(false) }
        btnFullscreen.setOnClickListener { toggleOrientation() }
        btnLoop.setOnClickListener {
            loopEnabled = !loopEnabled
            player?.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            btnLoop.alpha = if (loopEnabled) 1f else 0.6f
            scheduleHide()
        }
        btnSpeed.setOnClickListener { showSpeedMenu(it) }
        findViewById<View>(R.id.btn_share).setOnClickListener { share() }
        findViewById<View>(R.id.btn_more).setOnClickListener { showMoreMenu(it) }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player?.duration ?: 0L
                    if (duration > 0) timeCurrent.text = formatTime(duration * progress / 1000)
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                userSeeking = true
                handler.removeCallbacks(hideControls)
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                val duration = player?.duration ?: 0L
                if (duration > 0 && bar != null) player?.seekTo(duration * bar.progress / 1000)
                userSeeking = false
                scheduleHide()
            }
        })
    }

    private fun setupAudioPanel(source: Uri) {
        audioPanel.visibility = View.VISIBLE
        playerView.visibility = View.INVISIBLE
        val title = findViewById<TextView>(R.id.audio_title)
        val artist = findViewById<TextView>(R.id.audio_artist)
        title.text = displayName.substringBeforeLast('.')
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, source)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title.text = it }
            artist.text = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                if (bitmap != null) {
                    val image = findViewById<ImageView>(R.id.audio_art)
                    image.setPadding(0, 0, 0, 0)
                    image.setImageBitmap(bitmap)
                }
            }
            retriever.release()
        } catch (_: Exception) {
        }
        controlsVisible = true
    }

    private fun setupGestures() {
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (locked || isAudio) return false
                if (detector.scaleFactor > 1.04f && !zoomFill) {
                    zoomFill = true
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else if (detector.scaleFactor < 0.96f && zoomFill) {
                    zoomFill = false
                    playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                return true
            }
        })
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                gestureMode = 0
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!locked) setControlsVisible(!controlsVisible || isAudio)
                else showUnlock()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true
                val width = root.width
                when {
                    e.x < width / 3f -> { seekBy(-10_000); flash(flashLeft) }
                    e.x > width * 2f / 3f -> { seekBy(10_000); flash(flashRight) }
                    else -> togglePlay()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (locked) return
                longPressBoost = true
                player?.setPlaybackSpeed(2f)
                showIndicator(R.drawable.ic_media_fullscreen, "2x")
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (locked || e1 == null || scaleDetector.isInProgress) return false
                if (gestureMode == 0) {
                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y
                    if (abs(dx) < dp(12) && abs(dy) < dp(12)) return false
                    gestureMode = when {
                        abs(dx) > abs(dy) -> if (isAudio) -1 else 1
                        e1.x < root.width / 2f -> 2
                        else -> 3
                    }
                    when (gestureMode) {
                        1 -> {
                            seekStartMs = player?.currentPosition ?: 0L
                            seekTargetMs = seekStartMs
                        }
                        2 -> startBrightness = currentBrightness()
                        3 -> startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    }
                }
                val totalDx = e2.x - e1.x
                val totalDy = e1.y - e2.y
                when (gestureMode) {
                    1 -> {
                        val duration = max(player?.duration ?: 0L, 0L)
                        val delta = (totalDx / root.width * 90_000).toLong()
                        seekTargetMs = (seekStartMs + delta).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
                        seekPreview.text = "${formatTime(seekTargetMs)} / ${formatTime(duration)}"
                        seekPreview.alpha = 1f
                    }
                    2 -> {
                        val value = (startBrightness + totalDy / root.height * 1.4f).coerceIn(0.02f, 1f)
                        val attrs = window.attributes
                        attrs.screenBrightness = value
                        window.attributes = attrs
                        showIndicator(R.drawable.ic_media_brightness, "${(value * 100).toInt()}%")
                    }
                    3 -> {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val value = (startVolume + totalDy / root.height * maxVol * 1.4f).toInt().coerceIn(0, maxVol)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                        showIndicator(R.drawable.ic_media_volume, "${(value * 100 / max(maxVol, 1))}%")
                    }
                }
                return true
            }
        })
        root.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            detector.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (gestureMode == 1) {
                    player?.seekTo(seekTargetMs)
                    seekPreview.animate().alpha(0f).setDuration(160).start()
                }
                if (longPressBoost) {
                    longPressBoost = false
                    player?.setPlaybackSpeed(speed)
                    handler.postDelayed(hideIndicator, 300)
                }
                gestureMode = 0
            }
            true
        }
    }

    private fun currentBrightness(): Float {
        val current = window.attributes.screenBrightness
        if (current >= 0f) return current
        return try {
            android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Exception) {
            0.5f
        }
    }

    private fun showIndicator(icon: Int, text: String) {
        indicatorIcon.setImageResource(icon)
        indicatorText.text = text
        indicator.animate().cancel()
        indicator.alpha = 1f
        handler.removeCallbacks(hideIndicator)
        handler.postDelayed(hideIndicator, 900)
    }

    private fun flash(view: TextView) {
        view.animate().cancel()
        view.alpha = 1f
        view.animate().alpha(0f).setStartDelay(350).setDuration(250).start()
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
            p.play()
        } else if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
        scheduleHide()
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(if (duration > 0) min(target, duration) else target)
        updateProgress()
        scheduleHide()
    }

    private fun updatePlayIcon() {
        val p = player
        val ended = p?.playbackState == Player.STATE_ENDED
        btnPlay.setImageResource(
            when {
                ended -> R.drawable.ic_media_replay
                p?.isPlaying == true || p?.playWhenReady == true && p.playbackState == Player.STATE_BUFFERING -> R.drawable.ic_media_pause
                else -> R.drawable.ic_media_play
            }
        )
    }

    private fun updateProgress() {
        val p = player ?: return
        if (userSeeking) return
        val duration = p.duration
        val position = p.currentPosition
        if (duration > 0) {
            seekBar.progress = (position * 1000 / duration).toInt()
            seekBar.secondaryProgress = (p.bufferedPosition * 1000 / duration).toInt()
            timeTotal.text = formatTime(duration)
        }
        timeCurrent.text = formatTime(position)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = max(ms, 0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        if (!isAudio && player?.isPlaying == true && !locked) handler.postDelayed(hideControls, 3500)
    }

    private fun setControlsVisible(visible: Boolean) {
        if (locked) return
        controlsVisible = visible
        val alpha = if (visible) 1f else 0f
        listOf(controlsTop, controlsCenter, controlsBottom).forEach {
            it.animate().alpha(alpha).setDuration(180).start()
            it.visibility = View.VISIBLE
            it.isClickable = visible
        }
        controlsCenter.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            scheduleHide()
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setLocked(value: Boolean) {
        locked = value
        if (value) {
            listOf(controlsTop, controlsCenter, controlsBottom).forEach { it.visibility = View.INVISIBLE }
            btnUnlock.visibility = View.VISIBLE
            handler.removeCallbacks(hideControls)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            handler.postDelayed({ btnUnlock.animate().alpha(0f).setDuration(250).start() }, 2500)
        } else {
            btnUnlock.visibility = View.GONE
            btnUnlock.alpha = 1f
            listOf(controlsTop, controlsCenter, controlsBottom).forEach { it.visibility = View.VISIBLE; it.alpha = 1f }
            controlsVisible = true
            scheduleHide()
        }
    }

    private fun showUnlock() {
        btnUnlock.animate().cancel()
        btnUnlock.alpha = 1f
        btnUnlock.visibility = View.VISIBLE
        handler.postDelayed({ if (locked) btnUnlock.animate().alpha(0f).setDuration(250).start() }, 2500)
    }

    private fun toggleOrientation() {
        landscapeForced = !landscapeForced
        requestedOrientation = if (landscapeForced) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        btnFullscreen.setImageResource(if (landscapeForced) R.drawable.ic_media_fullscreen_exit else R.drawable.ic_media_fullscreen)
        scheduleHide()
    }

    private fun showSpeedMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        speeds.forEachIndexed { index, value ->
            val item = menu.menu.add(0, index, index, getString(R.string.media_speed_format, formatSpeed(value)))
            item.isCheckable = true
            item.isChecked = abs(value - speed) < 0.01f
        }
        menu.setOnMenuItemClickListener { item ->
            speed = speeds[item.itemId]
            player?.setPlaybackSpeed(speed)
            btnSpeed.text = getString(R.string.media_speed_format, formatSpeed(speed))
            true
        }
        menu.show()
    }

    private fun formatSpeed(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0')

    private fun showMoreMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, R.string.media_action_open_with)
        menu.setOnMenuItemClickListener {
            openWith()
            true
        }
        menu.show()
    }

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: (if (isAudio) "audio/*" else "video/*")

    private fun shareableUri(): Uri? {
        val local = file
        if (local != null) return FileProvider.getUriForFile(this, "$packageName.fileprovider", local)
        return sourceUri
    }

    private fun share() {
        try {
            val uri = shareableUri() ?: return
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(displayName)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, null))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_document_to_share, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWith() {
        try {
            val uri = shareableUri() ?: return
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeOf(displayName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, null))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.toast_no_app_available, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
        if (deleteOnExit) {
            try {
                val target = file
                if (target != null && target.parentFile?.name == "doc_viewer") target.delete()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "video_path"
        const val EXTRA_NAME = "video_name"
        const val EXTRA_DELETE = "video_delete_on_exit"
    }
}
