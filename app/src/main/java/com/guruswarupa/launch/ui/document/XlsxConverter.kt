package com.guruswarupa.launch.ui.document

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object XlsxConverter {

    private const val MAX_ROWS = 5000
    private const val MAX_COLS = 200

    private class CellStyle(
        val numFmtId: Int,
        val bold: Boolean,
        val italic: Boolean,
        val color: String?,
        val background: String?,
        val align: String?
    )

    private class Styles(val cells: List<CellStyle>, val formats: Map<Int, String>)

    private class Sheet(val name: String, val path: String)

    private class SheetData(
        val rows: ArrayList<Pair<Int, ArrayList<Pair<Int, Pair<String, Int>>>>>,
        val merges: List<IntArray>,
        val columnWidths: Map<Int, Double>,
        val hiddenColumns: Set<Int>,
        var truncated: Boolean
    )

    fun convert(file: File, title: String): String {
        ZipPackage(file).use { pkg ->
            val workbook = pkg.xml("xl/workbook.xml") ?: throw IllegalStateException("Missing workbook.xml")
            val rels = pkg.relationships("xl/_rels/workbook.xml.rels", "xl/")
            val date1904 = workbook.find("workbookPr")?.attr("date1904").let { it == "1" || it == "true" }
            val sheets = ArrayList<Sheet>()
            workbook.child("sheets")?.childrenNamed("sheet")?.forEach { s ->
                val relId = s.attr("id") ?: return@forEach
                val path = rels[relId]?.target ?: return@forEach
                if (s.attr("state") == "veryHidden") return@forEach
                sheets.add(Sheet(s.attr("name") ?: "Sheet ${sheets.size + 1}", path))
            }
            if (sheets.isEmpty()) throw IllegalStateException("No sheets")

            val shared = readSharedStrings(pkg)
            val styles = readStyles(pkg)

            val nav = StringBuilder()
            val body = StringBuilder()
            sheets.forEachIndexed { index, sheet ->
                nav.append("<a href=\"#sheet-$index\" class=\"tab\" data-i=\"$index\">${Html.esc(sheet.name)}</a>")
                body.append("<section id=\"sheet-$index\" class=\"sheet\">")
                renderSheet(pkg, sheet, shared, styles, date1904, body)
                body.append("</section>")
            }
            val page = StringBuilder()
            page.append("<div class=\"xl\"><nav class=\"tabs\">").append(nav).append("</nav><div class=\"sheets\">").append(body).append("</div></div>")
            return Html.page(title, page.toString(), css(sheets.size), "xlsx")
        }
    }

    private fun css(sheetCount: Int): String {
        val tabRules = StringBuilder()
        for (i in 0 until sheetCount) {
            tabRules.append("body:has(#sheet-$i:target) a.tab[data-i=\"$i\"]{background:var(--accent);color:#fff;border-color:var(--accent)}\n")
        }
        return """
body.xlsx{overflow-x:hidden}
.xl{display:flex;flex-direction:column;height:100vh}
.tabs{display:flex;gap:6px;overflow-x:auto;padding:8px 10px;background:var(--head-bg);border-bottom:1px solid var(--border);position:sticky;top:0;z-index:5;white-space:nowrap}
a.tab{flex:none;text-decoration:none;color:var(--fg);padding:6px 14px;border:1px solid var(--border);border-radius:16px;font-size:13px}
body:not(:has(.sheet:target)) a.tab[data-i="0"]{background:var(--accent);color:#fff;border-color:var(--accent)}
$tabRules
.sheets{flex:1;overflow:auto;-webkit-overflow-scrolling:touch}
.sheet{display:none}
.sheet:target{display:block}
body:not(:has(.sheet:target)) .sheet:first-child{display:block}
table.grid{border-collapse:separate;border-spacing:0;font-size:13px;width:max-content;min-width:100%}
table.grid th,table.grid td{border-right:1px solid var(--border);border-bottom:1px solid var(--border);padding:4px 8px;white-space:pre;vertical-align:top;min-width:72px;max-width:420px;overflow:hidden;text-overflow:ellipsis}
table.grid thead th{position:sticky;top:0;background:var(--head-bg);z-index:3;font-weight:600;color:var(--muted);text-align:center}
table.grid th.rh,table.grid thead th.corner{min-width:0;width:32px;max-width:32px;padding:4px 3px;font-size:11px}
table.grid th.rh{position:sticky;left:0;background:var(--head-bg);z-index:2;color:var(--muted);font-weight:500;text-align:center}
table.grid thead th.corner{left:0;z-index:4}
td.num{text-align:right;font-variant-numeric:tabular-nums}
.note{padding:10px 14px;color:var(--muted);font-size:13px}
.empty-sheet{padding:30px;text-align:center;color:var(--muted)}
"""
    }

    private fun readSharedStrings(pkg: ZipPackage): List<String> {
        val input = pkg.stream("xl/sharedStrings.xml") ?: return emptyList()
        val result = ArrayList<String>()
        input.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            var current: StringBuilder? = null
            var inText = false
            var skipDepth = 0
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (localName(parser.name)) {
                        "si" -> current = StringBuilder()
                        "t" -> if (skipDepth == 0) inText = true
                        "rPh", "phoneticPr" -> skipDepth++
                    }
                    XmlPullParser.TEXT -> if (inText) current?.append(parser.text)
                    XmlPullParser.END_TAG -> when (localName(parser.name)) {
                        "t" -> inText = false
                        "rPh" -> if (skipDepth > 0) skipDepth--
                        "si" -> {
                            result.add(current?.toString().orEmpty())
                            current = null
                        }
                    }
                }
                event = parser.next()
            }
        }
        return result
    }

    private fun readStyles(pkg: ZipPackage): Styles {
        val root = pkg.xml("xl/styles.xml") ?: return Styles(emptyList(), emptyMap())
        val formats = HashMap<Int, String>()
        root.child("numFmts")?.childrenNamed("numFmt")?.forEach { n ->
            val id = n.attr("numFmtId")?.toIntOrNull() ?: return@forEach
            formats[id] = n.attr("formatCode").orEmpty()
        }
        val fonts = root.child("fonts")?.childrenNamed("font").orEmpty().map { f ->
            Triple(f.child("b") != null, f.child("i") != null, safeCssColor(f.child("color")?.attr("rgb")))
        }
        val fills = root.child("fills")?.childrenNamed("fill").orEmpty().map { f ->
            val pattern = f.child("patternFill")
            if (pattern?.attr("patternType") == "solid") safeCssColor(pattern.child("fgColor")?.attr("rgb")) else null
        }
        val cells = root.child("cellXfs")?.childrenNamed("xf").orEmpty().map { xf ->
            val font = fonts.getOrNull(xf.attr("fontId")?.toIntOrNull() ?: 0)
            CellStyle(
                numFmtId = xf.attr("numFmtId")?.toIntOrNull() ?: 0,
                bold = font?.first == true,
                italic = font?.second == true,
                color = font?.third,
                background = fills.getOrNull(xf.attr("fillId")?.toIntOrNull() ?: 0),
                align = xf.child("alignment")?.attr("horizontal")
            )
        }
        return Styles(cells, formats)
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (c in ref) {
            if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1)
            else if (c in 'a'..'z') n = n * 26 + (c - 'a' + 1)
            else break
        }
        return n - 1
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            n--
            sb.append('A' + n % 26)
            n /= 26
        }
        return sb.reverse().toString()
    }

    private fun readSheet(pkg: ZipPackage, path: String, shared: List<String>, styles: Styles, date1904: Boolean): SheetData {
        val rows = ArrayList<Pair<Int, ArrayList<Pair<Int, Pair<String, Int>>>>>()
        val merges = ArrayList<IntArray>()
        val widths = HashMap<Int, Double>()
        val hidden = HashSet<Int>()
        var truncated = false
        val input = pkg.stream(path) ?: return SheetData(rows, merges, widths, hidden, false)
        input.use {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(it, null)
            var rowIndex = -1
            var rowCells: ArrayList<Pair<Int, Pair<String, Int>>>? = null
            var rowHidden = false
            var cellCol = 0
            var cellType: String? = null
            var cellStyle = 0
            var value: StringBuilder? = null
            var inline: StringBuilder? = null
            var capture = 0
            var inInline = false
            var autoRow = 0
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (localName(parser.name)) {
                        "col" -> {
                            val min = parser.getAttributeValue(null, "min")?.toIntOrNull() ?: 0
                            val max = (parser.getAttributeValue(null, "max")?.toIntOrNull() ?: min).coerceAtMost(min + MAX_COLS)
                            val width = parser.getAttributeValue(null, "width")?.toDoubleOrNull()
                            val isHidden = parser.getAttributeValue(null, "hidden").let { h -> h == "1" || h == "true" }
                            for (c in min..max) {
                                if (width != null) widths[c - 1] = width
                                if (isHidden) hidden.add(c - 1)
                            }
                        }
                        "row" -> {
                            val r = parser.getAttributeValue(null, "r")?.toIntOrNull()
                            rowIndex = if (r != null) r - 1 else autoRow
                            autoRow = rowIndex + 1
                            rowHidden = parser.getAttributeValue(null, "hidden").let { h -> h == "1" || h == "true" }
                            if (rowIndex >= MAX_ROWS) {
                                truncated = true
                                rowCells = null
                            } else {
                                rowCells = ArrayList()
                            }
                        }
                        "c" -> {
                            val ref = parser.getAttributeValue(null, "r")
                            cellCol = if (ref != null) columnIndex(ref) else (rowCells?.lastOrNull()?.first ?: -1) + 1
                            cellType = parser.getAttributeValue(null, "t")
                            cellStyle = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                            value = null
                            inline = null
                        }
                        "v" -> {
                            value = StringBuilder()
                            capture = 1
                        }
                        "is" -> {
                            inline = StringBuilder()
                            inInline = true
                        }
                        "t" -> if (inInline) capture = 2
                        "mergeCell" -> {
                            val ref = parser.getAttributeValue(null, "ref")
                            if (ref != null && ref.contains(':')) {
                                val a = ref.substringBefore(':')
                                val b = ref.substringAfter(':')
                                val r1 = a.dropWhile { ch -> ch.isLetter() }.toIntOrNull()
                                val r2 = b.dropWhile { ch -> ch.isLetter() }.toIntOrNull()
                                if (r1 != null && r2 != null) merges.add(intArrayOf(r1 - 1, columnIndex(a), r2 - 1, columnIndex(b)))
                            }
                        }
                    }
                    XmlPullParser.TEXT -> when (capture) {
                        1 -> value?.append(parser.text)
                        2 -> inline?.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> when (localName(parser.name)) {
                        "v" -> capture = 0
                        "t" -> if (inInline) capture = 0
                        "is" -> inInline = false
                        "c" -> {
                            val cells = rowCells
                            if (cells != null && !rowHidden && cellCol < MAX_COLS) {
                                val text = cellDisplay(cellType, value?.toString(), inline?.toString(), cellStyle, shared, styles, date1904)
                                if (text.isNotEmpty() || styles.cells.getOrNull(cellStyle)?.background != null) {
                                    cells.add(cellCol to (text to cellStyle))
                                }
                            }
                        }
                        "row" -> {
                            val cells = rowCells
                            if (cells != null && !rowHidden && cells.isNotEmpty()) rows.add(rowIndex to cells)
                            rowCells = null
                        }
                    }
                }
                event = parser.next()
            }
        }
        return SheetData(rows, merges, widths, hidden, truncated)
    }

    private fun cellDisplay(
        type: String?,
        raw: String?,
        inline: String?,
        styleIndex: Int,
        shared: List<String>,
        styles: Styles,
        date1904: Boolean
    ): String {
        return when (type) {
            "s" -> raw?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "inlineStr" -> inline.orEmpty()
            "str" -> raw.orEmpty()
            "b" -> if (raw?.trim() == "1") "TRUE" else "FALSE"
            "e" -> raw.orEmpty()
            "d" -> raw.orEmpty()
            else -> {
                val text = raw?.trim().orEmpty()
                if (text.isEmpty()) return ""
                val style = styles.cells.getOrNull(styleIndex)
                formatNumber(text, style?.numFmtId ?: 0, styles.formats, date1904)
            }
        }
    }

    private fun isDateFormat(id: Int, code: String?): Boolean {
        if (id in 14..22 || id in 45..47) return true
        if (code == null) return false
        val cleaned = code.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\[[^\\]]*\\]"), "").replace(Regex("\\\\."), "")
        return cleaned.any { it.lowercaseChar() in "ymdhs" } && !cleaned.contains("General", ignoreCase = true)
    }

    private fun formatNumber(raw: String, id: Int, formats: Map<Int, String>, date1904: Boolean): String {
        val code = formats[id] ?: builtinFormat(id)
        val number = raw.toBigDecimalOrNull() ?: return raw
        if (isDateFormat(id, code)) {
            return formatDate(number.toDouble(), code.orEmpty(), id, date1904) ?: raw
        }
        val section = code?.substringBefore(';').orEmpty()
        if (code == null || section.equals("General", true) || section.isEmpty()) {
            return number.round(MathContext(15)).stripTrailingZeros().toPlainString()
        }
        val percent = section.contains('%')
        val value = if (percent) number.multiply(BigDecimal(100)) else number
        val decimalsPart = section.substringAfter('.', "").takeWhile { it == '0' || it == '#' || it == '?' }
        val decimals = decimalsPart.length
        val thousands = section.contains(",") && (section.contains("#,#") || section.contains("0,0") || section.contains("#,0"))
        val rounded = value.setScale(decimals, java.math.RoundingMode.HALF_UP)
        var text = if (thousands) java.text.DecimalFormat(if (decimals > 0) "#,##0." + "0".repeat(decimals) else "#,##0", java.text.DecimalFormatSymbols(Locale.US)).format(rounded) else rounded.toPlainString()
        if (percent) text += "%"
        val currency = Regex("\\[\\$([^\\]-]*)").find(section)?.groupValues?.get(1)
            ?: Regex("^\"?([$€£¥₹])").find(section)?.groupValues?.get(1)
        if (!currency.isNullOrEmpty()) text = currency + text
        return text
    }

    private fun builtinFormat(id: Int): String? = when (id) {
        1 -> "0"
        2 -> "0.00"
        3 -> "#,##0"
        4 -> "#,##0.00"
        9 -> "0%"
        10 -> "0.00%"
        11 -> "0.00E+00"
        37 -> "#,##0"
        38 -> "#,##0"
        39 -> "#,##0.00"
        40 -> "#,##0.00"
        14 -> "yyyy-mm-dd"
        15 -> "d-mmm-yy"
        16 -> "d-mmm"
        17 -> "mmm-yy"
        18 -> "h:mm AM/PM"
        19 -> "h:mm:ss AM/PM"
        20 -> "h:mm"
        21 -> "h:mm:ss"
        22 -> "yyyy-mm-dd h:mm"
        45 -> "mm:ss"
        46 -> "[h]:mm:ss"
        47 -> "mm:ss.0"
        else -> null
    }

    private fun formatDate(serial: Double, code: String, id: Int, date1904: Boolean): String? {
        return try {
            val epoch = if (date1904) LocalDateTime.of(1904, 1, 1, 0, 0) else LocalDateTime.of(1899, 12, 30, 0, 0)
            val wholeDays = Math.floor(serial).toLong()
            val seconds = Math.round((serial - wholeDays) * 86400.0)
            val dateTime = epoch.plusDays(wholeDays).plusSeconds(seconds)
            val pattern = toJavaPattern(if (code.isEmpty()) builtinFormat(id).orEmpty() else code)
            DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).format(dateTime)
        } catch (_: Exception) {
            null
        }
    }

    private fun toJavaPattern(excel: String): String {
        val code = excel.substringBefore(';')
        val sb = StringBuilder()
        var i = 0
        var lastWasHour = false
        val hasAmPm = code.contains("AM/PM", ignoreCase = true) || code.contains("A/P", ignoreCase = true)
        while (i < code.length) {
            val c = code[i]
            when {
                c == '"' -> {
                    val end = code.indexOf('"', i + 1).let { if (it < 0) code.length else it }
                    sb.append("'").append(code.substring(i + 1, end).replace("'", "''")).append("'")
                    i = end + 1
                }
                c == '[' -> i = code.indexOf(']', i).let { if (it < 0) code.length else it + 1 }
                c == '\\' && i + 1 < code.length -> {
                    sb.append("'").append(code[i + 1]).append("'")
                    i += 2
                }
                c.lowercaseChar() == 'y' -> {
                    val n = run { var k = i; while (k < code.length && code[k].lowercaseChar() == 'y') k++; k - i }
                    sb.append(if (n <= 2) "yy" else "yyyy")
                    i += n
                    lastWasHour = false
                }
                c.lowercaseChar() == 'm' -> {
                    val n = run { var k = i; while (k < code.length && code[k].lowercaseChar() == 'm') k++; k - i }
                    val nextIsSeconds = code.substring(i + n).trimStart().firstOrNull()?.let { it == ':' } == true &&
                        code.substring(i + n).dropWhile { it == ':' }.firstOrNull()?.lowercaseChar() == 's'
                    if (lastWasHour || nextIsSeconds) {
                        sb.append(if (n >= 2) "mm" else "m")
                    } else {
                        sb.append(when (n) { 1 -> "M"; 2 -> "MM"; 3 -> "MMM"; 4 -> "MMMM"; else -> "MMMMM" })
                    }
                    i += n
                    lastWasHour = false
                }
                c.lowercaseChar() == 'd' -> {
                    val n = run { var k = i; while (k < code.length && code[k].lowercaseChar() == 'd') k++; k - i }
                    sb.append(when (n) { 1 -> "d"; 2 -> "dd"; 3 -> "EEE"; else -> "EEEE" })
                    i += n
                    lastWasHour = false
                }
                c.lowercaseChar() == 'h' -> {
                    val n = run { var k = i; while (k < code.length && code[k].lowercaseChar() == 'h') k++; k - i }
                    sb.append(if (hasAmPm) (if (n >= 2) "hh" else "h") else (if (n >= 2) "HH" else "H"))
                    i += n
                    lastWasHour = true
                }
                c.lowercaseChar() == 's' -> {
                    val n = run { var k = i; while (k < code.length && code[k].lowercaseChar() == 's') k++; k - i }
                    sb.append(if (n >= 2) "ss" else "s")
                    i += n
                    lastWasHour = false
                }
                code.regionMatches(i, "AM/PM", 0, 5, ignoreCase = true) -> {
                    sb.append("a")
                    i += 5
                }
                code.regionMatches(i, "A/P", 0, 3, ignoreCase = true) -> {
                    sb.append("a")
                    i += 3
                }
                c.isLetter() -> {
                    sb.append("'").append(c).append("'")
                    i++
                }
                c == '\'' -> {
                    sb.append("''")
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString().ifBlank { "yyyy-MM-dd" }
    }

    private fun renderSheet(pkg: ZipPackage, sheet: Sheet, shared: List<String>, styles: Styles, date1904: Boolean, out: StringBuilder) {
        val data = readSheet(pkg, sheet.path, shared, styles, date1904)
        if (data.rows.isEmpty()) {
            out.append("<div class=\"empty-sheet\">This sheet is empty</div>")
            return
        }
        var maxCol = 0
        for ((_, cells) in data.rows) for ((col, _) in cells) if (col > maxCol) maxCol = col
        for (m in data.merges) if (m[3] > maxCol && m[3] < MAX_COLS) maxCol = m[3]

        val spans = HashMap<Long, IntArray>()
        val covered = HashSet<Long>()
        fun key(r: Int, c: Int): Long = r.toLong() * 1000L + c
        for (m in data.merges) {
            val rowSpan = (m[2] - m[0] + 1).coerceAtLeast(1)
            val colSpan = (m[3] - m[1] + 1).coerceAtLeast(1)
            if (rowSpan == 1 && colSpan == 1) continue
            spans[key(m[0], m[1])] = intArrayOf(rowSpan, colSpan)
            for (r in m[0]..m[2]) for (c in m[1]..m[3]) if (r != m[0] || c != m[1]) covered.add(key(r, c))
        }

        out.append("<table class=\"grid\"><colgroup><col style=\"width:32px\">")
        for (c in 0..maxCol) {
            if (c in data.hiddenColumns) continue
            val width = data.columnWidths[c]
            out.append(if (width != null) "<col style=\"width:${(width * 7).toInt().coerceIn(24, 520)}px\">" else "<col>")
        }
        out.append("</colgroup><thead><tr><th class=\"corner\"></th>")
        for (c in 0..maxCol) {
            if (c in data.hiddenColumns) continue
            out.append("<th>${columnName(c)}</th>")
        }
        out.append("</tr></thead><tbody>")

        var previousRow = -1
        for ((rowIndex, cells) in data.rows) {
            if (previousRow >= 0 && rowIndex - previousRow > 1) {
                val gap = (rowIndex - previousRow - 1).coerceAtMost(3)
                repeat(gap) {
                    out.append("<tr><th class=\"rh\">&nbsp;</th>")
                    out.append("<td colspan=\"${maxCol + 1}\"></td></tr>")
                }
            }
            previousRow = rowIndex
            out.append("<tr><th class=\"rh\">${rowIndex + 1}</th>")
            val byColumn = HashMap<Int, Pair<String, Int>>()
            cells.forEach { byColumn[it.first] = it.second }
            for (c in 0..maxCol) {
                if (c in data.hiddenColumns) continue
                val k = key(rowIndex, c)
                if (k in covered) continue
                val cell = byColumn[c]
                val span = spans[k]
                val styleInfo = cell?.let { styles.cells.getOrNull(it.second) }
                val css = ArrayList<String>()
                if (styleInfo?.bold == true) css.add("font-weight:700")
                if (styleInfo?.italic == true) css.add("font-style:italic")
                styleInfo?.color?.let { css.add("color:$it") }
                styleInfo?.background?.let { css.add("background:$it") }
                val text = cell?.first.orEmpty()
                val numeric = text.isNotEmpty() && text.trim().replace(",", "").replace("%", "").toDoubleOrNull() != null
                val align = styleInfo?.align
                if (align == "center") css.add("text-align:center") else if (align == "right" || (align == null && numeric)) css.add("text-align:right")
                out.append("<td")
                if (span != null) {
                    if (span[0] > 1) out.append(" rowspan=\"${span[0]}\"")
                    if (span[1] > 1) out.append(" colspan=\"${span[1]}\"")
                }
                if (css.isNotEmpty()) out.append(" style=\"${css.joinToString(";")}\"")
                out.append(">").append(Html.esc(text)).append("</td>")
            }
            out.append("</tr>")
        }
        out.append("</tbody></table>")
        if (data.truncated) out.append("<div class=\"note\">Showing the first $MAX_ROWS rows.</div>")
    }
}
