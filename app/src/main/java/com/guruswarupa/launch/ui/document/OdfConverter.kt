package com.guruswarupa.launch.ui.document

import java.io.File
import java.util.Locale

internal object OdfConverter {

    private class TextStyle(val css: String, val bold: Boolean, val italic: Boolean, val underline: Boolean, val strike: Boolean)

    private class Context(val pkg: ZipPackage, val styles: Map<String, TextStyle>, val pageWidthCm: Double, val pageHeightCm: Double)

    fun convert(file: File, title: String): String {
        ZipPackage(file).use { pkg ->
            val content = pkg.xml("content.xml", keepMixed = true) ?: throw IllegalStateException("Missing content.xml")
            val body = content.child("body") ?: throw IllegalStateException("Missing body")
            val styles = HashMap<String, TextStyle>()
            collectStyles(content.child("automatic-styles"), styles)
            pkg.xml("styles.xml")?.let { collectStyles(it.child("styles"), styles); collectStyles(it.child("automatic-styles"), styles) }
            val pageProps = pkg.xml("styles.xml")?.find("page-layout-properties")
            val pageWidth = pageProps?.attr("page-width")?.let { lengthToCm(it) } ?: 28.0
            val pageHeight = pageProps?.attr("page-height")?.let { lengthToCm(it) } ?: 15.75
            val ctx = Context(pkg, styles, pageWidth, pageHeight)
            val sb = StringBuilder()
            return when {
                body.child("spreadsheet") != null -> {
                    renderSpreadsheet(body.child("spreadsheet")!!, ctx, sb)
                    Html.page(title, sb.toString(), SHEET_CSS + sheetTabCss, "xlsx")
                }
                body.child("presentation") != null -> {
                    renderPresentation(body.child("presentation")!!, ctx, sb)
                    Html.page(title, sb.toString(), SLIDE_CSS, "pptx")
                }
                else -> {
                    sb.append("<div class=\"doc odt\">")
                    renderBlocks((body.child("text") ?: body).children, ctx, sb)
                    sb.append("</div>")
                    Html.page(title, sb.toString(), TEXT_CSS)
                }
            }
        }
    }

    private fun lengthToCm(value: String): Double? {
        val number = Regex("^-?[0-9.]+").find(value)?.value?.toDoubleOrNull() ?: return null
        return when {
            value.endsWith("cm") -> number
            value.endsWith("mm") -> number / 10.0
            value.endsWith("in") -> number * 2.54
            value.endsWith("pt") -> number * 2.54 / 72.0
            value.endsWith("px") -> number * 2.54 / 96.0
            else -> number
        }
    }

    private fun collectStyles(root: XmlNode?, out: MutableMap<String, TextStyle>) {
        root ?: return
        for (style in root.childrenNamed("style")) {
            val name = style.attr("name") ?: continue
            val text = style.child("text-properties")
            val para = style.child("paragraph-properties")
            val css = StringBuilder()
            var bold = false
            var italic = false
            var underline = false
            var strike = false
            if (text != null) {
                if (text.attr("font-weight") == "bold") bold = true
                if (text.attr("font-style") == "italic") italic = true
                val ul = text.attr("text-underline-style")
                if (ul != null && ul != "none") underline = true
                val st = text.attr("text-line-through-style")
                if (st != null && st != "none") strike = true
                safeColor(text.attr("color"))?.let { css.append("color:$it;") }
                safeColor(text.attr("background-color"))?.let { css.append("background:$it;") }
                text.attr("font-size")?.let { size ->
                    if (Regex("^[0-9.]+(pt|px|cm|mm|in|%)$").matches(size)) css.append("font-size:$size;")
                }
            }
            when (para?.attr("text-align")) {
                "center" -> css.append("text-align:center;")
                "end", "right" -> css.append("text-align:right;")
                "justify" -> css.append("text-align:justify;")
            }
            safeColor(para?.attr("background-color"))?.let { css.append("background:$it;") }
            out[name] = TextStyle(css.toString(), bold, italic, underline, strike)
        }
    }

