package com.guruswarupa.launch.ui.document

import java.io.File

internal object DocxConverter {

    private class StyleInfo(
        val name: String,
        val basedOn: String?,
        val bold: Boolean?,
        val italic: Boolean?,
        val underline: Boolean?,
        val color: String?,
        val sizeHalfPt: Int?,
        val align: String?
    )

    private class RunFormat(
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var strike: Boolean = false,
        var color: String? = null,
        var sizeHalfPt: Int? = null,
        var highlight: String? = null,
        var superscript: Boolean = false,
        var subscript: Boolean = false,
        var caps: Boolean = false
    ) {
        fun copy() = RunFormat(bold, italic, underline, strike, color, sizeHalfPt, highlight, superscript, subscript, caps)
    }

    private class NumLevel(val format: String, val text: String, val start: Int, val indentPx: Int)

    private class Context(
        val pkg: ZipPackage,
        val rels: Map<String, Relationship>,
        val styles: Map<String, StyleInfo>,
        val numbering: Map<String, Map<Int, NumLevel>>,
        val counters: HashMap<String, IntArray> = HashMap()
    )

    fun convert(file: File, title: String): String {
        ZipPackage(file).use { pkg ->
            val document = pkg.xml("word/document.xml") ?: throw IllegalStateException("Missing document.xml")
            val body = document.child("body") ?: throw IllegalStateException("Missing body")
            val ctx = Context(
                pkg,
                pkg.relationships("word/_rels/document.xml.rels", "word/"),
                parseStyles(pkg.xml("word/styles.xml")),
                parseNumbering(pkg.xml("word/numbering.xml"))
            )
            val sb = StringBuilder()
            sb.append("<div class=\"doc docx\">")
            renderBlocks(body.children, ctx, sb)
            renderNotes(pkg, ctx, sb, "word/footnotes.xml", "Footnotes")
            renderNotes(pkg, ctx, sb, "word/endnotes.xml", "Endnotes")
            sb.append("</div>")
            return Html.page(title, sb.toString(), CSS)
        }
    }

    private fun renderNotes(pkg: ZipPackage, ctx: Context, sb: StringBuilder, path: String, heading: String) {
        val root = pkg.xml(path) ?: return
        val notes = root.children.filter {
            val type = it.attr("type")
            (it.name == "footnote" || it.name == "endnote") && type != "separator" && type != "continuationSeparator" && type != "continuationNotice"
        }
        if (notes.isEmpty()) return
        val inner = StringBuilder()
        for (note in notes) renderBlocks(note.children, ctx, inner)
        if (inner.isNotBlank()) {
            sb.append("<hr class=\"notes-rule\"><div class=\"notes\"><div class=\"notes-title\">$heading</div>")
            sb.append(inner)
            sb.append("</div>")
        }
    }

    private fun parseStyles(root: XmlNode?): Map<String, StyleInfo> {
        if (root == null) return emptyMap()
        val map = HashMap<String, StyleInfo>()
        for (style in root.childrenNamed("style")) {
            val id = style.attr("styleId") ?: continue
            val rPr = style.child("rPr")
            val pPr = style.child("pPr")
            map[id] = StyleInfo(
                name = style.child("name")?.attr("val").orEmpty(),
                basedOn = style.child("basedOn")?.attr("val"),
                bold = rPr?.let { toggle(it.child("b")) },
                italic = rPr?.let { toggle(it.child("i")) },
                underline = rPr?.child("u")?.let { it.attr("val") != "none" },
                color = rPr?.child("color")?.attr("val"),
                sizeHalfPt = rPr?.child("sz")?.attr("val")?.toIntOrNull(),
                align = pPr?.child("jc")?.attr("val")
            )
        }
        return map
    }

    private fun toggle(node: XmlNode?): Boolean? {
        node ?: return null
        val v = node.attr("val")
        return !(v == "0" || v == "false" || v == "off")
    }

