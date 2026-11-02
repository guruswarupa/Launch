package com.guruswarupa.launch.ui.document

import android.util.Base64
import java.io.File
import java.text.DecimalFormat
import java.util.Locale
import java.util.zip.ZipFile

internal object BinaryConverters {

    private const val MAX_IMAGE_BYTES = 24L * 1024 * 1024
    private const val HEX_BYTES = 256 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 5000

    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "avif", "heic", "heif", "jfif")
    val ARCHIVE_EXTENSIONS = setOf("zip", "jar", "apk", "aar", "war", "apkm", "xapk", "epub", "cbz", "kmz", "xpi", "odb", "crx")
    val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus", "wma", "mid", "midi", "amr")
    val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v", "mpg", "mpeg")
    val MEDIA_EXTENSIONS = setOf(
        "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus", "wma", "mid", "midi",
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "wmv", "m4v"
    )

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return DecimalFormat("#.##").format(value) + " " + units[unit]
    }

    private fun mimeFor(extension: String): String = when (extension) {
        "png" -> "image/png"
        "jpg", "jpeg", "jfif" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "svg" -> "image/svg+xml"
        "ico" -> "image/x-icon"
        "avif" -> "image/avif"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        else -> "application/octet-stream"
    }

    fun image(file: File, title: String): String {
        val extension = file.extension.lowercase()
        if (file.length() > MAX_IMAGE_BYTES) return info(file, title, "Image", "This image is too large to preview. Use Open with to view it in another app.")
        val data = file.readBytes()
        val uri = "data:${mimeFor(extension)};base64," + Base64.encodeToString(data, Base64.NO_WRAP)
        val body = "<div class=\"imgview\"><img src=\"$uri\" alt=\"${Html.esc(title)}\"><div class=\"cap\">${Html.esc(title)} · ${formatSize(file.length())}</div></div>"
        return Html.page(title, body, "body{display:flex;min-height:100vh;align-items:center;justify-content:center}.imgview{padding:12px;text-align:center}.imgview img{max-width:100%;height:auto;background:repeating-conic-gradient(#8883 0 25%,#0000 0 50%) 50%/20px 20px;border-radius:4px}.cap{margin-top:10px;font-size:13px;color:var(--muted)}")
    }

    fun archive(file: File, title: String): String? {
        return try {
            ZipFile(file).use { zip ->
                val rows = StringBuilder()
                var count = 0
                var total = 0L
                var compressed = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    count++
                    if (!e.isDirectory) {
                        total += maxOf(e.size, 0)
                        compressed += maxOf(e.compressedSize, 0)
                    }
                    if (count <= MAX_ARCHIVE_ENTRIES) {
                        val name = Html.esc(e.name)
                        rows.append("<tr><td class=\"nm\">${if (e.isDirectory) "&#128193; " else ""}$name</td><td class=\"sz\">${if (e.isDirectory) "" else formatSize(maxOf(e.size, 0))}</td></tr>")
                    }
                }
                val summary = "<div class=\"sum\">$count items · ${formatSize(total)} uncompressed · ${formatSize(file.length())} on disk</div>"
                val note = if (count > MAX_ARCHIVE_ENTRIES) "<div class=\"sum\">Showing the first $MAX_ARCHIVE_ENTRIES entries.</div>" else ""
                Html.page(
                    title,
                    "<div class=\"doc\"><h2>${Html.esc(title)}</h2>$summary<table class=\"arch\">$rows</table>$note</div>",
                    ".arch{width:100%;font-size:14px}.arch td{padding:6px 4px;border-bottom:1px solid var(--border)}.nm{word-break:break-all}.sz{text-align:right;white-space:nowrap;color:var(--muted);padding-left:12px}.sum{color:var(--muted);margin:6px 0 14px}"
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hex(file: File, title: String): String {
        val length = file.length()
        val read = minOf(length, HEX_BYTES.toLong()).toInt()
        val bytes = ByteArray(read)
        file.inputStream().use { input ->
            var offset = 0
            while (offset < read) {
                val n = input.read(bytes, offset, read - offset)
                if (n < 0) break
                offset += n
            }
        }
        val sb = StringBuilder()
        sb.append("<div class=\"sum\">${Html.esc(title)} · ${formatSize(length)}")
        if (length > HEX_BYTES) sb.append(" · showing the first ${formatSize(HEX_BYTES.toLong())}")
        sb.append("</div><div class=\"hex\">")
        var i = 0
        while (i < read) {
            val end = minOf(i + 16, read)
            sb.append("<div class=\"l\"><span class=\"off\">").append(String.format(Locale.US, "%08X", i)).append("</span><span class=\"hx\">")
            for (k in i until i + 16) {
                if (k < end) sb.append(String.format(Locale.US, "%02X ", bytes[k].toInt() and 0xFF)) else sb.append("   ")
                if (k == i + 7) sb.append(" ")
            }
            sb.append("</span><span class=\"as\">")
            for (k in i until end) {
                val b = bytes[k].toInt() and 0xFF
                sb.append(if (b in 32..126) Html.esc(b.toChar().toString()) else "·")
            }
            sb.append("</span></div>")
            i += 16
        }
        sb.append("</div>")
        return Html.page(
            title,
            sb.toString(),
            "body{font-family:ui-monospace,'Roboto Mono',Menlo,monospace;font-size:12px;line-height:1.5}.sum{padding:12px 14px;color:var(--muted);font-family:sans-serif;font-size:13px}.hex{padding:0 8px 100px;overflow-x:auto;white-space:pre;min-width:max-content}.l{display:flex;gap:14px}.off{color:var(--muted)}.as{color:var(--accent)}",
            "hexview"
        )
    }

    fun info(file: File, title: String, kind: String, message: String): String {
        val body = "<div class=\"doc\"><h2>${Html.esc(title)}</h2><p>$kind · ${formatSize(file.length())}</p><p class=\"msg\">${Html.esc(message)}</p></div>"
        return Html.page(title, body, ".msg{color:var(--muted)}")
    }

    fun extractLegacyText(file: File, title: String): String {
        val bytes = file.readBytes().let { if (it.size > 24 * 1024 * 1024) it.copyOf(24 * 1024 * 1024) else it }
        val seen = LinkedHashSet<String>()
        val out = StringBuilder()
        fun flush(buffer: StringBuilder) {
            val text = buffer.toString().trim()
            if (text.length >= 6 && text.any { it.isLetter() } && seen.add(text)) out.append(text).append('\n')
            buffer.setLength(0)
        }
        val ascii = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 32..126 || c == 9) ascii.append(c.toChar()) else if (c == 13 || c == 10) { ascii.append('\n') } else flush(ascii)
        }
        flush(ascii)
        val wide = StringBuilder()
        var i = 0
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() and 0xFF
            if (hi == 0 && (lo in 32..126 || lo == 13 || lo == 10 || lo == 9)) {
                wide.append(if (lo == 13) '\n' else lo.toChar())
                i += 2
            } else {
                flush(wide)
                i += if (hi == 0) 2 else 1
            }
        }
        flush(wide)
        val lines = out.toString().lines().filter { it.isNotBlank() }.take(20000)
        val body = StringBuilder()
        body.append("<div class=\"doc\"><div class=\"warn\">Basic text extraction. This older format (.${Html.esc(file.extension.lowercase())}) has no formatting, tables or images here. Save it as .docx, .xlsx or .pptx for the full view.</div>")
        lines.forEach { body.append("<p>${Html.esc(it)}</p>") }
        body.append("</div>")
        return Html.page(title, body.toString(), ".warn{background:var(--head-bg);border:1px solid var(--border);border-radius:8px;padding:10px 12px;margin-bottom:16px;color:var(--muted);font-size:14px}.doc p{margin:0 0 6px}")
    }
}