    private fun safeColor(value: String?): String? {
        if (value == null) return null
        return if (Regex("^#[0-9a-fA-F]{6}$").matches(value)) value else null
    }

    private fun wrapStyle(html: String, style: TextStyle?): String {
        if (style == null) return html
        var out = html
        if (style.css.isNotEmpty()) out = "<span style=\"${style.css}\">$out</span>"
        if (style.bold) out = "<b>$out</b>"
        if (style.italic) out = "<i>$out</i>"
        if (style.underline) out = "<u>$out</u>"
        if (style.strike) out = "<s>$out</s>"
        return out
    }

    private fun renderBlocks(nodes: List<XmlNode>, ctx: Context, sb: StringBuilder) {
        for (node in nodes) {
            when (node.name) {
                "p" -> renderParagraph(node, ctx, sb, null)
                "h" -> {
                    val level = (node.attr("outline-level")?.toIntOrNull() ?: 1).coerceIn(1, 6)
                    val inner = inline(node, ctx)
                    sb.append("<h$level>${if (inner.isBlank()) "&nbsp;" else inner}</h$level>")
                }
                "list" -> renderList(node, ctx, sb)
                "table" -> renderTable(node, ctx, sb)
                "section", "tracked-changes" -> if (node.name == "section") renderBlocks(node.children, ctx, sb)
                "table-of-content", "index-body" -> renderBlocks(node.children, ctx, sb)
                "index-title" -> renderBlocks(node.children, ctx, sb)
            }
        }
    }

    private fun renderParagraph(node: XmlNode, ctx: Context, sb: StringBuilder, extraStyle: String?) {
        val style = ctx.styles[node.attr("style-name").orEmpty()]
        val inner = inline(node, ctx)
        val css = style?.css.orEmpty() + (extraStyle ?: "")
        val content = wrapStyle(if (inner.isBlank()) "&nbsp;" else inner, style?.let { TextStyle("", it.bold, it.italic, it.underline, it.strike) })
        sb.append("<p${if (inner.isBlank()) " class=\"empty\"" else ""}${if (css.isNotEmpty()) " style=\"$css\"" else ""}>$content</p>")
    }

    private fun renderList(list: XmlNode, ctx: Context, sb: StringBuilder) {
        sb.append("<ul class=\"ol\">")
        for (item in list.childrenNamed("list-item")) {
            sb.append("<li>")
            for (child in item.children) {
                when (child.name) {
                    "p" -> renderParagraph(child, ctx, sb, "margin:0 0 4px;")
                    "h" -> sb.append("<p><b>${inline(child, ctx)}</b></p>")
                    "list" -> renderList(child, ctx, sb)
                }
            }
            sb.append("</li>")
        }
        sb.append("</ul>")
    }

    private fun inline(node: XmlNode, ctx: Context): String {
        val sb = StringBuilder()
        val items = node.mixed ?: arrayListOf<Any>(node.text.toString())
        for (item in items) {
            when (item) {
                is String -> sb.append(Html.esc(item))
                is XmlNode -> when (item.name) {
                    "span" -> sb.append(wrapStyle(inline(item, ctx), ctx.styles[item.attr("style-name").orEmpty()]))
                    "a" -> {
                        val href = item.attr("href").orEmpty()
                        val text = inline(item, ctx)
                        val safe = href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:") || href.startsWith("#")
                        if (safe) sb.append("<a href=\"${Html.esc(href)}\">$text</a>") else sb.append(text)
                    }
                    "s" -> sb.append("&nbsp;".repeat((item.attr("c")?.toIntOrNull() ?: 1).coerceIn(1, 50)))
                    "tab" -> sb.append("<span class=\"tab\"></span>")
                    "line-break" -> sb.append("<br>")
                    "frame" -> item.find("image")?.let { img ->
                        val href = img.attr("href")
                        val uri = if (href != null && !href.startsWith("http")) ctx.pkg.imageDataUri(href) else null
                        if (uri != null) sb.append("<img class=\"pic\" src=\"$uri\" alt=\"\">")
                    }
                    "image" -> {
                        val href = item.attr("href")
                        val uri = if (href != null && !href.startsWith("http")) ctx.pkg.imageDataUri(href) else null
                        if (uri != null) sb.append("<img class=\"pic\" src=\"$uri\" alt=\"\">")
                    }
                    "note" -> sb.append("<sup>${Html.esc(item.child("note-citation")?.text?.toString().orEmpty())}</sup>")
                    "bookmark", "bookmark-start" -> item.attr("name")?.let { sb.append("<a id=\"${Html.esc(it)}\"></a>") }
                    "date", "time", "title", "page-number", "page-count", "file-name", "author-name" -> sb.append(Html.esc(item.allText()))
                    else -> sb.append(inline(item, ctx))
                }
            }
        }
        return sb.toString()
    }