    private fun parseNumbering(root: XmlNode?): Map<String, Map<Int, NumLevel>> {
        if (root == null) return emptyMap()
        val abstracts = HashMap<String, Map<Int, NumLevel>>()
        for (abs in root.childrenNamed("abstractNum")) {
            val id = abs.attr("abstractNumId") ?: continue
            val levels = HashMap<Int, NumLevel>()
            for (lvl in abs.childrenNamed("lvl")) {
                val index = lvl.attr("ilvl")?.toIntOrNull() ?: continue
                levels[index] = NumLevel(
                    format = lvl.child("numFmt")?.attr("val") ?: "decimal",
                    text = lvl.child("lvlText")?.attr("val").orEmpty(),
                    start = lvl.child("start")?.attr("val")?.toIntOrNull() ?: 1,
                    indentPx = (lvl.child("pPr")?.child("ind")?.attr("left")?.toIntOrNull() ?: ((index + 1) * 360)) / 15
                )
            }
            abstracts[id] = levels
        }
        val result = HashMap<String, Map<Int, NumLevel>>()
        for (num in root.childrenNamed("num")) {
            val numId = num.attr("numId") ?: continue
            val abstractId = num.child("abstractNumId")?.attr("val") ?: continue
            abstracts[abstractId]?.let { result[numId] = it }
        }
        return result
    }

    private fun resolveStyle(ctx: Context, styleId: String?): List<StyleInfo> {
        val chain = ArrayList<StyleInfo>()
        var current = styleId
        var guard = 0
        while (current != null && guard++ < 12) {
            val style = ctx.styles[current] ?: break
            chain.add(style)
            current = style.basedOn
        }
        return chain.reversed()
    }

    private fun applyStyleChain(format: RunFormat, chain: List<StyleInfo>) {
        for (style in chain) {
            style.bold?.let { format.bold = it }
            style.italic?.let { format.italic = it }
            style.underline?.let { format.underline = it }
            style.color?.let { if (it != "auto") format.color = it }
            style.sizeHalfPt?.let { format.sizeHalfPt = it }
        }
    }

    private fun headingLevel(chain: List<StyleInfo>): Int {
        for (style in chain.reversed()) {
            val name = style.name.lowercase()
            if (name == "title") return 1
            if (name == "subtitle") return 2
            val match = Regex("^heading\\s*(\\d)").find(name)
            if (match != null) return match.groupValues[1].toInt().coerceIn(1, 6)
        }
        return 0
    }

    private fun renderBlocks(nodes: List<XmlNode>, ctx: Context, sb: StringBuilder) {
        for (node in nodes) {
            when (node.name) {
                "p" -> renderParagraph(node, ctx, sb)
                "tbl" -> renderTable(node, ctx, sb)
                "sdt" -> node.child("sdtContent")?.let { renderBlocks(it.children, ctx, sb) }
                "customXml", "ins" -> renderBlocks(node.children, ctx, sb)
            }
        }
    }

