package com.guruswarupa.launch.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object SampledBitmap {

    private fun sampleFor(width: Int, height: Int, maxSidePx: Int): Int {
        var sample = 1
        if (width <= 0 || height <= 0) return sample
        while (width / (sample * 2) >= maxSidePx || height / (sample * 2) >= maxSidePx) sample *= 2
        return sample
    }

    fun fromBytes(bytes: ByteArray, maxSidePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxSidePx) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    fun fromFile(path: String, maxSidePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, maxSidePx) }
        return BitmapFactory.decodeFile(path, options)
    }
}