    private fun renderTable(table: XmlNode, ctx: Context, sb: StringBuilder) {
        sb.append("<div class=\"tw\"><table class=\"dt\">")
        for (row in table.children) {
            if (row.name == "table-header-rows" || row.name == "table-rows") {
                row.childrenNamed("table-row").forEach { renderTableRow(it, ctx, sb) }
            } else if (row.name == "table-row") {
                renderTableRow(row, ctx, sb)
            }
        }
        sb.append("</table></div>")
    }

    private fun renderTableRow(row: XmlNode, ctx: Context, sb: StringBuilder) {
        sb.append("<tr>")
        for (cell in row.children) {
            if (cell.name != "table-cell") continue
            val colSpan = cell.attr("number-columns-spanned")?.toIntOrNull() ?: 1
            val rowSpan = cell.attr("number-rows-spanned")?.toIntOrNull() ?: 1
            sb.append("<td")
            if (colSpan > 1) sb.append(" colspan=\"$colSpan\"")
            if (rowSpan > 1) sb.append(" rowspan=\"$rowSpan\"")
            sb.append(">")
            renderBlocks(cell.children, ctx, sb)
            sb.append("</td>")
        }
        sb.append("</tr>")
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

    private fun renderSpreadsheet(root: XmlNode, ctx: Context, sb: StringBuilder) {
        val tables = root.childrenNamed("table")
        val nav = StringBuilder()
        val body = StringBuilder()
        tables.forEachIndexed { index, table ->
            nav.append("<a href=\"#sheet-$index\" class=\"tab\" data-i=\"$index\">${Html.esc(table.attr("name").orEmpty().ifBlank { "Sheet ${index + 1}" })}</a>")
            body.append("<section id=\"sheet-$index\" class=\"sheet\">")
            renderSheet(table, ctx, body)
            body.append("</section>")
        }
        sb.append("<div class=\"xl\"><nav class=\"tabs\">$nav</nav><div class=\"sheets\">$body</div></div>")
        sheetTabCss = tables.indices.joinToString("\n") { "body:has(#sheet-$it:target) a.tab[data-i=\"$it\"]{background:var(--accent);color:#fff;border-color:var(--accent)}" }
    }

    private var sheetTabCss = ""

    private fun renderSheet(table: XmlNode, ctx: Context, out: StringBuilder) {
        val rows = ArrayList<List<Triple<String, Int, Int>>>()
        var maxCols = 0
        var truncated = false
        for (rowNode in table.children) {
            val rowGroup = when (rowNode.name) {
                "table-row" -> listOf(rowNode)
                "table-header-rows", "table-rows" -> rowNode.childrenNamed("table-row")
                else -> emptyList()
            }
            for (row in rowGroup) {
                val repeat = (row.attr("number-rows-repeated")?.toIntOrNull() ?: 1).coerceAtMost(200)
                val cells = ArrayList<Triple<String, Int, Int>>()
                for (cell in row.children) {
                    if (cell.name != "table-cell" && cell.name != "covered-table-cell") continue
                    val times = (cell.attr("number-columns-repeated")?.toIntOrNull() ?: 1).coerceAtMost(100)
                    val text = cell.childrenNamed("p").joinToString("\n") { inline(it, ctx).replace(Regex("<[^>]+>"), "").let { s -> android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY).toString() } }
                    val covered = cell.name == "covered-table-cell"
                    val colSpan = cell.attr("number-columns-spanned")?.toIntOrNull() ?: 1
                    val rowSpan = cell.attr("number-rows-spanned")?.toIntOrNull() ?: 1
                    repeat(if (text.isEmpty()) minOf(times, 3) else times) {
                        cells.add(Triple(if (covered) "\u0000" else text, colSpan, rowSpan))
                    }
                }
                while (cells.isNotEmpty() && cells.last().first.isEmpty()) cells.removeAt(cells.size - 1)
                val isEmptyRow = cells.isEmpty()
                repeat(if (isEmptyRow) 1 else repeat) {
                    if (rows.size < 5000) rows.add(cells) else truncated = true
                }
                if (cells.size > maxCols) maxCols = cells.size
            }
        }
        while (rows.isNotEmpty() && rows.last().isEmpty()) rows.removeAt(rows.size - 1)
        if (rows.isEmpty()) {
            out.append("<div class=\"empty-sheet\">This sheet is empty</div>")
            return
        }
        out.append("<table class=\"grid\"><thead><tr><th class=\"corner\"></th>")
        for (c in 0 until maxCols) out.append("<th>${columnName(c)}</th>")
        out.append("</tr></thead><tbody>")
        rows.forEachIndexed { r, cells ->
            out.append("<tr><th class=\"rh\">${r + 1}</th>")
            for (c in 0 until maxCols) {
                val cell = cells.getOrNull(c)
                if (cell?.first == "\u0000") continue
                val text = cell?.first.orEmpty()
                val numeric = text.isNotEmpty() && text.replace(",", "").replace("%", "").toDoubleOrNull() != null
                out.append("<td")
                if (cell != null && cell.second > 1) out.append(" colspan=\"${cell.second}\"")
                if (cell != null && cell.third > 1) out.append(" rowspan=\"${cell.third}\"")
                if (numeric) out.append(" class=\"num\"")
                out.append(">").append(Html.esc(text)).append("</td>")
            }
            out.append("</tr>")
        }
        out.append("</tbody></table>")
        if (truncated) out.append("<div class=\"note\">Showing the first 5000 rows.</div>")
    }

