package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.guruswarupa.launch.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var grid: View
    private lateinit var flashButton: ImageView
    private lateinit var timerButton: ImageView
    private lateinit var timerLabel: TextView
    private lateinit var shutter: FrameLayout
    private lateinit var shutterInner: View
    private lateinit var countdown: TextView
    private lateinit var recordingLabel: TextView
    private lateinit var photoMode: TextView
    private lateinit var videoMode: TextView
    private lateinit var thumb: ImageView

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var timerSeconds = 0
    private var videoSelected = false
    private var gridOn = false
    private var timer: CountDownTimer? = null
    private var lastUri: Uri? = null
    private var recordStart = 0L

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasCamera()) startCamera() else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(buildUi())
        if (hasCamera()) startCamera() else permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun icon(res: Int, size: Int = 44, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(res)
        setColorFilter(Color.WHITE)
        val p = dp(10)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        setOnClickListener { onClick() }
    }

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        root.addView(previewView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        grid = object : View(this) {
            private val paint = android.graphics.Paint().apply { color = 0x88FFFFFF.toInt(); strokeWidth = 2f }
            override fun onDraw(c: android.graphics.Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                for (i in 1..2) {
                    c.drawLine(w * i / 3, 0f, w * i / 3, h, paint)
                    c.drawLine(0f, h * i / 3, w, h * i / 3, paint)
                }
            }
        }.apply { visibility = View.GONE }
        root.addView(grid, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        top.addView(icon(R.drawable.ic_f_back) { finish() })
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        flashButton = icon(R.drawable.ic_cam_flash_off) { cycleFlash() }
        top.addView(flashButton)
        val timerBox = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setOnClickListener { cycleTimer() } }
        timerButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_cam_timer); setColorFilter(Color.WHITE)
            val p = dp(10); setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        timerLabel = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f; visibility = View.GONE }
        timerBox.addView(timerButton); timerBox.addView(timerLabel)
        top.addView(timerBox)
        top.addView(icon(R.drawable.ic_cam_grid) { gridOn = !gridOn; grid.visibility = if (gridOn) View.VISIBLE else View.GONE })
        root.addView(top, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        recordingLabel = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { setColor(0xCCE53935.toInt()); cornerRadius = dp(14).toFloat() }
            setPadding(dp(12), dp(4), dp(12), dp(4)); visibility = View.GONE
        }
        root.addView(recordingLabel, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(70) })

        countdown = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 96f; gravity = Gravity.CENTER; visibility = View.GONE
        }
        root.addView(countdown, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(0x66000000)
        }
        val modes = LinearLayout(this).apply { gravity = Gravity.CENTER }
        photoMode = modeLabel("PHOTO") { selectMode(false) }
        videoMode = modeLabel("VIDEO") { selectMode(true) }
        modes.addView(photoMode); modes.addView(videoMode)
        bottom.addView(modes, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, 0) }
        thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(0x44FFFFFF); cornerRadius = dp(12).toFloat() }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            setOnClickListener { openLast() }
        }
        row.addView(thumb, LinearLayout.LayoutParams(dp(52), dp(52)))
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        shutter = FrameLayout(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(4), Color.WHITE) }
            setOnClickListener { onShutter() }
        }
        shutterInner = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) } }
        shutter.addView(shutterInner, FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER))
        row.addView(shutter, LinearLayout.LayoutParams(dp(78), dp(78)))
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(icon(R.drawable.ic_cam_flip, 52) { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK; bind() }.apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x44FFFFFF) }
        })
        bottom.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            top.setPadding(dp(12), b.top + dp(8), dp(12), dp(8))
            bottom.setPadding(dp(16), dp(12), dp(16), b.bottom + dp(20))
            insets
        }
        selectMode(false)
        return root
    }

    private fun modeLabel(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(16), dp(6), dp(16), dp(6))
        setOnClickListener { onClick() }
    }

    private fun selectMode(video: Boolean) {
        if (recording != null) return
        videoSelected = video
        photoMode.setTextColor(if (video) 0x99FFFFFF.toInt() else 0xFFFFD54F.toInt())
        videoMode.setTextColor(if (video) 0xFFFFD54F.toInt() else 0x99FFFFFF.toInt())
        (shutterInner.background as GradientDrawable).setColor(if (video) 0xFFE53935.toInt() else Color.WHITE)
        if (provider != null) bind()
    }

    private fun startCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({ provider = f.get(); bind() }, ContextCompat.getMainExecutor(this))
        loadLastThumb()
    }

    private fun bind() {
        val p = provider ?: return
        p.unbindAll()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        try {
            camera = if (videoSelected) {
                val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD, androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
                videoCapture = VideoCapture.withOutput(recorder)
                imageCapture = null
                p.bindToLifecycle(this, selector, preview, videoCapture)
            } else {
                imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setFlashMode(flashMode).build()
                videoCapture = null
                p.bindToLifecycle(this, selector, preview, imageCapture)
            }
            applyFlash()
        } catch (e: Exception) {
            Toast.makeText(this, "Camera unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cycleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        applyFlash()
    }

    private fun applyFlash() {
        flashButton.setImageResource(when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> R.drawable.ic_cam_flash_on
            ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_cam_flash_auto
            else -> R.drawable.ic_cam_flash_off
        })
        imageCapture?.flashMode = flashMode
        if (videoSelected) camera?.cameraControl?.enableTorch(flashMode == ImageCapture.FLASH_MODE_ON)
    }

    private fun cycleTimer() {
        timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
        timerLabel.visibility = if (timerSeconds == 0) View.GONE else View.VISIBLE
        timerLabel.text = "${timerSeconds}s"
        timerButton.setColorFilter(if (timerSeconds == 0) Color.WHITE else 0xFFFFD54F.toInt())
    }

    private fun onShutter() {
        if (recording != null) { recording?.stop(); return }
        timer?.cancel()
        if (timerSeconds > 0) {
            countdown.visibility = View.VISIBLE
            timer = object : CountDownTimer(timerSeconds * 1000L, 1000) {
                override fun onTick(ms: Long) { countdown.text = ((ms + 999) / 1000).toString() }
                override fun onFinish() { countdown.visibility = View.GONE; capture() }
            }.start()
        } else capture()
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun capture() {
        if (videoSelected) recordVideo() else takePhoto()
    }

    private fun takePhoto() {
        val ic = imageCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${stamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
        }
        val out = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        flashShutter()
        ic.takePicture(out, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                lastUri = r.savedUri
                loadLastThumb()
            }
            override fun onError(e: ImageCaptureException) {
                Toast.makeText(this@CameraActivity, "Could not save photo", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun flashShutter() {
        previewView.animate().alpha(0.2f).setDuration(60).withEndAction { previewView.animate().alpha(1f).setDuration(120).start() }.start()
    }

    private fun recordVideo() {
        val vc = videoCapture ?: return
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_${stamp()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Camera")
        }
        val opts = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
        var pending = vc.output.prepareRecording(this, opts)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { ev ->
            when (ev) {
                is VideoRecordEvent.Start -> {
                    recordStart = SystemClock.elapsedRealtime()
                    recordingLabel.visibility = View.VISIBLE
                    shutterInner.animate().scaleX(0.55f).scaleY(0.55f).setDuration(150).start()
                    tickRecording()
                }
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    recordingLabel.visibility = View.GONE
                    shutterInner.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    if (!ev.hasError()) { lastUri = ev.outputResults.outputUri; loadLastThumb() }
                    else Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun tickRecording() {
        if (recording == null) return
        val s = (SystemClock.elapsedRealtime() - recordStart) / 1000
        recordingLabel.text = "● %02d:%02d".format(s / 60, s % 60)
        recordingLabel.postDelayed({ tickRecording() }, 500)
    }

    private fun loadLastThumb() {
        Thread {
            val uri = lastUri ?: try { latestMedia() } catch (e: Exception) { null } ?: return@Thread
            lastUri = uri
            val bmp = try {
                if (android.os.Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(uri, android.util.Size(256, 256), null) else null
            } catch (e: Exception) { null }
            runOnUiThread { if (bmp != null) thumb.setImageBitmap(bmp) }
        }.start()
    }

    private fun latestMedia(): Uri? {
        val q = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE),
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (1,3) AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("DCIM/Camera%"),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        ) ?: return null
        q.use {
            if (!it.moveToFirst()) return null
            val id = it.getLong(0)
            val base = if (it.getInt(1) == 3) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            return Uri.withAppendedPath(base, id.toString())
        }
    }

    private fun openLast() {
        val uri = lastUri ?: return
        val type = contentResolver.getType(uri) ?: "image/*"
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setClassName(packageName, if (type.startsWith("video")) "com.guruswarupa.launch.ui.activities.VideoPlayerExternalActivity" else "com.guruswarupa.launch.ui.activities.PhotoViewerExternalActivity")
        }
        try { startActivity(i) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        timer?.cancel()
        recording?.stop()
        super.onDestroy()
    }
}
