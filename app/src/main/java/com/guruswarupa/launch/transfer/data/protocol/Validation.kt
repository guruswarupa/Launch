package com.guruswarupa.launch.transfer.data.protocol

object FilenameSanitizer {
    private const val FALLBACK = "file"

    /** Returns a safe display name. The result is never used as a path by itself. */
    fun sanitize(raw: String): String {
        val cleaned = buildString {
            for (c in raw) {
                when {
                    c == '/' || c == '\\' || c == ':' -> append('_')
                    c.isISOControl() -> append('_')
                    else -> append(c)
                }
            }
        }.trim().trim('.').trim()
        val name = truncateUtf8(cleaned, Limits.MAX_NAME_BYTES)
        return if (name.isEmpty()) FALLBACK else name
    }

    private fun truncateUtf8(s: String, maxBytes: Int): String {
        if (s.toByteArray(Charsets.UTF_8).size <= maxBytes) return s
        val sb = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val chars = Character.charCount(cp)
            val n = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + n > maxBytes) break
            sb.appendCodePoint(cp)
            bytes += n
            i += chars
        }
        return sb.toString().trim()
    }
}

object DirPath {
    /** Splits a sender-supplied relative directory into safe segments; `..`, `.` and empty parts are dropped. */
    fun sanitize(raw: String): List<String> {
        if (raw.isEmpty()) return emptyList()
        if (raw.toByteArray(Charsets.UTF_8).size > Limits.MAX_DIR_BYTES) throw ProtocolException("limits_exceeded", "directory too long")
        val parts = raw.split('/').map { it.trim() }.filter { it.isNotEmpty() && it != "." && it != ".." }
        if (parts.size > Limits.MAX_DIR_DEPTH) throw ProtocolException("limits_exceeded", "directory too deep")
        return parts.map(FilenameSanitizer::sanitize)
    }
}

object ProposalValidator {
    private fun fail(msg: String): Nothing = throw ProtocolException("limits_exceeded", msg)

    fun validateHeader(p: Message.TransferPropose, maxTotal: Long = Limits.MAX_TOTAL_BYTES) {
        if (p.transfer.isEmpty() || p.transfer.length > 64) throw ProtocolException("bad_message", "bad transfer id")
        if (p.count < 1 || p.count > Limits.MAX_FILES) fail("file count")
        if (p.total < 0 || p.total > maxTotal) fail("total size")
    }

    /** Validates one file; ids must be the sequence "1", "2", … so duplicates are impossible. Returns the new running total. */
    fun validateFile(f: FileMeta, index: Int, runningTotal: Long, maxTotal: Long = Limits.MAX_TOTAL_BYTES): Long {
        if (f.id != (index + 1).toString()) throw ProtocolException("bad_message", "bad file id")
        if (f.size < 0) throw ProtocolException("bad_message", "negative size")
        if (f.name.toByteArray(Charsets.UTF_8).size > Limits.MAX_NAME_BYTES) fail("name length")
        if ((f.orig?.toByteArray(Charsets.UTF_8)?.size ?: 0) > Limits.MAX_DIR_BYTES) fail("directory too long")
        if (f.size > maxTotal - runningTotal) fail("total size")
        return runningTotal + f.size
    }
}

/** Cleans a peer-supplied label (device name) for display. */
fun cleanLabel(raw: String, maxChars: Int = 40): String {
    val cleaned = raw.filter { !it.isISOControl() }.trim()
    val cut = if (cleaned.length > maxChars) cleaned.take(maxChars).trim() else cleaned
    return cut.ifEmpty { "Unknown device" }
}
