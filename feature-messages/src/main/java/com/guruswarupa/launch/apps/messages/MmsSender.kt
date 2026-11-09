package com.guruswarupa.launch.apps.messages

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.guruswarupa.launch.messages.R
import java.io.ByteArrayOutputStream
import java.io.File

/** A file the user picked to send. */
class PickedMedia(val uri: Uri, val mime: String, val name: String)

class MmsTooLarge(val limitKb: Int) : Exception()

object MmsSender {

    private fun manager(ctx: Context): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()

    /** The biggest picture message the carrier accepts. Defaults to 300 KB when the phone doesn't say. */
    fun limitBytes(ctx: Context): Int = try {
        manager(ctx).carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 300 * 1024).takeIf { it > 0 } ?: (300 * 1024)
    } catch (_: Exception) { 300 * 1024 }

    private fun shrinkImage(ctx: Context, uri: Uri, budget: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var maxSide = 1280
        while (maxSide >= 240) {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
            val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
            for (q in intArrayOf(85, 70, 55, 40)) {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
                if (out.size() <= budget) return out.toByteArray()
            }
            maxSide = (maxSide * 0.7f).toInt()
        }
        return null
    }

    private fun readBytes(ctx: Context, uri: Uri, limit: Int): ByteArray {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > limit) throw MmsTooLarge(limit / 1024)
            }
            return out.toByteArray()
        }
        throw MmsTooLarge(limit / 1024)
    }

    /** Sends [text] and [media] as one picture message. Runs on the calling thread, so call it off the main thread. */
    fun send(ctx: Context, address: String, text: String, media: List<PickedMedia>): Boolean {
        val number = address.filter { it.isDigit() || it == '+' }.ifEmpty { return false }
        val limit = limitBytes(ctx)
        var budget = limit - 4096 - text.toByteArray().size
        val parts = ArrayList<MmsPart>()
        if (text.isNotBlank()) parts.add(MmsPart("text/plain", "text.txt", "<text>", text.toByteArray(Charsets.UTF_8)))
        media.forEachIndexed { i, m ->
            val perItem = budget / (media.size - i)
            if (m.mime.startsWith("image/") && m.mime != "image/gif") {
                val data = shrinkImage(ctx, m.uri, perItem) ?: throw MmsTooLarge(limit / 1024)
                parts.add(MmsPart("image/jpeg", "image${i + 1}.jpg", "<img$i>", data)); budget -= data.size
            } else {
                val data = readBytes(ctx, m.uri, perItem)
                val type = m.mime
                parts.add(MmsPart(type, "media${i + 1}.${MmsStore.extensionFor(type)}", "<m$i>", data)); budget -= data.size
            }
        }
        val pdu = MmsPdu.sendReq(number, parts, "T" + System.currentTimeMillis())
        if (pdu.size > limit) throw MmsTooLarge(limit / 1024)

        val dir = File(ctx.cacheDir, "mms").apply { mkdirs() }
        val file = File(dir, "send_${System.currentTimeMillis()}.pdu").apply { writeBytes(pdu) }
        val fileUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        for (pkg in listOf("com.android.mms.service", "com.android.phone")) {
            try { ctx.grantUriPermission(pkg, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
        val thread = SmsRepo.threadFor(ctx, number)
        val row = MmsStore.insert(ctx, thread, number, inbox = false, dateSeconds = System.currentTimeMillis() / 1000, parts = parts, read = true)
        val result = PendingIntent.getBroadcast(
            ctx, (System.currentTimeMillis() and 0xFFFFFF).toInt(),
            Intent(ctx, MmsSentReceiver::class.java).setData(row ?: Uri.parse("mms://none")).putExtra("pdu", file.absolutePath),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return try {
            manager(ctx).sendMultimediaMessage(ctx, fileUri, null, null, result)
            true
        } catch (_: Exception) {
            MmsStore.setBox(ctx, row, Telephony.Mms.MESSAGE_BOX_FAILED); file.delete(); false
        }
    }
}

class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ok = resultCode == android.app.Activity.RESULT_OK
        val row = intent.data?.takeIf { it.scheme == "content" }
        MmsStore.setBox(context, row, if (ok) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED)
        intent.getStringExtra("pdu")?.let { try { File(it).delete() } catch (_: Exception) {} }
    }
}

/** Picture message notices arrive here. We ask the phone's MMS service to fetch the message. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        val n = MmsPdu.parseNotification(data) ?: return
        val dir = File(context.cacheDir, "mms").apply { mkdirs() }
        val file = File(dir, "recv_${System.currentTimeMillis()}.pdu").apply { writeBytes(ByteArray(0)) }
        val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        for (pkg in listOf("com.android.mms.service", "com.android.phone")) {
            try { context.grantUriPermission(pkg, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
        }
        val done = PendingIntent.getBroadcast(
            context, (System.currentTimeMillis() and 0xFFFFFF).toInt(),
            Intent(context, MmsDownloadedReceiver::class.java).putExtra("pdu", file.absolutePath).putExtra("from", n.from.orEmpty()),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.downloadMultimediaMessage(context, n.location, fileUri, null, done)
        } catch (_: Exception) { file.delete() }
    }
}

class MmsDownloadedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra("pdu") ?: return
        val ok = resultCode == android.app.Activity.RESULT_OK
        val pending = goAsync()
        Thread {
            try {
                val file = File(path)
                if (ok && file.length() > 0) {
                    val r = MmsPdu.parseRetrieveConf(file.readBytes())
                    if (r != null) {
                        val from = r.from ?: intent.getStringExtra("from").orEmpty()
                        val thread = SmsRepo.threadFor(context, from)
                        val date = r.dateSeconds.takeIf { it > 0 } ?: (System.currentTimeMillis() / 1000)
                        val row = MmsStore.insert(context, thread, from, inbox = true, dateSeconds = date, parts = r.parts, read = false)
                        if (row != null) {
                            val text = r.parts.firstOrNull { it.contentType.startsWith("text/plain") }?.let { String(it.data, Charsets.UTF_8) }
                            val label = text?.takeIf { it.isNotBlank() } ?: context.getString(when {
                                r.parts.any { it.contentType.startsWith("image/") } -> R.string.msg_photo
                                r.parts.any { it.contentType.startsWith("video/") } -> R.string.msg_video
                                r.parts.any { it.contentType.startsWith("audio/") } -> R.string.msg_audio
                                else -> R.string.msg_picture_message
                            })
                            Notifier.incoming(context, from, label)
                        }
                    }
                }
                file.delete()
            } catch (_: Exception) {
            } finally { pending.finish() }
        }.start()
    }
}
