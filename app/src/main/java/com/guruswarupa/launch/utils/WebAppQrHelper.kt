package com.guruswarupa.launch.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.guruswarupa.launch.models.WebAppEntry
import org.json.JSONObject

object WebAppQrHelper {

    fun encode(entry: WebAppEntry): String {
        val json = JSONObject()
        json.put("name", entry.name)
        json.put("url", entry.url)
        return json.toString()
    }

    /** Returns null if [text] isn't a valid web-app QR payload. */
    fun decode(text: String): WebAppEntry? {
        return try {
            val json = JSONObject(text)
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
            val url = json.optString("url").takeIf { it.isNotBlank() } ?: return null
            WebAppEntry(id = "", name = name, url = url)
        } catch (_: Exception) {
            null
        }
    }

    fun toBitmap(text: String, sizePx: Int = 640): Bitmap? {
        return try {
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
