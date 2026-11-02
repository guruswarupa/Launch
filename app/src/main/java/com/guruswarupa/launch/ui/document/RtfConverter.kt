package com.guruswarupa.launch.ui.document

import java.io.File
import java.nio.charset.Charset

internal object RtfConverter {

    private class State(
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var strike: Boolean = false,
        var colorIndex: Int = 0,
        var sizeHalfPt: Int = 0,
        var skip: Boolean = false,
        var unicodeSkip: Int = 1
    ) {
        fun copy() = State(bold, italic, underline, strike, colorIndex, sizeHalfPt, skip, unicodeSkip)
    }

    private val SKIPPED = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "headerl", "headerr", "headerf",
        "footerl", "footerr", "footerf", "listtable", "listoverridetable", "generator", "themedata", "colorschememapping",
        "latentstyles", "datastore", "xmlnstbl", "rsidtbl", "operator", "author", "company", "object", "fldinst", "bkmkstart",
        "bkmkend", "revtbl", "pgptbl", "mmathPr", "wgrffmtfilter", "private", "nonshppict", "shpinst"
    )

    fun convert(file: File, title: String): String {
        val bytes = file.readBytes().let { if (it.size > 16 * 1024 * 1024) it.copyOf(16 * 1024 * 1024) else it }
        val text = String(bytes, Charsets.ISO_8859_1)
        if (!text.trimStart().startsWith("{\\rtf")) throw IllegalStateException("Not an RTF file")
        val codePage = Regex("\\\\ansicpg(\\d+)").find(text)?.groupValues?.get(1)?.let {
            try { Charset.forName("windows-$it") } catch (_: Exception) { Charsets.ISO_8859_1 }
        } ?: Charsets.ISO_8859_1
        val colors = parseColors(text)

        val out = StringBuilder()
        val paragraph = StringBuilder()
        var align = ""
        val stack = ArrayList<State>()
        var state = State()
        var i = 0
        var pendingSkip = 0
        val pendingBytes = java.io.ByteArrayOutputStream()

        fun flushBytes() {
            if (pendingBytes.size() > 0) {
                paragraph.append(styled(Html.esc(String(pendingBytes.toByteArray(), codePage)), state, colors))
                pendingBytes.reset()
            }
        }

        fun endParagraph() {
            flushBytes()
            val style = if (align.isNotEmpty()) " style=\"text-align:$align\"" else ""
            out.append("<p$style>").append(if (paragraph.isBlank()) "&nbsp;" else paragraph).append("</p>")
            paragraph.setLength(0)
        }

        fun emit(textValue: String) {
            if (state.skip) return
            flushBytes()
            paragraph.append(styled(Html.esc(textValue), state, colors))
        }

        while (i < text.length) {
            val c = text[i]
            when (c) {
                '{' -> {
                    flushBytes()
                    stack.add(state.copy())
                    i++
                    var j = i
                    if (j < text.length && text[j] == '\\') {
                        if (j + 1 < text.length && text[j + 1] == '*') {
                            state.skip = true
                        } else {
                            val word = Regex("^\\\\([a-zA-Z]+)").find(text.substring(j, minOf(text.length, j + 24)))?.groupValues?.get(1)
                            if (word != null && word in SKIPPED) state.skip = true
                        }
                    }
                }
                '}' -> {
                    flushBytes()
                    if (stack.isNotEmpty()) state = stack.removeAt(stack.size - 1)
                    i++
                }
                '\\' -> {
                    i++
                    if (i >= text.length) break
                    val n = text[i]
                    when {
                        n == '\\' || n == '{' || n == '}' -> {
                            if (pendingSkip > 0) pendingSkip-- else emit(n.toString())
                            i++
                        }
                        n == '\'' -> {
                            val hex = text.substring(i + 1, minOf(text.length, i + 3))
                            hex.toIntOrNull(16)?.let { if (!state.skip) pendingBytes.write(it) }
                            i += 3
                        }
                        n == '~' -> { emit(" "); i++ }
                        n == '-' -> { i++ }
                        n == '_' -> { emit("‑"); i++ }
                        n == '*' -> { i++ }
                        n == '\n' || n == '\r' -> { i++ }
                        n.isLetter() -> {
                            var j = i
                            while (j < text.length && text[j].isLetter()) j++
                            val word = text.substring(i, j)
                            var k = j
                            var negative = false
                            if (k < text.length && text[k] == '-') { negative = true; k++ }
                            val numStart = k
                            while (k < text.length && text[k].isDigit()) k++
                            val param = if (k > numStart) text.substring(numStart, k).toIntOrNull()?.let { if (negative) -it else it } else null
                            if (k < text.length && text[k] == ' ') k++
                            i = k
                            when (word) {
                                "par", "sect", "page" -> if (!state.skip) endParagraph()
                                "line" -> emit("\n")
                                "tab" -> emit(" ")
                                "pard" -> { align = ""; state.bold = false; state.italic = false; state.underline = false; state.strike = false }
                                "plain" -> { state.bold = false; state.italic = false; state.underline = false; state.strike = false; state.sizeHalfPt = 0; state.colorIndex = 0 }
                                "b" -> state.bold = param != 0
                                "i" -> state.italic = param != 0
                                "ul", "uld", "uldb" -> state.underline = param != 0
                                "ulnone" -> state.underline = false
                                "strike" -> state.strike = param != 0
                                "cf" -> state.colorIndex = param ?: 0
                                "fs" -> state.sizeHalfPt = param ?: 0
                                "qc" -> align = "center"
                                "qr" -> align = "right"
                                "ql" -> align = ""
                                "qj" -> align = "justify"
                                "uc" -> state.unicodeSkip = param ?: 1
                                "u" -> {
                                    if (param != null && !state.skip) {
                                        flushBytes()
                                        val code = if (param < 0) param + 65536 else param
                                        paragraph.append(styled(Html.esc(String(Character.toChars(code))), state, colors))
                                    }
                                    pendingSkip = state.unicodeSkip
                                }
                                "emdash" -> emit("—")
                                "endash" -> emit("–")
                                "bullet" -> emit("•")
                                "lquote" -> emit("‘")
                                "rquote" -> emit("’")
                                "ldblquote" -> emit("“")
                                "rdblquote" -> emit("”")
                                "trowd", "row" -> if (word == "row") endParagraph()
                                "cell" -> emit(" | ")
                            }
                        }
                        else -> i++
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (pendingSkip > 0) {
                        pendingSkip--
                    } else if (!state.skip) {
                        flushBytes()
                        val start = i
                        while (i < text.length && text[i] != '\\' && text[i] != '{' && text[i] != '}' && text[i] != '\r' && text[i] != '\n') i++
                        paragraph.append(styled(Html.esc(text.substring(start, i)), state, colors))
                        continue
                    }
                    i++
                }
            }
        }
        if (paragraph.isNotBlank()) endParagraph()
        return Html.page(title, "<div class=\"doc rtf\">$out</div>", ".rtf p{margin:0 0 10px;white-space:pre-wrap}")
    }

    private fun parseColors(text: String): List<String?> {
        val start = text.indexOf("{\\colortbl")
        if (start < 0) return emptyList()
        val end = text.indexOf('}', start)
        if (end < 0) return emptyList()
        val table = text.substring(start, end)
        val colors = ArrayList<String?>()
        colors.add(null)
        val entries = table.substringAfter(';', "").split(';')
        for (entry in entries) {
            val r = Regex("\\\\red(\\d+)").find(entry)?.groupValues?.get(1)?.toIntOrNull()
            val g = Regex("\\\\green(\\d+)").find(entry)?.groupValues?.get(1)?.toIntOrNull()
            val b = Regex("\\\\blue(\\d+)").find(entry)?.groupValues?.get(1)?.toIntOrNull()
            if (r != null && g != null && b != null) colors.add(String.format("#%02x%02x%02x", r, g, b))
            else if (entry.isNotBlank() || colors.size > 1) colors.add(null)
        }
        return colors
    }

    private fun styled(html: String, state: State, colors: List<String?>): String {
        if (html.isEmpty()) return html
        var out = html
        val css = StringBuilder()
        colors.getOrNull(state.colorIndex)?.let { css.append("color:$it;") }
        if (state.sizeHalfPt > 0) css.append("font-size:${state.sizeHalfPt / 2.0}pt;")
        if (css.isNotEmpty()) out = "<span style=\"$css\">$out</span>"
        if (state.bold) out = "<b>$out</b>"
        if (state.italic) out = "<i>$out</i>"
        if (state.underline) out = "<u>$out</u>"
        if (state.strike) out = "<s>$out</s>"
        return out
    }
}
