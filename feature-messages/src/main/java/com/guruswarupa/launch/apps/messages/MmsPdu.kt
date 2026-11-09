package com.guruswarupa.launch.apps.messages

import java.io.ByteArrayOutputStream

/** One piece of a picture message: text, an image, a video or a sound. */
class MmsPart(val contentType: String, val name: String?, val contentId: String?, val data: ByteArray)

/**
 * Writes and reads the binary form of MMS (OMA MMS 1.2 encapsulation).
 * The phone's own MMS service does the sending and downloading; this only builds and understands the message bytes.
 */
object MmsPdu {

    // ---- writing -------------------------------------------------------------------------------------------

    private fun ByteArrayOutputStream.text(s: String) { write(s.toByteArray(Charsets.UTF_8)); write(0) }

    private fun uintvar(value: Int): ByteArray {
        if (value == 0) return byteArrayOf(0)
        val out = ArrayList<Byte>()
        var v = value
        out.add((v and 0x7F).toByte()); v = v ushr 7
        while (v > 0) { out.add(0, ((v and 0x7F) or 0x80).toByte()); v = v ushr 7 }
        return out.toByteArray()
    }

    private fun smil(parts: List<MmsPart>): String {
        val body = StringBuilder()
        val media = parts.filter { !it.contentType.startsWith("text/") }
        val texts = parts.filter { it.contentType.startsWith("text/") }
        val slides = if (media.isEmpty()) listOf<MmsPart?>(null) else media
        slides.forEachIndexed { i, m ->
            body.append("<par dur=\"5000ms\">")
            if (m != null) {
                val tag = when { m.contentType.startsWith("image/") -> "img"; m.contentType.startsWith("video/") -> "video"; else -> "audio" }
                val region = if (tag == "audio") "" else " region=\"Image\""
                body.append("<$tag src=\"${m.name}\"$region/>")
            }
            if (i == 0) texts.forEach { body.append("<text src=\"${it.name}\" region=\"Text\"/>") }
            body.append("</par>")
        }
        return "<smil><head><layout><root-layout width=\"320px\" height=\"480px\"/>" +
            "<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" width=\"100%\" height=\"80%\"/>" +
            "<region id=\"Text\" top=\"80%\" left=\"0\" width=\"100%\" height=\"20%\"/></layout></head><body>$body</body></smil>"
    }

    /** A send request for one recipient. [number] should be digits with an optional leading +. */
    fun sendReq(number: String, parts: List<MmsPart>, transactionId: String): ByteArray {
        val all = ArrayList<MmsPart>()
        all.add(MmsPart("application/smil", "smil.xml", "<smil>", smil(parts).toByteArray(Charsets.UTF_8)))
        all.addAll(parts)

        val out = ByteArrayOutputStream()
        out.write(0x8C); out.write(0x80)                       // message type: m-send-req
        out.write(0x98); out.text(transactionId)               // transaction id
        out.write(0x8D); out.write(0x92)                       // version 1.2
        out.write(0x89); out.write(0x01); out.write(0x81)      // from: insert address token
        out.write(0x97); out.text("$number/TYPE=PLMN")         // to
        out.write(0x8A); out.write(0x80)                       // class: personal
        out.write(0x8F); out.write(0x81)                       // priority: normal
        out.write(0x86); out.write(0x81)                       // delivery report: no
        out.write(0x90); out.write(0x81)                       // read report: no

        // Content-Type: application/vnd.wap.multipart.related; type="application/smil"; start="<smil>"
        val ct = ByteArrayOutputStream().apply {
            write(0xB3)
            write(0x89); text("application/smil")
            write(0x8A); text("<smil>")
        }.toByteArray()
        out.write(0x84); out.write(ct.size); out.write(ct)

        out.write(uintvar(all.size))
        for (p in all) {
            val headers = ByteArrayOutputStream()
            if (p.contentType.startsWith("text/plain")) {
                headers.write(0x03); headers.write(0x83); headers.write(0x81); headers.write(0xEA)  // text/plain; charset=utf-8
            } else headers.text(p.contentType)
            p.name?.let { headers.write(0x8E); headers.text(it) }                                     // Content-Location
            p.contentId?.let { headers.write(0xC0); headers.write(0x22); headers.text(it) }           // Content-ID
            val h = headers.toByteArray()
            out.write(uintvar(h.size)); out.write(uintvar(p.data.size)); out.write(h); out.write(p.data)
        }
        return out.toByteArray()
    }

