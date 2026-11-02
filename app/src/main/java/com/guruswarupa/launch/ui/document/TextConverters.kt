package com.guruswarupa.launch.ui.document

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object TextConverters {

    private const val MAX_BYTES = 6 * 1024 * 1024
    private const val HIGHLIGHT_LIMIT = 400 * 1024

    class Loaded(val text: String, val truncated: Boolean)

    fun load(file: File): Loaded {
        val length = file.length()
        val read = minOf(length, MAX_BYTES.toLong()).toInt()
        val bytes = ByteArray(read)
        file.inputStream().use { input ->
            var offset = 0
            while (offset < read) {
                val n = input.read(bytes, offset, read - offset)
                if (n < 0) break
                offset += n
            }
        }
        return Loaded(decode(bytes), length > MAX_BYTES)
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            try { String(bytes, Charset.forName("windows-1252")) } catch (_: Exception) { String(bytes, Charsets.ISO_8859_1) }
        }
    }

    fun looksBinary(file: File): Boolean {
        val sample = ByteArray(4096)
        val n = file.inputStream().use { it.read(sample) }
        if (n <= 0) return false
        if (n >= 2 && ((sample[0] == 0xFF.toByte() && sample[1] == 0xFE.toByte()) || (sample[0] == 0xFE.toByte() && sample[1] == 0xFF.toByte()))) return false
        var suspicious = 0
        for (k in 0 until n) {
            val b = sample[k].toInt() and 0xFF
            if (b == 0) return true
            if (b < 9 || (b in 14..31)) suspicious++
        }
        return suspicious * 100 / n > 8
    }

    private fun banner(truncated: Boolean): String =
        if (truncated) "<div class=\"note\">Large file: showing the first 6 MB.</div>" else ""

    fun plain(file: File, title: String): String {
        val loaded = load(file)
        return codeView(loaded.text, "text", title, loaded.truncated)
    }

    fun code(file: File, extension: String, title: String): String {
        val loaded = load(file)
        var text = loaded.text
        if (extension == "json" || extension == "geojson") text = prettyJson(text) ?: text
        if (extension == "xml" || extension == "svg" || extension == "plist" || extension == "xhtml") text = prettyXml(text) ?: text
        return codeView(text, extension, title, loaded.truncated)
    }

    private fun prettyJson(text: String): String? = try {
        val trimmed = text.trim()
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun prettyXml(text: String): String? {
        val lines = text.lines()
        val average = if (lines.isEmpty()) 0 else text.length / lines.size
        if (lines.size > 6 && average < 160) return null
        if (text.length > 2 * 1024 * 1024) return null
        return try {
            val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, if (text.trimStart().startsWith("<?xml")) "no" else "yes")
            val writer = java.io.StringWriter()
            transformer.transform(javax.xml.transform.stream.StreamSource(java.io.StringReader(text)), javax.xml.transform.stream.StreamResult(writer))
            writer.toString().trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    fun htmlSource(file: File, title: String): String {
        val loaded = load(file)
        return codeView(loaded.text, "html", title, loaded.truncated)
    }

    fun htmlRendered(file: File, title: String): String {
        val loaded = load(file)
        val withTheme = loaded.text
        return withTheme
    }

    private fun codeView(text: String, extension: String, title: String, truncated: Boolean): String {
        val lines = if (text.length <= HIGHLIGHT_LIMIT && extension != "text") highlight(text, extension) else plainLines(text)
        val sb = StringBuilder()
        sb.append(banner(truncated))
        sb.append("<div class=\"code\">")
        lines.forEachIndexed { index, line ->
            sb.append("<div class=\"l\"><span class=\"n\">${index + 1}</span><span class=\"c\">")
            sb.append(if (line.isEmpty()) "&#8203;" else line)
            sb.append("</span></div>")
        }
        sb.append("</div>")
        return Html.page(title, sb.toString(), CODE_CSS, "codeview")
    }

    private fun plainLines(text: String): List<String> =
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { Html.esc(it) }

    private val CLASS_ONLY = setOf("kw", "str", "cm", "num")

    private fun keywordsFor(extension: String): Set<String> = when (extension) {
        "py" -> setOf("def", "class", "import", "from", "as", "if", "elif", "else", "for", "while", "return", "yield", "try", "except", "finally", "with", "lambda", "pass", "break", "continue", "raise", "in", "is", "not", "and", "or", "None", "True", "False", "global", "nonlocal", "async", "await", "assert", "del")
        "kt", "java", "c", "cpp", "h", "swift", "go", "rs", "dart", "ts", "tsx", "js", "jsx", "vue", "cs", "php" ->
            setOf("fun", "val", "var", "let", "const", "class", "interface", "object", "enum", "struct", "impl", "trait", "func", "def", "function", "return", "if", "else", "for", "while", "do", "when", "switch", "case", "break", "continue", "try", "catch", "finally", "throw", "throws", "new", "this", "super", "import", "package", "from", "export", "extends", "implements", "public", "private", "protected", "internal", "static", "final", "override", "abstract", "open", "null", "nil", "true", "false", "void", "int", "long", "float", "double", "boolean", "string", "char", "byte", "in", "is", "as", "typeof", "instanceof", "await", "async", "suspend", "lateinit", "by", "init", "companion", "data", "sealed", "default", "namespace", "using", "use", "mut", "pub", "mod", "match", "type", "defer", "go", "chan", "select", "range", "unsafe")
        "sql" -> setOf("select", "from", "where", "insert", "into", "values", "update", "set", "delete", "create", "table", "alter", "drop", "join", "left", "right", "inner", "outer", "on", "group", "by", "order", "having", "limit", "and", "or", "not", "null", "as", "distinct", "union", "index", "primary", "key", "foreign", "references", "default", "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "AND", "OR", "NOT", "NULL", "AS", "DISTINCT", "UNION")
        "sh", "bat", "ps1" -> setOf("if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac", "function", "return", "echo", "export", "local", "in", "exit", "set", "unset", "source", "cd", "ls", "rm", "cp", "mv", "mkdir", "foreach", "param")
        "rb" -> setOf("def", "end", "class", "module", "if", "elsif", "else", "unless", "while", "until", "for", "do", "return", "yield", "begin", "rescue", "ensure", "require", "include", "nil", "true", "false", "self", "puts", "attr_accessor")
        "json", "geojson" -> setOf("true", "false", "null")
        "yaml", "yml", "toml", "ini", "cfg", "conf" -> setOf("true", "false", "null", "yes", "no", "on", "off")
        else -> emptySet()
    }

    private fun lineCommentFor(extension: String): String? = when (extension) {
        "py", "sh", "rb", "yaml", "yml", "toml", "ini", "cfg", "conf", "ps1" -> "#"
        "sql" -> "--"
        "bat" -> "REM "
        "json", "geojson", "xml", "html", "htm", "md", "text", "csv", "log" -> null
        else -> "//"
    }

    private fun highlight(text: String, extension: String): List<String> {
        val keywords = keywordsFor(extension)
        val lineComment = lineCommentFor(extension)
        val markup = extension in setOf("xml", "html", "htm", "svg", "vue")
        val parts = ArrayList<String>()
        parts.add("<!--[\\s\\S]*?-->".takeIf { markup } ?: "")
        parts.add("/\\*[\\s\\S]*?\\*/".takeIf { lineComment == "//" || extension == "css" || extension == "sql" } ?: "")
        parts.add(if (lineComment != null) Regex.escape(lineComment) + "[^\\n]*" else "")
        parts.add("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''")
        parts.add("\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)*'|`(?:[^`\\\\]|\\\\.)*`")
        parts.add("\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b|0x[0-9a-fA-F]+")
        parts.add("[A-Za-z_][A-Za-z0-9_]*")
        val regex = Regex(parts.filter { it.isNotEmpty() }.joinToString("|") { "($it)" })
        val groupKinds = ArrayList<String>()
        if (markup) groupKinds.add("cm")
        if (lineComment == "//" || extension == "css" || extension == "sql") groupKinds.add("cm")
        if (lineComment != null) groupKinds.add("cm")
        groupKinds.add("str")
        groupKinds.add("str")
        groupKinds.add("num")
        groupKinds.add("id")

        val segments = ArrayList<Pair<String, String?>>()
        var last = 0
        for (m in regex.findAll(text)) {
            if (m.range.first > last) segments.add(text.substring(last, m.range.first) to null)
            var kind: String? = null
            for (g in 1 until m.groups.size) {
                if (m.groups[g] != null) {
                    kind = groupKinds.getOrNull(g - 1)
                    break
                }
            }
            val value = m.value
            val cls = when (kind) {
                "id" -> if (value in keywords) "kw" else null
                else -> kind
            }
            segments.add(value to cls)
            last = m.range.last + 1
        }
        if (last < text.length) segments.add(text.substring(last) to null)

        val lines = ArrayList<StringBuilder>()
        lines.add(StringBuilder())
        for ((value, cls) in segments) {
            val pieces = value.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            pieces.forEachIndexed { index, piece ->
                if (index > 0) lines.add(StringBuilder())
                if (piece.isNotEmpty()) {
                    val escaped = Html.esc(piece)
                    if (cls != null && cls in CLASS_ONLY) lines.last().append("<span class=\"$cls\">$escaped</span>") else lines.last().append(escaped)
                }
            }
        }
        return lines.map { it.toString() }
    }

    private const val CODE_CSS = """
body.codeview{font-family:ui-monospace,'Roboto Mono',Menlo,Consolas,monospace;font-size:13px;line-height:1.55}
.code{padding:8px 0 100px;min-width:100%}
.l{display:flex;min-height:1.55em}
.n{flex:none;width:3.6em;text-align:right;padding-right:12px;color:var(--muted);user-select:none;border-right:1px solid var(--border);margin-right:12px}
.c{white-space:pre;flex:1}
html[data-wrap="on"] .c{white-space:pre-wrap;word-break:break-word}
html[data-wrap="off"] body.codeview{overflow-x:auto}
.kw{color:#a626a4}.str{color:#50a14f}.cm{color:#8a8f98;font-style:italic}.num{color:#b76b01}
html[data-theme="dark"] .kw{color:#c678dd}html[data-theme="dark"] .str{color:#98c379}html[data-theme="dark"] .cm{color:#7f848e}html[data-theme="dark"] .num{color:#d19a66}
.note{padding:10px 14px;color:var(--muted);font-family:sans-serif}
"""

    fun csv(file: File, title: String, forcedDelimiter: Char? = null): String {
        val loaded = load(file)
        val delimiter = forcedDelimiter ?: detectDelimiter(loaded.text)
        val rows = parseCsv(loaded.text, delimiter, 5001)
        val truncatedRows = rows.size > 5000
        val data = if (truncatedRows) rows.subList(0, 5000) else rows
        val columns = data.maxOfOrNull { it.size } ?: 0
        val sb = StringBuilder()
        sb.append("<div class=\"sheets full\"><table class=\"grid\"><thead><tr><th class=\"corner\"></th>")
        for (c in 0 until columns) sb.append("<th>${columnLabel(c)}</th>")
        sb.append("</tr></thead><tbody>")
        data.forEachIndexed { r, row ->
            sb.append("<tr><th class=\"rh\">${r + 1}</th>")
            for (c in 0 until columns) {
                val cell = row.getOrNull(c).orEmpty()
                val numeric = cell.isNotEmpty() && cell.replace(",", "").toDoubleOrNull() != null
                sb.append(if (numeric) "<td class=\"num\">" else "<td>").append(Html.esc(cell)).append("</td>")
            }
            sb.append("</tr>")
        }
        sb.append("</tbody></table>")
        if (truncatedRows || loaded.truncated) sb.append("<div class=\"note\">Large file: showing the first rows only.</div>")
        sb.append("</div>")
        return Html.page(title, sb.toString(), CSV_CSS, "xlsx")
    }

    private fun columnLabel(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            n--
            sb.append('A' + n % 26)
            n /= 26
        }
        return sb.reverse().toString()
    }

    private fun detectDelimiter(text: String): Char {
        val sample = text.take(4000).lines().take(10)
        val candidates = charArrayOf(',', '\t', ';', '|')
        var best = ','
        var bestScore = -1
        for (candidate in candidates) {
            val counts = sample.filter { it.isNotBlank() }.map { line -> line.count { it == candidate } }
            if (counts.isEmpty() || counts.all { it == 0 }) continue
            val consistent = counts.count { it == counts.first() }
            val score = consistent * 100 + counts.first()
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun parseCsv(text: String, delimiter: Char, maxRows: Int): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length && rows.size < maxRows) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                } else cell.append(c)
            } else {
                when {
                    c == '"' && cell.isEmpty() -> inQuotes = true
                    c == delimiter -> { row.add(cell.toString()); cell.setLength(0) }
                    c == '\n' || c == '\r' -> {
                        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        row.add(cell.toString()); cell.setLength(0)
                        rows.add(row)
                        row = ArrayList()
                    }
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }

    private const val CSV_CSS = """
body.xlsx{overflow:auto}
table.grid{border-collapse:separate;border-spacing:0;font-size:13px;width:max-content;min-width:100%}
table.grid th,table.grid td{border-right:1px solid var(--border);border-bottom:1px solid var(--border);padding:4px 8px;white-space:pre;vertical-align:top;min-width:72px;max-width:420px;overflow:hidden;text-overflow:ellipsis}
table.grid thead th{position:sticky;top:0;background:var(--head-bg);z-index:3;font-weight:600;color:var(--muted);text-align:center}
table.grid th.rh,table.grid thead th.corner{min-width:0;width:32px;max-width:32px;padding:4px 3px;font-size:11px}
table.grid th.rh{position:sticky;left:0;background:var(--head-bg);z-index:2;color:var(--muted);font-weight:500;text-align:center}
table.grid thead th.corner{left:0;z-index:4}
td.num{text-align:right}
tbody tr:first-child td{font-weight:600}
.note{padding:10px 14px;color:var(--muted);font-size:13px}
"""

    fun markdown(file: File, title: String): String {
        val loaded = load(file)
        val body = MarkdownRenderer.render(loaded.text)
        return Html.page(title, banner(loaded.truncated) + "<div class=\"doc md\">$body</div>", MarkdownRenderer.CSS)
    }
}

internal object MarkdownRenderer {

    fun render(source: String): String {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = StringBuilder()
        var i = 0
        val paragraph = ArrayList<String>()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                out.append("<p>").append(inline(paragraph.joinToString("\n"))).append("</p>")
                paragraph.clear()
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    flushParagraph()
                    val fence = trimmed.take(3)
                    val language = trimmed.drop(3).trim()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                        code.append(lines[i]).append('\n')
                        i++
                    }
                    out.append("<pre><code${if (language.isNotEmpty()) " class=\"lang-${Html.esc(language)}\"" else ""}>${Html.esc(code.toString().trimEnd('\n'))}</code></pre>")
                    i++
                    continue
                }
                trimmed.isEmpty() -> flushParagraph()
                Regex("^#{1,6}\\s+.*").matches(trimmed) -> {
                    flushParagraph()
                    val level = trimmed.takeWhile { it == '#' }.length
                    out.append("<h$level>${inline(trimmed.drop(level).trim().trimEnd('#').trim())}</h$level>")
                }
                Regex("^([-*_])(\\s*\\1){2,}\\s*$").matches(trimmed) -> {
                    flushParagraph()
                    out.append("<hr>")
                }
                trimmed.startsWith(">") -> {
                    flushParagraph()
                    val quote = StringBuilder()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        quote.append(lines[i].trim().removePrefix(">").removePrefix(" ")).append('\n')
                        i++
                    }
                    out.append("<blockquote>").append(render(quote.toString())).append("</blockquote>")
                    continue
                }
                isTableStart(lines, i) -> {
                    flushParagraph()
                    i = renderTable(lines, i, out)
                    continue
                }
                Regex("^\\s*([-*+]|\\d+[.)])\\s+.*").matches(line) -> {
                    flushParagraph()
                    i = renderList(lines, i, out)
                    continue
                }
                else -> paragraph.add(line.trim())
            }
            i++
        }
        flushParagraph()
        return out.toString()
    }

    private fun isTableStart(lines: List<String>, i: Int): Boolean {
        if (i + 1 >= lines.size) return false
        val header = lines[i]
        val separator = lines[i + 1].trim()
        return header.contains('|') && Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$").matches(separator)
    }

    private fun splitRow(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.drop(1)
        if (t.endsWith("|")) t = t.dropLast(1)
        return t.split('|').map { it.trim() }
    }

    private fun renderTable(lines: List<String>, start: Int, out: StringBuilder): Int {
        val header = splitRow(lines[start])
        val aligns = splitRow(lines[start + 1]).map {
            when {
                it.startsWith(":") && it.endsWith(":") -> "center"
                it.endsWith(":") -> "right"
                else -> "left"
            }
        }
        out.append("<div class=\"tw\"><table class=\"dt\"><thead><tr>")
        header.forEachIndexed { index, cell -> out.append("<th style=\"text-align:${aligns.getOrElse(index) { "left" }}\">${inline(cell)}</th>") }
        out.append("</tr></thead><tbody>")
        var i = start + 2
        while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
            out.append("<tr>")
            splitRow(lines[i]).forEachIndexed { index, cell -> out.append("<td style=\"text-align:${aligns.getOrElse(index) { "left" }}\">${inline(cell)}</td>") }
            out.append("</tr>")
            i++
        }
        out.append("</tbody></table></div>")
        return i
    }

    private fun renderList(lines: List<String>, start: Int, out: StringBuilder): Int {
        val regex = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)")
        var i = start
        val baseIndent = regex.find(lines[i])!!.groupValues[1].length
        val ordered = regex.find(lines[i])!!.groupValues[2].first().isDigit()
        out.append(if (ordered) "<ol>" else "<ul>")
        while (i < lines.size) {
            val m = regex.find(lines[i])
            if (m == null) {
                if (lines[i].isBlank()) {
                    if (i + 1 < lines.size && regex.find(lines[i + 1]) != null && regex.find(lines[i + 1])!!.groupValues[1].length >= baseIndent) { i++; continue }
                }
                break
            }
            val indent = m.groupValues[1].length
            if (indent < baseIndent) break
            if (indent > baseIndent) {
                i = renderList(lines, i, out)
                continue
            }
            var text = m.groupValues[3]
            var checkbox = ""
            Regex("^\\[( |x|X)\\]\\s+").find(text)?.let {
                checkbox = if (it.groupValues[1] == " ") "&#9744; " else "&#9745; "
                text = text.substring(it.value.length)
            }
            out.append("<li>").append(checkbox).append(inline(text))
            i++
            if (i < lines.size) {
                val next = regex.find(lines[i])
                if (next != null && next.groupValues[1].length > baseIndent) {
                    i = renderList(lines, i, out)
                }
            }
            out.append("</li>")
        }
        out.append(if (ordered) "</ol>" else "</ul>")
        return i
    }

    fun inline(text: String): String {
        val codes = ArrayList<String>()
        var work = Regex("`([^`]+)`").replace(text) {
            codes.add("<code>${Html.esc(it.groupValues[1])}</code>")
            "\u0001${codes.size - 1}\u0001"
        }
        work = Html.esc(work)
        work = Regex("!\\[([^\\]]*)\\]\\(([^)\\s]+)[^)]*\\)").replace(work) {
            val alt = it.groupValues[1]
            val src = it.groupValues[2]
            if (src.startsWith("data:image/")) "<img src=\"$src\" alt=\"$alt\">" else "<span class=\"imgalt\">[$alt]</span>"
        }
        work = Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)[^)]*\\)").replace(work) {
            val href = it.groupValues[2].replace("&amp;", "&")
            val safe = href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:") || href.startsWith("#")
            if (safe) "<a href=\"${Html.esc(href)}\">${it.groupValues[1]}</a>" else it.groupValues[1]
        }
        work = Regex("&lt;(https?://[^\\s&]+)&gt;").replace(work) { "<a href=\"${it.groupValues[1]}\">${it.groupValues[1]}</a>" }
        work = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__").replace(work) { "<b>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</b>" }
        work = Regex("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])|(?<![_\\w])_(?!\\s)(.+?)(?<!\\s)_(?![_\\w])").replace(work) { "<i>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</i>" }
        work = Regex("~~(.+?)~~").replace(work) { "<s>${it.groupValues[1]}</s>" }
        work = work.replace("  \n", "<br>").replace("\n", " ")
        return Regex("\u0001(\\d+)\u0001").replace(work) { codes[it.groupValues[1].toInt()] }
    }

    const val CSS = """
.md h1,.md h2{border-bottom:1px solid var(--border);padding-bottom:.3em}
.md h1{font-size:1.9em;margin:1.2em 0 .6em}
.md h2{font-size:1.5em;margin:1.2em 0 .6em}
.md h3{font-size:1.25em;margin:1.1em 0 .5em}
.md p{margin:0 0 14px}
.md pre{background:var(--code-bg);border:1px solid var(--border);border-radius:8px;padding:12px 14px;overflow-x:auto}
.md code{font-family:ui-monospace,Menlo,Consolas,monospace;font-size:.9em;background:var(--code-bg);padding:.12em .35em;border-radius:4px}
.md pre code{background:none;padding:0}
.md blockquote{margin:0 0 14px;padding:2px 14px;border-left:4px solid var(--border);color:var(--muted)}
.md ul,.md ol{padding-left:26px;margin:0 0 14px}
.md hr{border:0;border-top:1px solid var(--border);margin:22px 0}
.tw{overflow-x:auto;margin:0 0 14px}
.dt{border:1px solid var(--border)}
.dt th,.dt td{border:1px solid var(--border);padding:6px 10px}
.dt th{background:var(--head-bg)}
.imgalt{color:var(--muted)}
.note{padding:10px 14px;color:var(--muted)}
"""
}
