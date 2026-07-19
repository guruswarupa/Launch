package com.guruswarupa.launch.managers

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt




class NoiseDecibelManager(@Suppress("unused") private val context: android.content.Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var onDecibelChangedListener: ((Double) -> Unit)? = null
    private var readBuffer: ShortArray? = null
    private var readBufferSize = 0

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2
        private const val UPDATE_INTERVAL_MS = 100L
    }

    private val recordingRunnable = object : Runnable {
        override fun run() {
            if (isRecording && audioRecord != null) {
                val decibel = calculateDecibel()
                onDecibelChangedListener?.invoke(decibel)
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }




    fun setOnDecibelChangedListener(listener: (Double) -> Unit) {
        onDecibelChangedListener = listener
    }




    fun startRecording(): Boolean {
        if (isRecording) return true

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
                return false
            }

            val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER

            audioRecord = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            } catch (_: SecurityException) {
                Log.e("NoiseDecibelManager", "Microphone permission required")
                return false
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            readBuffer = ShortArray(bufferSize)
            readBufferSize = bufferSize
            handler.post(recordingRunnable)
            return true
        } catch (_: Exception) {
            stopRecording()
            return false
        }
    }




    fun stopRecording() {
        isRecording = false
        handler.removeCallbacks(recordingRunnable)

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (_: Exception) {

        }
        readBuffer = null
        readBufferSize = 0
    }




    private fun calculateDecibel(): Double {
        val currentAudioRecord = this.audioRecord ?: return 0.0
        val buffer = readBuffer ?: return 0.0
        val bufferSize = readBufferSize
        if (bufferSize <= 0) return 0.0

        try {
            val readSize = currentAudioRecord.read(buffer, 0, bufferSize)

            if (readSize <= 0) {
                return 0.0
            }


            var sum = 0.0
            for (i in 0 until readSize) {
                sum += (buffer[i] * buffer[i]).toDouble()
            }
            val rms = sqrt(sum / readSize)




            val referenceValue = 32767.0
            val db = if (rms > 0) {
                20 * log10(rms / referenceValue)
            } else {
                -96.0
            }


            return max(0.0, min(120.0, db + 96.0))
        } catch (_: Exception) {
            return 0.0
        }
    }




    fun hasMicrophone(): Boolean {
        return try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            bufferSize != AudioRecord.ERROR_BAD_VALUE && bufferSize != AudioRecord.ERROR
        } catch (_: Exception) {
            false
        }
    }




    fun isRecording(): Boolean = isRecording




    fun cleanup() {
        stopRecording()
        onDecibelChangedListener = null
    }
}