    // ---- reading -------------------------------------------------------------------------------------------

    class Notification(val transactionId: String, val location: String, val from: String?, val size: Long, val subject: String?)
    class Retrieved(val from: String?, val to: List<String>, val subject: String?, val dateSeconds: Long, val parts: List<MmsPart>)

    private class Reader(val b: ByteArray, var pos: Int = 0) {
        val more get() = pos < b.size
        fun u8(): Int = if (pos < b.size) b[pos++].toInt() and 0xFF else throw IndexOutOfBoundsException()
        fun peek(): Int = if (pos < b.size) b[pos].toInt() and 0xFF else -1
        fun uintvar(): Int { var v = 0; var c: Int; do { c = u8(); v = (v shl 7) or (c and 0x7F) } while (c and 0x80 != 0); return v }
        fun text(): String {
            var end = pos
            while (end < b.size && b[end].toInt() != 0) end++
            var start = pos
            if (start < end && (b[start].toInt() == 0x7F || b[start].toInt() == 0x22)) start++   // quote marker
            val s = String(b, start, end - start, Charsets.UTF_8)
            pos = minOf(end + 1, b.size)
            return s
        }
        fun longInt(): Long { val n = u8(); var v = 0L; repeat(n) { v = (v shl 8) or u8().toLong() }; return v }
        fun skip(n: Int) { pos = minOf(pos + n, b.size) }
    }

    /** Encoded-string-value: optional charset prefix, then text. */
    private fun Reader.encodedString(): String {
        val p = peek()
        if (p in 0..31) { val len = if (p == 31) { u8(); uintvar() } else u8(); val end = pos + len; if (len > 0) { if (peek() >= 0x80 || peek() < 32) u8(); val s = String(b, pos, maxOf(0, end - pos - 1), Charsets.UTF_8); pos = end; return s.trimEnd('\u0000') } else return "" }
        return text()
    }

    private fun cleanAddress(raw: String?): String? = raw?.substringBefore("/TYPE=")?.takeIf { it.isNotBlank() && it != "insert-address-token" }

    /** Reads the "you have a picture message" notice that arrives by WAP push. */
    fun parseNotification(pdu: ByteArray): Notification? = try {
        val r = Reader(pdu)
        // WAP push can arrive with the content type header already stripped; accept both.
        var tx = ""; var loc = ""; var from: String? = null; var size = 0L; var subject: String? = null; var type = -1
        while (r.more) {
            val field = r.u8()
            when (field) {
                0x8C -> type = r.u8()
                0x98 -> tx = r.text()
                0x8D -> r.u8()
                0x83 -> loc = r.text()
                0x89 -> { val len = r.uintvar(); val end = r.pos + len; if (len > 1) { r.u8(); from = cleanAddress(String(pdu, r.pos, maxOf(0, end - r.pos - 1), Charsets.UTF_8)) }; r.pos = end }
                0x8E -> size = r.longInt()
                0x96 -> subject = r.encodedString()
                0x8A, 0x88, 0x8F, 0x86, 0x90 -> r.u8()
                0x84 -> r.skip(r.u8())
                else -> if (field and 0x80 != 0) { val v = r.peek(); if (v in 0..30) r.skip(r.u8()) else if (v == 31) { r.u8(); r.skip(r.uintvar()) } else if (v >= 0x80) r.u8() else r.text() } else r.text()
            }
        }
        if (type != 0x82 && loc.isEmpty()) null else if (loc.isEmpty()) null else Notification(tx, loc, from, size, subject)
    } catch (_: Exception) { null }

    private val wellKnown = mapOf(
        0x00 to "*/*", 0x01 to "text/*", 0x02 to "text/html", 0x03 to "text/plain", 0x06 to "text/x-vCalendar", 0x07 to "text/x-vCard",
        0x1D to "image/gif", 0x1E to "image/jpeg", 0x1F to "image/tiff", 0x20 to "image/png", 0x21 to "image/vnd.wap.wbmp",
        0x23 to "application/vnd.wap.multipart.mixed", 0x33 to "application/vnd.wap.multipart.related", 0x26 to "application/vnd.wap.multipart.alternative"
    )