    private fun renderPresentation(root: XmlNode, ctx: Context, sb: StringBuilder) {
        val pages = root.childrenNamed("page")
        sb.append("<div class=\"deck\">")
        pages.forEachIndexed { index, page ->
            sb.append("<div class=\"slide-wrap\"><div class=\"slide-label\">Slide ${index + 1} of ${pages.size}</div>")
            sb.append("<div class=\"slide\" style=\"aspect-ratio:${(ctx.pageWidthCm * 1000).toLong()}/${(ctx.pageHeightCm * 1000).toLong()};background:#fff;color:#111\">")
            for (frame in page.childrenNamed("frame")) {
                val x = frame.attr("x")?.let { lengthToCm(it) }
                val y = frame.attr("y")?.let { lengthToCm(it) }
                val w = frame.attr("width")?.let { lengthToCm(it) }
                val h = frame.attr("height")?.let { lengthToCm(it) }
                val position = if (x != null && y != null && w != null && h != null) {
                    String.format(
                        Locale.US,
                        "position:absolute;left:%.3f%%;top:%.3f%%;width:%.3f%%;height:%.3f%%;",
                        x / ctx.pageWidthCm * 100, y / ctx.pageHeightCm * 100, w / ctx.pageWidthCm * 100, h / ctx.pageHeightCm * 100
                    )
                } else ""
                val className = frame.attr("class").orEmpty()
                val baseSize = if (className == "title") 32.0 else 20.0
                val fontSize = String.format(Locale.US, "font-size:%.3fcqw;", baseSize * 2.54 / (28.0 * 72.0) * 100.0)
                val textBox = frame.child("text-box")
                val image = frame.child("image")
                sb.append("<div class=\"shape\" style=\"$position$fontSize\">")
                if (textBox != null) {
                    sb.append("<div class=\"text\">")
                    renderBlocks(textBox.children, ctx, sb)
                    sb.append("</div>")
                } else if (image != null) {
                    val href = image.attr("href")
                    val uri = if (href != null && !href.startsWith("http")) ctx.pkg.imageDataUri(href) else null
                    if (uri != null) sb.append("<img class=\"pic\" style=\"width:100%;height:100%;object-fit:contain\" src=\"$uri\" alt=\"\">")
                }
                sb.append("</div>")
            }
            sb.append("</div>")
            val notes = page.child("notes")?.findAll("p")?.joinToString("\n") { it.allText() }?.trim().orEmpty()
            if (notes.isNotEmpty()) {
                sb.append("<details class=\"notes\"><summary>Speaker notes</summary><div>${Html.esc(notes).replace("\n", "<br>")}</div></details>")
            }
            sb.append("</div>")
        }
        sb.append("</div>")
    }

