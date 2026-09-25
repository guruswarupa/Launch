package com.guruswarupa.launch.managers

data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {

    private val timeTagRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offsetRegex = Regex("\\[offset:\\s*(-?\\d+)]", RegexOption.IGNORE_CASE)

    fun parse(lrc: String): List<LyricLine> {
        val offsetMs = offsetRegex.find(lrc)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()

        lrc.lineSequence().forEach { rawLine ->
            var cursor = 0
            val timestamps = mutableListOf<Long>()
            while (true) {
                val match = timeTagRegex.find(rawLine, cursor) ?: break
                if (match.range.first != cursor) break
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    2 -> fraction.toLong() * 10
                    3 -> fraction.toLong()
                    else -> fraction.padEnd(3, '0').take(3).toLong()
                }
                timestamps.add((minutes * 60 + seconds) * 1000 + fractionMs - offsetMs)
                cursor = match.range.last + 1
            }
            if (timestamps.isEmpty()) return@forEach
            val text = rawLine.substring(cursor).trim()
            timestamps.forEach { timeMs -> lines.add(LyricLine(timeMs.coerceAtLeast(0L), text)) }
        }

        return lines.sortedBy { it.timeMs }
    }

    fun indexAt(lines: List<LyricLine>, posMs: Long): Int {
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= posMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
