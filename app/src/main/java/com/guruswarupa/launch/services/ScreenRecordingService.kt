package com.guruswarupa.launch.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.guruswarupa.launch.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ScreenRecordingService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 240

    private var isRecording = false

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_recording_channel"

        const val ACTION_START = "START_RECORDING"
        const val ACTION_STOP = "STOP_RECORDING"
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_DATA = "DATA"

        fun startService(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ScreenRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        var isRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                if (resultCode != 0 && data != null) {
                    startRecording(resultCode, data)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        if (isRecording) return

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDensity = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }


        if (screenWidth % 2 != 0) screenWidth--
        if (screenHeight % 2 != 0) screenHeight--

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        if (mediaProjection == null) {
            stopSelf()
            return
        }

        initRecorder()

        try {
            mediaRecorder?.start()

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecording",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface, null, null
            )

            isRecording = true
            isRunning = true
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            stopSelf()
        }
    }

    private var recordingUri: android.net.Uri? = null

    private fun initRecorder() {
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "ScreenRecord_$timeStamp.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/LaunchRecordings")
                put(android.provider.MediaStore.Video.Media.IS_PENDING, 1)
            }

            recordingUri = contentResolver.insert(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            val fd = recordingUri?.let { uri ->
                contentResolver.openFileDescriptor(uri, "w")
            } ?: throw IllegalStateException("Failed to create MediaStore entry")

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(fd.fileDescriptor)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoSize(screenWidth, screenHeight)
                setVideoEncodingBitRate(5 * 1024 * 1024)
                setVideoFrameRate(30)
                prepare()
            }
        } else {

            val videoDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "LaunchRecordings")
            if (!videoDir.exists()) videoDir.mkdirs()
            val videoFile = File(videoDir, fileName)

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoFile.absolutePath)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoSize(screenWidth, screenHeight)
                setVideoEncodingBitRate(5 * 1024 * 1024)
                setVideoFrameRate(30)
                prepare()
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            recordingUri?.let { uri ->
                try {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    contentResolver.update(uri, contentValues, null, null)
                } catch (_: Exception) {}
            }
            recordingUri = null
        }

        isRecording = false
        isRunning = false
        Toast.makeText(this, "Recording saved to Movies/LaunchRecordings", Toast.LENGTH_LONG).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Screen")
            .setContentText("Launch is recording your screen")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}