    private const val TEXT_CSS = """
.odt p{margin:0 0 10px}
.odt p.empty{margin:0;height:.9em}
.odt h1{font-size:1.9em;margin:22px 0 12px}
.odt h2{font-size:1.5em;margin:20px 0 10px}
.odt h3{font-size:1.25em;margin:18px 0 8px}
.odt ul.ol{padding-left:26px;margin:0 0 10px}
.tab{display:inline-block;width:2.2em}
.pic{display:block;margin:10px auto;max-width:100%;height:auto}
.tw{overflow-x:auto;margin:12px 0}
.dt{width:100%;border:1px solid var(--border)}
.dt td{border:1px solid var(--border);padding:6px 9px;vertical-align:top}
.dt td p{margin:0 0 4px}
"""

    private const val SHEET_CSS = """
body.xlsx{overflow-x:hidden}
.xl{display:flex;flex-direction:column;height:100vh}
.tabs{display:flex;gap:6px;overflow-x:auto;padding:8px 10px;background:var(--head-bg);border-bottom:1px solid var(--border);position:sticky;top:0;z-index:5;white-space:nowrap}
a.tab{flex:none;text-decoration:none;color:var(--fg);padding:6px 14px;border:1px solid var(--border);border-radius:16px;font-size:13px}
body:not(:has(.sheet:target)) a.tab[data-i="0"]{background:var(--accent);color:#fff;border-color:var(--accent)}
body:has(.sheet:target) a.tab{}
.sheets{flex:1;overflow:auto}
.sheet{display:none}
.sheet:target{display:block}
body:not(:has(.sheet:target)) .sheet:first-child{display:block}
table.grid{border-collapse:separate;border-spacing:0;font-size:13px;width:max-content;min-width:100%}
table.grid th,table.grid td{border-right:1px solid var(--border);border-bottom:1px solid var(--border);padding:4px 8px;white-space:pre;vertical-align:top;min-width:72px;max-width:420px;overflow:hidden;text-overflow:ellipsis}
table.grid thead th{position:sticky;top:0;background:var(--head-bg);z-index:3;font-weight:600;color:var(--muted);text-align:center}
table.grid th.rh,table.grid thead th.corner{min-width:0;width:32px;max-width:32px;padding:4px 3px;font-size:11px}
table.grid th.rh{position:sticky;left:0;background:var(--head-bg);z-index:2;color:var(--muted);font-weight:500;text-align:center}
table.grid thead th.corner{left:0;z-index:4}
td.num{text-align:right}
.note{padding:10px 14px;color:var(--muted);font-size:13px}
.empty-sheet{padding:30px;text-align:center;color:var(--muted)}
"""

    private const val SLIDE_CSS = """
body.pptx{background:#dfe3e8}
html[data-theme="dark"] body.pptx{background:#0d0d0d}
.deck{padding:12px 10px 100px;max-width:1100px;margin:0 auto}
.slide-wrap{margin-bottom:22px}
.slide-label{font-size:12px;color:var(--muted);margin:0 2px 6px}
.slide{position:relative;width:100%;container-type:inline-size;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,.25);border-radius:3px}
.shape{overflow:hidden}
.shape .text p{margin:0 0 .4em;line-height:1.2}
.pic{display:block}
details.notes{margin-top:8px;font-size:14px}
details.notes summary{cursor:pointer;color:var(--accent)}
details.notes div{padding:8px 4px;color:var(--muted)}
"""
}