    private fun renderParagraph(p: XmlNode, ctx: Context, sb: StringBuilder) {
        val pPr = p.child("pPr")
        val styleId = pPr?.child("pStyle")?.attr("val")
        val chain = resolveStyle(ctx, styleId)
        val heading = headingLevel(chain)
        val align = pPr?.child("jc")?.attr("val") ?: chain.lastOrNull { it.align != null }?.align
        val baseFormat = RunFormat()
        applyStyleChain(baseFormat, chain)

        val runs = StringBuilder()
        renderInline(p.children, ctx, baseFormat, runs)

        val styleParts = ArrayList<String>()
        when (align) {
            "center" -> styleParts.add("text-align:center")
            "right", "end" -> styleParts.add("text-align:right")
            "both", "distribute" -> styleParts.add("text-align:justify")
        }
        pPr?.child("shd")?.attr("fill")?.let { safeCssColor(it)?.let { c -> styleParts.add("background:$c") } }
        val indent = pPr?.child("ind")
        val numPr = pPr?.child("numPr")
        val numId = numPr?.child("numId")?.attr("val")
        val styleAttr = { extra: List<String> ->
            val all = styleParts + extra
            if (all.isEmpty()) "" else " style=\"${all.joinToString(";")}\""
        }

        if (numId != null && numId != "0") {
            val level = numPr.child("ilvl")?.attr("val")?.toIntOrNull() ?: 0
            val marker = nextMarker(ctx, numId, level)
            val levelInfo = ctx.numbering[numId]?.get(level)
            val leftPx = levelInfo?.indentPx ?: ((level + 1) * 24)
            sb.append("<div class=\"li\"${styleAttr(listOf("margin-left:${leftPx}px"))}><span class=\"mk\">${Html.esc(marker)}</span><div class=\"lt\">")
            sb.append(if (runs.isBlank()) "&nbsp;" else runs)
            sb.append("</div></div>")
            return
        }

        val extra = ArrayList<String>()
        indent?.attr("left")?.toIntOrNull()?.let { if (it > 0) extra.add("margin-left:${it / 15}px") }
        indent?.attr("firstLine")?.toIntOrNull()?.let { if (it > 0) extra.add("text-indent:${it / 15}px") }
        val content = if (runs.isBlank()) "&nbsp;" else runs.toString()
        if (heading > 0) {
            sb.append("<h$heading${styleAttr(extra)}>$content</h$heading>")
        } else {
            sb.append("<p${if (runs.isBlank()) " class=\"empty\"" else ""}${styleAttr(extra)}>$content</p>")
        }
    }

    private fun nextMarker(ctx: Context, numId: String, level: Int): String {
        val levels = ctx.numbering[numId]
        val counters = ctx.counters.getOrPut(numId) { IntArray(10) { -1 } }
        val lvl = level.coerceIn(0, 9)
        val info = levels?.get(lvl)
        counters[lvl] = if (counters[lvl] < 0) (info?.start ?: 1) else counters[lvl] + 1
        for (deeper in lvl + 1 until 10) counters[deeper] = -1
        if (info == null) return "•"
        if (info.format == "bullet") {
            val symbol = info.text
            return if (symbol.isBlank() || symbol.any { it.code in 0xE000..0xF8FF }) "•" else symbol
        }
        var text = info.text.ifEmpty { "%${lvl + 1}." }
        for (l in 0..lvl) {
            val levelInfo = levels[l]
            val value = if (counters[l] < 0) (levelInfo?.start ?: 1) else counters[l]
            val formatted = when (levelInfo?.format) {
                "lowerLetter" -> alphaNumeral(value, false)
                "upperLetter" -> alphaNumeral(value, true)
                "lowerRoman" -> romanNumeral(value, false)
                "upperRoman" -> romanNumeral(value, true)
                "decimalZero" -> value.toString().padStart(2, '0')
                else -> value.toString()
            }
            text = text.replace("%${l + 1}", formatted)
        }
        return text
    }

    private fun renderInline(nodes: List<XmlNode>, ctx: Context, base: RunFormat, sb: StringBuilder) {
        for (node in nodes) {
            when (node.name) {
                "r" -> renderRun(node, ctx, base, sb)
                "hyperlink" -> {
                    val relId = node.attr("id")
                    val anchor = node.attr("anchor")
                    val rel = relId?.let { ctx.rels[it] }
                    val href = when {
                        rel != null && rel.external -> rel.target
                        anchor != null -> "#$anchor"
                        else -> null
                    }
                    val inner = StringBuilder()
                    renderInline(node.children, ctx, base, inner)
                    if (href != null && isSafeHref(href)) {
                        sb.append("<a href=\"${Html.esc(href)}\">").append(inner).append("</a>")
                    } else {
                        sb.append(inner)
                    }
                }
                "bookmarkStart" -> node.attr("name")?.let { sb.append("<a id=\"${Html.esc(it)}\"></a>") }
                "ins", "smartTag", "fldSimple", "sdt", "customXml" -> {
                    val content = if (node.name == "sdt") node.child("sdtContent")?.children ?: emptyList() else node.children
                    renderInline(content, ctx, base, sb)
                }
            }
        }
    }

