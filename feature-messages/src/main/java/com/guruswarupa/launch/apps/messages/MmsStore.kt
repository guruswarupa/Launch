package com.guruswarupa.launch.apps.messages

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.webkit.MimeTypeMap

/** One attachment of a picture message as stored in the phone's MMS database. */
data class Attachment(val partId: Long, val type: String, val name: String?) {
    val isImage get() = type.startsWith("image/")
    val isVideo get() = type.startsWith("video/")
    val isAudio get() = type.startsWith("audio/")
    val uri: Uri get() = Uri.parse("content://mms/part/$partId")
}

/** Reads and writes picture messages in the system MMS provider. Writing works while Launch Messages is the default SMS app. */
object MmsStore {

    fun extensionFor(type: String): String = when {
        type == "image/jpeg" -> "jpg"
        type == "audio/amr" -> "amr"
        type == "audio/mpeg" -> "mp3"
        type == "audio/mp4" || type == "audio/aac" -> "m4a"
        type == "video/3gpp" -> "3gp"
        else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: "bin"
    }

    fun insert(ctx: Context, threadId: Long, address: String, inbox: Boolean, dateSeconds: Long, parts: List<MmsPart>, read: Boolean): Uri? {
        val cr = ctx.contentResolver
        return try {
            val v = ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, threadId)
                put(Telephony.Mms.DATE, dateSeconds); put(Telephony.Mms.DATE_SENT, if (inbox) dateSeconds else 0L)
                put(Telephony.Mms.MESSAGE_BOX, if (inbox) Telephony.Mms.MESSAGE_BOX_INBOX else Telephony.Mms.MESSAGE_BOX_OUTBOX)
                put(Telephony.Mms.READ, if (read) 1 else 0); put(Telephony.Mms.SEEN, if (read) 1 else 0)
                put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
                put(Telephony.Mms.MESSAGE_TYPE, if (inbox) 132 else 128)
                put(Telephony.Mms.MMS_VERSION, 18)
                put(Telephony.Mms.MESSAGE_CLASS, "personal")
                put(Telephony.Mms.PRIORITY, 129)
                put(Telephony.Mms.READ_REPORT, 129); put(Telephony.Mms.DELIVERY_REPORT, 129)
                put(Telephony.Mms.MESSAGE_SIZE, parts.sumOf { it.data.size })
                put(Telephony.Mms.TRANSACTION_ID, "T" + System.currentTimeMillis())
            }
            val msg = cr.insert(Telephony.Mms.CONTENT_URI, v) ?: return null
            val id = ContentUris.parseId(msg)
            // addresses
            fun addr(a: String, type: Int) {
                cr.insert(Uri.parse("content://mms/$id/addr"), ContentValues().apply { put("address", a); put("type", type); put("charset", 106); put("msg_id", id) })
            }
            if (inbox) { addr(address, 137); addr("insert-address-token", 151) } else { addr("insert-address-token", 137); addr(address, 151) }
            // parts
            parts.forEachIndexed { i, p ->
                val pv = ContentValues().apply {
                    put("mid", id); put("ct", p.contentType); put("seq", if (p.contentType == "application/smil") -1 else 0)
                    p.name?.let { put("name", it); put("cl", it) }
                    p.contentId?.let { put("cid", it) }
                    if (p.contentType.startsWith("text/") || p.contentType == "application/smil") { put("chset", 106); put("text", String(p.data, Charsets.UTF_8)) }
                }
                val row = cr.insert(Uri.parse("content://mms/$id/part"), pv)
                if (row != null && !(p.contentType.startsWith("text/") || p.contentType == "application/smil")) {
                    cr.openOutputStream(Uri.parse("content://mms/part/${ContentUris.parseId(row)}"))?.use { it.write(p.data) }
                }
            }
            msg
        } catch (_: Exception) { null }
    }

    fun setBox(ctx: Context, mms: Uri?, box: Int) {
        mms ?: return
        try { ctx.contentResolver.update(mms, ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, box) }, null, null) } catch (_: Exception) {}
    }

    /** Text and attachments of one message, skipping the layout file. */
    fun parts(ctx: Context, mmsId: Long): Pair<String, List<Attachment>> {
        val text = StringBuilder()
        val atts = ArrayList<Attachment>()
        try {
            ctx.contentResolver.query(Uri.parse("content://mms/$mmsId/part"), arrayOf("_id", "ct", "name", "text", "cl"), null, null, "seq ASC, _id ASC")?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(1).orEmpty()
                    when {
                        type == "application/smil" -> {}
                        type.startsWith("text/") -> c.getString(3)?.let { if (text.isNotEmpty()) text.append('\n'); text.append(it) }
                        else -> atts.add(Attachment(c.getLong(0), type, c.getString(2) ?: c.getString(4)))
                    }
                }
            }
        } catch (_: Exception) {}
        return text.toString() to atts
    }

    /** The number or name on the other end of the message. */
    fun otherAddress(ctx: Context, mmsId: Long, inbox: Boolean): String {
        val want = if (inbox) 137 else 151
        try {
            ctx.contentResolver.query(Uri.parse("content://mms/$mmsId/addr"), arrayOf("address", "type"), null, null, null)?.use { c ->
                var fallback = ""
                while (c.moveToNext()) {
                    val a = c.getString(0).orEmpty()
                    if (a.isBlank() || a == "insert-address-token") continue
                    if (c.getInt(1) == want) return a
                    if (fallback.isEmpty()) fallback = a
                }
                return fallback
            }
        } catch (_: Exception) {}
        return ""
    }

    fun summary(ctx: Context, text: String, atts: List<Attachment>, labels: (Attachment) -> String): String =
        if (text.isNotBlank()) text else atts.firstOrNull()?.let(labels).orEmpty()

    fun delete(ctx: Context, mmsId: Long): Boolean = try {
        ctx.contentResolver.delete(Uri.parse("content://mms/$mmsId"), null, null) > 0
    } catch (_: Exception) { false }
}