    private fun Reader.contentType(): String {
        val p = peek()
        return when {
            p >= 0x80 -> { u8(); wellKnown[p and 0x7F] ?: "application/octet-stream" }
            p in 0..30 -> { val len = u8(); val end = pos + len; val t = if (peek() >= 0x80) wellKnown[u8() and 0x7F] ?: "application/octet-stream" else text(); pos = minOf(end, b.size); t }
            p == 31 -> { u8(); val len = uintvar(); val end = pos + len; val t = if (peek() >= 0x80) wellKnown[u8() and 0x7F] ?: "application/octet-stream" else text(); pos = minOf(end, b.size); t }
            else -> text()
        }
    }

    /** Reads a downloaded picture message into its parts. */
    fun parseRetrieveConf(pdu: ByteArray): Retrieved? = try {
        val r = Reader(pdu)
        var from: String? = null; val to = ArrayList<String>(); var subject: String? = null; var date = 0L; var type = -1
        var parts: List<MmsPart> = emptyList()
        while (r.more) {
            val field = r.u8()
            when (field) {
                0x8C -> type = r.u8()
                0x98 -> r.text()
                0x8D -> r.u8()
                0x85 -> date = r.longInt()
                0x89 -> { val len = r.uintvar(); val end = r.pos + len; if (len > 1) { r.u8(); from = cleanAddress(String(pdu, r.pos, maxOf(0, end - r.pos - 1), Charsets.UTF_8)) }; r.pos = end }
                0x97, 0x82, 0x81 -> to.add(cleanAddress(r.encodedString()).orEmpty())
                0x96 -> subject = r.encodedString()
                0x84 -> { r.contentType(); parts = parseParts(r) }
                0x8A, 0x8F, 0x86, 0x90, 0x94, 0x95, 0x99, 0x9A, 0x92, 0x93 -> r.u8()
                else -> if (field and 0x80 != 0) { val v = r.peek(); if (v in 0..30) r.skip(r.u8()) else if (v == 31) { r.u8(); r.skip(r.uintvar()) } else if (v >= 0x80) r.u8() else r.text() } else r.text()
            }
            if (field == 0x84) break
        }
        if (type != 0x84 && parts.isEmpty()) null else Retrieved(from, to.filter { it.isNotEmpty() }, subject, date, parts)
    } catch (_: Exception) { null }

    /** Body of a multipart message: count, then each part's headers and data. */
    fun parseParts(r0: Any): List<MmsPart> {
        val r = r0 as Reader
        val count = r.uintvar()
        val out = ArrayList<MmsPart>()
        repeat(count) {
            val headersLen = r.uintvar(); val dataLen = r.uintvar()
            val headerEnd = r.pos + headersLen
            val type = r.contentType()
            var name: String? = null; var cid: String? = null
            while (r.pos < headerEnd) {
                when (val h = r.u8()) {
                    0x8E -> name = r.text()
                    0xC0 -> cid = r.text()
                    0xAE -> r.text()
                    0xC5 -> { val v = r.peek(); if (v in 0..30) r.skip(r.u8()) else r.u8() }
                    else -> if (h and 0x80 != 0) { val v = r.peek(); if (v in 0..30) r.skip(r.u8()) else if (v >= 0x80) r.u8() else r.text() } else r.text()
                }
            }
            r.pos = headerEnd
            val data = r.b.copyOfRange(r.pos, minOf(r.pos + dataLen, r.b.size))
            r.skip(dataLen)
            out.add(MmsPart(type, name, cid, data))
        }
        return out
    }

    /** Test hook: parses just a multipart body. */
    fun partsOf(body: ByteArray): List<MmsPart> = parseParts(Reader(body))

    /** The body of a send request, for round-trip tests. */
    fun bodyOfSendReq(pdu: ByteArray): List<MmsPart> {
        val r = Reader(pdu)
        while (r.more) {
            when (val f = r.u8()) {
                0x84 -> { r.skip(r.u8()); return parseParts(r) }
                0x8C, 0x8D, 0x8A, 0x8F, 0x86, 0x90 -> r.u8()
                0x98, 0x97 -> r.text()
                0x89 -> r.skip(r.u8())
                else -> if (f and 0x80 != 0) r.u8() else r.text()
            }
        }
        return emptyList()
    }
}