    private fun isSafeHref(href: String): Boolean {
        val lower = href.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:") ||
            lower.startsWith("tel:") || lower.startsWith("#")
    }

    private fun renderRun(run: XmlNode, ctx: Context, base: RunFormat, sb: StringBuilder) {
        val format = base.copy()
        val rPr = run.child("rPr")
        if (rPr != null) {
            rPr.child("rStyle")?.attr("val")?.let { applyStyleChain(format, resolveStyle(ctx, it)) }
            rPr.child("b")?.let { format.bold = toggle(it) == true }
            rPr.child("i")?.let { format.italic = toggle(it) == true }
            rPr.child("u")?.let { format.underline = it.attr("val") != "none" }
            rPr.child("strike")?.let { format.strike = toggle(it) == true }
            rPr.child("caps")?.let { format.caps = toggle(it) == true }
            rPr.child("color")?.attr("val")?.let { if (it != "auto") format.color = it }
            rPr.child("sz")?.attr("val")?.toIntOrNull()?.let { format.sizeHalfPt = it }
            rPr.child("highlight")?.attr("val")?.let { format.highlight = it }
            rPr.child("vertAlign")?.attr("val")?.let {
                format.superscript = it == "superscript"
                format.subscript = it == "subscript"
            }
        }
        if (rPr?.child("vanish") != null) return

        val inner = StringBuilder()
        for (child in run.children) {
            when (child.name) {
                "t", "delText" -> if (child.name == "t") inner.append(Html.esc(child.text.toString()))
                "tab" -> inner.append("<span class=\"tab\"></span>")
                "br", "cr" -> {
                    if (child.attr("type") == "page") inner.append("<hr class=\"pb\">") else inner.append("<br>")
                }
                "noBreakHyphen" -> inner.append("‑")
                "sym" -> child.attr("char")?.toIntOrNull(16)?.let { if (it in 0x20..0xF000) inner.append(Html.esc(String(Character.toChars(it)))) }
                "drawing" -> renderDrawing(child, ctx, inner)
                "pict" -> renderPict(child, ctx, inner)
                "footnoteReference", "endnoteReference" -> inner.append("<sup>${Html.esc(child.attr("id").orEmpty())}</sup>")
            }
        }
        if (inner.isEmpty()) return

        val css = ArrayList<String>()
        format.color?.let { safeCssColor(it)?.let { c -> css.add("color:$c") } }
        format.sizeHalfPt?.let { css.add("font-size:${it / 2.0}pt") }
        format.highlight?.let { css.add("background:${highlightColor(it)}") }
        if (format.caps) css.add("text-transform:uppercase")
        var html = inner.toString()
        if (css.isNotEmpty()) html = "<span style=\"${css.joinToString(";")}\">$html</span>"
        if (format.bold) html = "<b>$html</b>"
        if (format.italic) html = "<i>$html</i>"
        if (format.underline) html = "<u>$html</u>"
        if (format.strike) html = "<s>$html</s>"
        if (format.superscript) html = "<sup>$html</sup>"
        if (format.subscript) html = "<sub>$html</sub>"
        sb.append(html)
    }

    private fun highlightColor(name: String): String = when (name) {
        "yellow" -> "#fff59d"
        "green" -> "#b9f6ca"
        "cyan" -> "#b2ebf2"
        "magenta" -> "#f8bbd0"
        "blue" -> "#bbdefb"
        "red" -> "#ffcdd2"
        "darkYellow" -> "#fbc02d"
        "lightGray" -> "#e0e0e0"
        else -> "#fff59d"
    }

    private fun renderDrawing(drawing: XmlNode, ctx: Context, sb: StringBuilder) {
        val blip = drawing.find("blip") ?: return
        val embed = blip.attr("embed") ?: return
        val rel = ctx.rels[embed] ?: return
        val uri = ctx.pkg.imageDataUri(rel.target) ?: return
        val extent = drawing.find("extent")
        val width = extent?.attr("cx")?.toLongOrNull()?.let { emuToPx(it) }
        val height = extent?.attr("cy")?.toLongOrNull()?.let { emuToPx(it) }
        val size = if (width != null && height != null && width > 0) " width=\"$width\" height=\"$height\"" else ""
        sb.append("<img class=\"pic\" src=\"$uri\"$size alt=\"\">")
    }

    private fun renderPict(pict: XmlNode, ctx: Context, sb: StringBuilder) {
        val imageData = pict.find("imagedata") ?: return
        val relId = imageData.attr("id") ?: return
        val rel = ctx.rels[relId] ?: return
        val uri = ctx.pkg.imageDataUri(rel.target) ?: return
        sb.append("<img class=\"pic\" src=\"$uri\" alt=\"\">")
    }

    private class Cell(val html: String, var colSpan: Int, var rowSpan: Int, val background: String?, var skip: Boolean)

    private fun renderTable(tbl: XmlNode, ctx: Context, sb: StringBuilder) {
        val rows = ArrayList<List<Cell>>()
        val restartCells = HashMap<Int, Cell>()
        for (tr in tbl.childrenNamed("tr")) {
            val cells = ArrayList<Cell>()
            var column = 0
            for (tc in tr.childrenNamed("tc")) {
                val tcPr = tc.child("tcPr")
                val span = tcPr?.child("gridSpan")?.attr("val")?.toIntOrNull() ?: 1
                val vMerge = tcPr?.child("vMerge")
                val inner = StringBuilder()
                renderBlocks(tc.children, ctx, inner)
                val bg = tcPr?.child("shd")?.attr("fill")?.let { safeCssColor(it) }
                val cell = Cell(inner.toString(), span, 1, bg, false)
                if (vMerge != null) {
                    if (vMerge.attr("val") == "restart") {
                        restartCells[column] = cell
                    } else {
                        val owner = restartCells[column]
                        if (owner != null) {
                            owner.rowSpan++
                            cell.skip = true
                        }
                    }
                } else {
                    restartCells.remove(column)
                }
                cells.add(cell)
                column += span
            }
            rows.add(cells)
        }
        sb.append("<div class=\"tw\"><table class=\"dt\">")
        for (row in rows) {
            sb.append("<tr>")
            for (cell in row) {
                if (cell.skip) continue
                sb.append("<td")
                if (cell.colSpan > 1) sb.append(" colspan=\"${cell.colSpan}\"")
                if (cell.rowSpan > 1) sb.append(" rowspan=\"${cell.rowSpan}\"")
                if (cell.background != null) sb.append(" style=\"background:${cell.background}\"")
                sb.append(">").append(cell.html).append("</td>")
            }
            sb.append("</tr>")
        }
        sb.append("</table></div>")
    }

    private const val CSS = """
.docx p{margin:0 0 10px}
.docx p.empty{margin:0;height:.9em}
.docx h1{font-size:1.9em;margin:22px 0 12px;line-height:1.25}
.docx h2{font-size:1.5em;margin:20px 0 10px;line-height:1.3}
.docx h3{font-size:1.25em;margin:18px 0 8px}
.docx h4,.docx h5,.docx h6{font-size:1.05em;margin:14px 0 6px}
.li{display:flex;margin:0 0 6px}
.li .mk{flex:none;min-width:1.6em;padding-right:.4em;text-align:right}
.li .lt{flex:1}
.tab{display:inline-block;width:2.2em}
.pic{display:block;margin:10px auto;max-width:100%;height:auto}
.tw{overflow-x:auto;margin:12px 0}
.dt{width:100%;border:1px solid var(--border)}
.dt td{border:1px solid var(--border);padding:6px 9px;vertical-align:top}
.dt td p{margin:0 0 4px}
hr.pb{border:0;border-top:2px dashed var(--border);margin:26px 0}
.notes{font-size:.9em;color:var(--muted)}
.notes-title{font-weight:700;margin-bottom:6px}
hr.notes-rule{border:0;border-top:1px solid var(--border);margin:30px 0 14px}
"""
}
