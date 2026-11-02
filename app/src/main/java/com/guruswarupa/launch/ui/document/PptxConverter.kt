package com.guruswarupa.launch.ui.document

import java.io.File
import java.util.Locale

internal object PptxConverter {

    private class Frame(val x: Double, val y: Double, val w: Double, val h: Double, val rot: Double, val flipH: Boolean, val flipV: Boolean)

    private class Transform(
        val offX: Double = 0.0,
        val offY: Double = 0.0,
        val childOffX: Double = 0.0,
        val childOffY: Double = 0.0,
        val scaleX: Double = 1.0,
        val scaleY: Double = 1.0
    ) {
        fun apply(frame: Frame): Frame = Frame(
            offX + (frame.x - childOffX) * scaleX,
            offY + (frame.y - childOffY) * scaleY,
            frame.w * scaleX,
            frame.h * scaleY,
            frame.rot,
            frame.flipH,
            frame.flipV
        )
    }

    private class Placeholder(val type: String?, val idx: String?, val frame: Frame?, val node: XmlNode)

    private class Part(val root: XmlNode, val rels: Map<String, Relationship>, val placeholders: List<Placeholder>)

    private class Theme(val colors: Map<String, String>, val clrMap: Map<String, String>) {
        fun resolve(name: String): String? {
            val mapped = clrMap[name] ?: name
            return colors[mapped]
        }
    }

    private class Context(
        val pkg: ZipPackage,
        val slideWidth: Double,
        val slideHeight: Double,
        val theme: Theme,
        val master: Part?,
        val masterStyles: XmlNode?
    )

    fun convert(file: File, title: String): String {
        ZipPackage(file).use { pkg ->
            val presentation = pkg.xml("ppt/presentation.xml") ?: throw IllegalStateException("Missing presentation.xml")
            val presRels = pkg.relationships("ppt/_rels/presentation.xml.rels", "ppt/")
            val size = presentation.find("sldSz")
            val width = size?.attr("cx")?.toDoubleOrNull() ?: 9144000.0
            val height = size?.attr("cy")?.toDoubleOrNull() ?: 6858000.0

            val slidePaths = ArrayList<String>()
            presentation.find("sldIdLst")?.childrenNamed("sldId")?.forEach { id ->
                val rid = id.attr("r:id") ?: return@forEach
                presRels[rid]?.target?.let { slidePaths.add(it) }
            }
            if (slidePaths.isEmpty()) throw IllegalStateException("No slides")

            val theme = readTheme(pkg, presRels)
            val body = StringBuilder()
            body.append("<div class=\"deck\">")
            slidePaths.forEachIndexed { index, path ->
                val slide = loadPart(pkg, path) ?: return@forEachIndexed
                val layoutRel = slide.rels.values.firstOrNull { it.type.endsWith("/slideLayout") }
                val layout = layoutRel?.let { loadPart(pkg, it.target) }
                val masterRel = layout?.rels?.values?.firstOrNull { it.type.endsWith("/slideMaster") }
                val master = masterRel?.let { loadPart(pkg, it.target) }
                val slideTheme = Theme(theme.colors, readClrMap(master?.root) ?: theme.clrMap)
                val ctx = Context(pkg, width, height, slideTheme, master, master?.root?.find("txStyles"))

                body.append("<div class=\"slide-wrap\"><div class=\"slide-label\">Slide ${index + 1} of ${slidePaths.size}</div>")
                body.append("<div class=\"slide\" style=\"aspect-ratio:${width.toLong()}/${height.toLong()};${backgroundCss(ctx, slide, layout, master)}\">")
                val showMaster = slide.root.attr("showMasterSp") != "0"
                if (showMaster && layout?.root?.attr("showMasterSp") != "0" && master != null) {
                    renderTree(master.root.find("spTree"), ctx, master, null, null, Transform(), false, body)
                }
                if (showMaster && layout != null) {
                    renderTree(layout.root.find("spTree"), ctx, layout, null, null, Transform(), false, body)
                }
                renderTree(slide.root.find("spTree"), ctx, slide, layout, master, Transform(), true, body)
                body.append("</div>")
                val notes = readNotes(pkg, slide)
                if (notes.isNotBlank()) {
                    body.append("<details class=\"notes\"><summary>Speaker notes</summary><div>${Html.esc(notes).replace("\n", "<br>")}</div></details>")
                }
                body.append("</div>")
            }
            body.append("</div>")
            return Html.page(title, body.toString(), CSS, "pptx")
        }
    }

    private fun loadPart(pkg: ZipPackage, path: String): Part? {
        val root = pkg.xml(path) ?: return null
        val dir = path.substringBeforeLast('/', "") + "/"
        val relsPath = dir + "_rels/" + path.substringAfterLast('/') + ".rels"
        val rels = pkg.relationships(relsPath, dir)
        val placeholders = ArrayList<Placeholder>()
        collectPlaceholders(root.find("spTree"), placeholders)
        return Part(root, rels, placeholders)
    }

    private fun collectPlaceholders(tree: XmlNode?, out: MutableList<Placeholder>) {
        tree ?: return
        for (node in tree.children) {
            if (node.name == "sp") {
                val ph = node.child("nvSpPr")?.child("nvPr")?.child("ph")
                if (ph != null) {
                    out.add(Placeholder(ph.attr("type"), ph.attr("idx"), frameOf(node.child("spPr")?.child("xfrm")), node))
                }
            } else if (node.name == "grpSp") {
                collectPlaceholders(node, out)
            }
        }
    }

    private fun readTheme(pkg: ZipPackage, presRels: Map<String, Relationship>): Theme {
        val themePath = presRels.values.firstOrNull { it.type.endsWith("/theme") }?.target
            ?: pkg.names().firstOrNull { it.startsWith("ppt/theme/theme") && it.endsWith(".xml") }
        val colors = HashMap<String, String>()
        val scheme = themePath?.let { pkg.xml(it) }?.find("clrScheme")
        scheme?.children?.forEach { c ->
            val value = c.child("srgbClr")?.attr("val") ?: c.child("sysClr")?.attr("lastClr")
            if (value != null) colors[c.name] = value
        }
        if (!colors.containsKey("dk1")) colors["dk1"] = "000000"
        if (!colors.containsKey("lt1")) colors["lt1"] = "FFFFFF"
        return Theme(colors, mapOf("bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2", "tx2" to "dk2"))
    }

    private fun readClrMap(master: XmlNode?): Map<String, String>? {
        val map = master?.find("clrMap") ?: return null
        val result = HashMap<String, String>()
        for ((k, v) in map.attrs) if (!k.contains(':')) result[k] = v
        return result
    }

    private fun frameOf(xfrm: XmlNode?): Frame? {
        xfrm ?: return null
        val off = xfrm.child("off")
        val ext = xfrm.child("ext")
        val x = off?.attr("x")?.toDoubleOrNull() ?: return null
        val y = off.attr("y")?.toDoubleOrNull() ?: return null
        val w = ext?.attr("cx")?.toDoubleOrNull() ?: return null
        val h = ext.attr("cy")?.toDoubleOrNull() ?: return null
        return Frame(
            x, y, w, h,
            (xfrm.attr("rot")?.toDoubleOrNull() ?: 0.0) / 60000.0,
            xfrm.attr("flipH") == "1",
            xfrm.attr("flipV") == "1"
        )
    }

    private fun colorOf(parent: XmlNode?, ctx: Context): String? {
        parent ?: return null
        val solid = parent.child("solidFill") ?: return null
        return colorFromFill(solid, ctx)
    }

    private fun colorFromFill(fill: XmlNode, ctx: Context): String? {
        fill.child("srgbClr")?.let { return applyColorMods("#" + (it.attr("val") ?: return null), it) }
        fill.child("sysClr")?.let { return "#" + (it.attr("lastClr") ?: "000000") }
        fill.child("schemeClr")?.let { node ->
            val base = ctx.theme.resolve(node.attr("val") ?: return null) ?: return null
            return applyColorMods("#$base", node)
        }
        fill.child("prstClr")?.let { return when (it.attr("val")) { "white" -> "#FFFFFF"; "black" -> "#000000"; else -> null } }
        return null
    }

    private fun applyColorMods(hex: String, node: XmlNode): String {
        var r = hex.substring(1, 3).toInt(16)
        var g = hex.substring(3, 5).toInt(16)
        var b = hex.substring(5, 7).toInt(16)
        node.child("lumMod")?.attr("val")?.toDoubleOrNull()?.let {
            val f = it / 100000.0
            r = (r * f).toInt().coerceIn(0, 255); g = (g * f).toInt().coerceIn(0, 255); b = (b * f).toInt().coerceIn(0, 255)
        }
        node.child("lumOff")?.attr("val")?.toDoubleOrNull()?.let {
            val f = it / 100000.0
            r = (r + (255 - r) * f).toInt().coerceIn(0, 255); g = (g + (255 - g) * f).toInt().coerceIn(0, 255); b = (b + (255 - b) * f).toInt().coerceIn(0, 255)
        }
        node.child("tint")?.attr("val")?.toDoubleOrNull()?.let {
            val f = 1.0 - it / 100000.0
            r = (r + (255 - r) * f).toInt().coerceIn(0, 255); g = (g + (255 - g) * f).toInt().coerceIn(0, 255); b = (b + (255 - b) * f).toInt().coerceIn(0, 255)
        }
        node.child("shade")?.attr("val")?.toDoubleOrNull()?.let {
            val f = it / 100000.0
            r = (r * f).toInt().coerceIn(0, 255); g = (g * f).toInt().coerceIn(0, 255); b = (b * f).toInt().coerceIn(0, 255)
        }
        return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    }

    private fun backgroundCss(ctx: Context, slide: Part, layout: Part?, master: Part?): String {
        for (part in listOfNotNull(slide, layout, master)) {
            val bg = part.root.find("bg") ?: continue
            val props = bg.child("bgPr")
            if (props != null) {
                colorOf(props, ctx)?.let { return "background:$it;" }
                props.child("gradFill")?.find("gs")?.let { gs ->
                    colorFromFill(gs, ctx)?.let { return "background:$it;" }
                }
                props.child("blipFill")?.find("blip")?.attr("r:embed")?.let { embed ->
                    val uri = ctx.pkg.imageDataUri(part.rels[embed]?.target)
                    if (uri != null) return "background:url('$uri') center/cover no-repeat;"
                }
            }
            bg.child("bgRef")?.let { ref ->
                colorFromFill(ref, ctx)?.let { return "background:$it;" }
            }
        }
        val bg1 = ctx.theme.resolve("bg1") ?: "FFFFFF"
        return "background:#$bg1;"
    }

    private fun readNotes(pkg: ZipPackage, slide: Part): String {
        val notesPath = slide.rels.values.firstOrNull { it.type.endsWith("/notesSlide") }?.target ?: return ""
        val root = pkg.xml(notesPath) ?: return ""
        val sb = StringBuilder()
        root.find("spTree")?.findAll("sp")?.forEach { sp ->
            val ph = sp.child("nvSpPr")?.child("nvPr")?.child("ph")
            if (ph?.attr("type") == "body") {
                sp.child("txBody")?.childrenNamed("p")?.forEach { p ->
                    val text = p.findAll("t").joinToString("") { it.text.toString() }
                    if (text.isNotBlank()) sb.append(text).append('\n')
                }
            }
        }
        return sb.toString().trim()
    }

    private fun renderTree(
        tree: XmlNode?,
        ctx: Context,
        part: Part,
        layout: Part?,
        master: Part?,
        transform: Transform,
        includePlaceholders: Boolean,
        out: StringBuilder
    ) {
        tree ?: return
        for (node in tree.children) {
            when (node.name) {
                "sp" -> renderShape(node, ctx, part, layout, master, transform, includePlaceholders, out)
                "pic" -> renderPicture(node, ctx, part, transform, out)
                "cxnSp" -> renderConnector(node, ctx, transform, out)
                "graphicFrame" -> renderGraphicFrame(node, ctx, part, transform, out)
                "grpSp" -> {
                    val xfrm = node.child("grpSpPr")?.child("xfrm")
                    val frame = frameOf(xfrm)
                    val childOff = xfrm?.child("chOff")
                    val childExt = xfrm?.child("chExt")
                    val inner = if (frame != null && childOff != null && childExt != null) {
                        val cw = childExt.attr("cx")?.toDoubleOrNull() ?: frame.w
                        val ch = childExt.attr("cy")?.toDoubleOrNull() ?: frame.h
                        val mapped = transform.apply(frame)
                        Transform(
                            mapped.x, mapped.y,
                            childOff.attr("x")?.toDoubleOrNull() ?: 0.0,
                            childOff.attr("y")?.toDoubleOrNull() ?: 0.0,
                            if (cw > 0) mapped.w / cw else 1.0,
                            if (ch > 0) mapped.h / ch else 1.0
                        )
                    } else transform
                    renderTree(node, ctx, part, layout, master, inner, includePlaceholders, out)
                }
            }
        }
    }

    private fun positionCss(frame: Frame, ctx: Context): String {
        val sb = StringBuilder("position:absolute;")
        sb.append("left:${pct(frame.x, ctx.slideWidth)};top:${pct(frame.y, ctx.slideHeight)};")
        sb.append("width:${pct(frame.w, ctx.slideWidth)};height:${pct(frame.h, ctx.slideHeight)};")
        val transforms = ArrayList<String>()
        if (frame.rot != 0.0) transforms.add("rotate(${frame.rot}deg)")
        if (frame.flipH) transforms.add("scaleX(-1)")
        if (frame.flipV) transforms.add("scaleY(-1)")
        if (transforms.isNotEmpty()) sb.append("transform:${transforms.joinToString(" ")};")
        return sb.toString()
    }

    private fun pct(value: Double, total: Double): String = String.format(Locale.US, "%.3f%%", value / total * 100.0)

    private fun cqw(emu: Double, ctx: Context): String = String.format(Locale.US, "%.3fcqw", emu / ctx.slideWidth * 100.0)

    private fun fontCqw(sizeHundredths: Double, ctx: Context): String =
        String.format(Locale.US, "%.3fcqw", (sizeHundredths / 100.0) / (ctx.slideWidth / 12700.0) * 100.0)

    private fun findPlaceholder(list: List<Placeholder>?, type: String?, idx: String?): Placeholder? {
        list ?: return null
        if (idx != null) list.firstOrNull { it.idx == idx && (type == null || it.type == null || it.type == type) }?.let { return it }
        if (type != null) {
            val normalized = normalizeType(type)
            list.firstOrNull { it.type != null && normalizeType(it.type) == normalized }?.let { return it }
        }
        return null
    }

    private fun normalizeType(type: String): String = when (type) {
        "ctrTitle" -> "title"
        "subTitle" -> "body"
        else -> type
    }

    private fun renderShape(
        sp: XmlNode,
        ctx: Context,
        part: Part,
        layout: Part?,
        master: Part?,
        transform: Transform,
        includePlaceholders: Boolean,
        out: StringBuilder
    ) {
        val ph = sp.child("nvSpPr")?.child("nvPr")?.child("ph")
        if (ph != null && !includePlaceholders) return
        val spPr = sp.child("spPr")
        var frame = frameOf(spPr?.child("xfrm"))
        var layoutPlaceholder: Placeholder? = null
        var masterPlaceholder: Placeholder? = null
        if (ph != null) {
            layoutPlaceholder = findPlaceholder(layout?.placeholders, ph.attr("type"), ph.attr("idx"))
            masterPlaceholder = findPlaceholder(master?.placeholders, ph.attr("type") ?: layoutPlaceholder?.type, ph.attr("idx") ?: layoutPlaceholder?.idx)
            if (frame == null) frame = layoutPlaceholder?.frame ?: masterPlaceholder?.frame
        }
        frame ?: return
        val placed = transform.apply(frame)

        val css = StringBuilder(positionCss(placed, ctx))
        val fillNode = spPr
        colorOf(fillNode, ctx)?.let { css.append("background:$it;") }
        if (spPr?.child("gradFill") != null) {
            spPr.child("gradFill")?.find("gs")?.let { gs -> colorFromFill(gs, ctx)?.let { css.append("background:$it;") } }
        }
        val line = spPr?.child("ln")
        if (line != null && line.child("noFill") == null) {
            val color = colorOf(line, ctx)
            if (color != null) {
                val width = (line.attr("w")?.toDoubleOrNull() ?: 12700.0)
                css.append("border:${cqw(width, ctx)} solid $color;")
            }
        }
        when (spPr?.child("prstGeom")?.attr("prst")) {
            "ellipse" -> css.append("border-radius:50%;")
            "roundRect" -> css.append("border-radius:10%;")
        }

        val txBody = sp.child("txBody")
        val bodyPr = txBody?.child("bodyPr")
        val anchor = bodyPr?.attr("anchor") ?: layoutPlaceholder?.node?.child("txBody")?.child("bodyPr")?.attr("anchor")
            ?: masterPlaceholder?.node?.child("txBody")?.child("bodyPr")?.attr("anchor")
        val insetL = bodyPr?.attr("lIns")?.toDoubleOrNull() ?: 91440.0
        val insetR = bodyPr?.attr("rIns")?.toDoubleOrNull() ?: 91440.0
        val insetT = bodyPr?.attr("tIns")?.toDoubleOrNull() ?: 45720.0
        val insetB = bodyPr?.attr("bIns")?.toDoubleOrNull() ?: 45720.0
        val textHtml = StringBuilder()
        if (txBody != null) {
            renderParagraphs(txBody, ctx, ph, layoutPlaceholder, masterPlaceholder, textHtml)
        }
        if (textHtml.isBlank() && fillNode?.child("solidFill") == null && spPr?.child("gradFill") == null && line == null) return

        out.append("<div class=\"shape\" style=\"$css\">")
        if (textHtml.isNotBlank()) {
            val justify = when (anchor) {
                "ctr" -> "center"
                "b" -> "flex-end"
                else -> "flex-start"
            }
            out.append("<div class=\"text\" style=\"justify-content:$justify;padding:${cqw(insetT, ctx)} ${cqw(insetR, ctx)} ${cqw(insetB, ctx)} ${cqw(insetL, ctx)}\">")
            out.append(textHtml)
            out.append("</div>")
        }
        out.append("</div>")
    }

    private fun defaultSizeHundredths(
        ph: XmlNode?,
        level: Int,
        layoutPlaceholder: Placeholder?,
        masterPlaceholder: Placeholder?,
        ctx: Context
    ): Double {
        val type = ph?.attr("type")
        val styleName = when {
            type == "title" || type == "ctrTitle" -> "titleStyle"
            ph != null -> "bodyStyle"
            else -> "otherStyle"
        }
        fun lookup(lvl: Int): Double? {
            val key = "lvl${lvl + 1}pPr"
            return layoutPlaceholder?.node?.child("txBody")?.child("lstStyle")?.child(key)?.child("defRPr")?.attr("sz")?.toDoubleOrNull()
                ?: masterPlaceholder?.node?.child("txBody")?.child("lstStyle")?.child(key)?.child("defRPr")?.attr("sz")?.toDoubleOrNull()
                ?: ctx.masterStyles?.child(styleName)?.child(key)?.child("defRPr")?.attr("sz")?.toDoubleOrNull()
        }
        lookup(level)?.let { return it }
        lookup(0)?.let { return it * Math.pow(0.85, level.toDouble()) }
        val base = when (type) {
            "title" -> 4400.0
            "ctrTitle" -> 6000.0
            "subTitle" -> 2400.0
            else -> 1800.0
        }
        return base * Math.pow(0.85, level.toDouble())
    }

    private fun renderParagraphs(
        txBody: XmlNode,
        ctx: Context,
        ph: XmlNode?,
        layoutPlaceholder: Placeholder?,
        masterPlaceholder: Placeholder?,
        out: StringBuilder
    ) {
        var autoNumber = 0
        val isBody = ph != null && ph.attr("type").let { it == null || it == "body" || it == "obj" }
        for (p in txBody.childrenNamed("p")) {
            val pPr = p.child("pPr")
            val level = pPr?.attr("lvl")?.toIntOrNull() ?: 0
            val align = pPr?.attr("algn") ?: layoutPlaceholder?.node?.child("txBody")?.child("lstStyle")?.child("lvl1pPr")?.attr("algn")
                ?: masterPlaceholder?.node?.child("txBody")?.child("lstStyle")?.child("lvl1pPr")?.attr("algn")
            val defaultSize = defaultSizeHundredths(ph, level, layoutPlaceholder, masterPlaceholder, ctx)
            val runs = StringBuilder()
            var any = false
            for (child in p.children) {
                when (child.name) {
                    "r", "fld" -> {
                        val text = child.child("t")?.text?.toString().orEmpty()
                        if (text.isEmpty()) continue
                        any = true
                        runs.append(renderRun(child, text, defaultSize, ctx))
                    }
                    "br" -> runs.append("<br>")
                }
            }
            val endSize = p.child("endParaRPr")?.attr("sz")?.toDoubleOrNull() ?: defaultSize
            val bulletNone = pPr?.child("buNone") != null
            val bulletChar = pPr?.child("buChar")?.attr("char")
            val autoNum = pPr?.child("buAutoNum")
            val marL = pPr?.attr("marL")?.toDoubleOrNull() ?: if (isBody) 342900.0 * (level + 1) else 0.0
            val indent = pPr?.attr("indent")?.toDoubleOrNull() ?: if (isBody) -285750.0 else 0.0
            var marker: String? = null
            if (any && !bulletNone) {
                if (autoNum != null) {
                    autoNumber++
                    marker = "$autoNumber."
                } else if (bulletChar != null) {
                    marker = bulletChar.takeIf { it.isNotBlank() && it.none { c -> c.code in 0xE000..0xF8FF } } ?: "•"
                } else if (isBody) {
                    marker = "•"
                }
            }
            if (!any) autoNumber = 0
            val style = StringBuilder("font-size:${fontCqw(endSize, ctx)};")
            when (align) {
                "ctr" -> style.append("text-align:center;")
                "r" -> style.append("text-align:right;")
                "just" -> style.append("text-align:justify;")
            }
            if (marL > 0 || indent != 0.0) {
                style.append("padding-left:${cqw(marL, ctx)};")
            }
            out.append("<div class=\"para\" style=\"$style\">")
            if (marker != null) {
                out.append("<span class=\"bu\" style=\"margin-left:${cqw(indent.coerceAtMost(0.0), ctx)}\">${Html.esc(marker)}</span>")
            }
            out.append(if (any) runs else "&nbsp;")
            out.append("</div>")
        }
    }

    private fun renderRun(run: XmlNode, text: String, defaultSize: Double, ctx: Context): String {
        val rPr = run.child("rPr")
        val css = StringBuilder()
        val size = rPr?.attr("sz")?.toDoubleOrNull() ?: defaultSize
        css.append("font-size:${fontCqw(size, ctx)};")
        val color = colorOf(rPr, ctx) ?: ("#" + (ctx.theme.resolve("tx1") ?: "000000"))
        css.append("color:$color;")
        rPr?.child("latin")?.attr("typeface")?.let { face ->
            if (face.matches(Regex("[A-Za-z0-9 \\-]+")) && !face.startsWith("+")) css.append("font-family:'$face',sans-serif;")
        }
        var html = Html.esc(text)
        html = "<span style=\"$css\">$html</span>"
        if (rPr?.attr("b") == "1") html = "<b>$html</b>"
        if (rPr?.attr("i") == "1") html = "<i>$html</i>"
        if (rPr?.attr("u").let { it != null && it != "none" }) html = "<u>$html</u>"
        if (rPr?.attr("strike").let { it != null && it != "noStrike" }) html = "<s>$html</s>"
        rPr?.attr("baseline")?.toIntOrNull()?.let { if (it > 0) html = "<sup>$html</sup>" else if (it < 0) html = "<sub>$html</sub>" }
        return html
    }

    private fun renderPicture(pic: XmlNode, ctx: Context, part: Part, transform: Transform, out: StringBuilder) {
        val frame = frameOf(pic.child("spPr")?.child("xfrm")) ?: return
        val embed = pic.child("blipFill")?.child("blip")?.attr("r:embed") ?: return
        val uri = ctx.pkg.imageDataUri(part.rels[embed]?.target) ?: return
        val placed = transform.apply(frame)
        out.append("<img class=\"pic\" style=\"${positionCss(placed, ctx)}\" src=\"$uri\" alt=\"\">")
    }

    private fun renderConnector(node: XmlNode, ctx: Context, transform: Transform, out: StringBuilder) {
        val spPr = node.child("spPr")
        val frame = frameOf(spPr?.child("xfrm")) ?: return
        val color = colorOf(spPr?.child("ln"), ctx) ?: return
        val width = spPr?.child("ln")?.attr("w")?.toDoubleOrNull() ?: 12700.0
        val placed = transform.apply(frame)
        val horizontal = placed.h <= placed.w
        val css = StringBuilder(positionCss(placed, ctx))
        if (horizontal) css.append("border-top:${cqw(width, ctx)} solid $color;height:0;") else css.append("border-left:${cqw(width, ctx)} solid $color;width:0;")
        out.append("<div style=\"$css\"></div>")
    }

    private fun renderGraphicFrame(node: XmlNode, ctx: Context, part: Part, transform: Transform, out: StringBuilder) {
        val frame = frameOf(node.child("xfrm")) ?: return
        val placed = transform.apply(frame)
        val table = node.find("tbl")
        if (table != null) {
            renderTable(table, placed, ctx, out)
            return
        }
        val hasChart = node.find("chart") != null
        val label = if (hasChart) "Chart" else "Object"
        out.append("<div class=\"shape placeholder-box\" style=\"${positionCss(placed, ctx)}\"><div class=\"text\" style=\"justify-content:center;text-align:center;font-size:${fontCqw(1800.0, ctx)}\">$label</div></div>")
    }

    private fun renderTable(tbl: XmlNode, frame: Frame, ctx: Context, out: StringBuilder) {
        val widths = tbl.child("tblGrid")?.childrenNamed("gridCol")?.map { it.attr("w")?.toDoubleOrNull() ?: 0.0 }.orEmpty()
        val total = widths.sum().takeIf { it > 0 } ?: frame.w
        out.append("<div class=\"shape\" style=\"${positionCss(frame, ctx)}\"><table class=\"ptable\">")
        out.append("<colgroup>")
        widths.forEach { out.append("<col style=\"width:${String.format(Locale.US, "%.2f", it / total * 100)}%\">") }
        out.append("</colgroup>")
        for (tr in tbl.childrenNamed("tr")) {
            out.append("<tr>")
            for (tc in tr.childrenNamed("tc")) {
                if (tc.attr("hMerge") == "1" || tc.attr("vMerge") == "1") continue
                val span = tc.attr("gridSpan")?.toIntOrNull() ?: 1
                val rowSpan = tc.attr("rowSpan")?.toIntOrNull() ?: 1
                val bg = colorOf(tc.child("tcPr"), ctx)
                out.append("<td")
                if (span > 1) out.append(" colspan=\"$span\"")
                if (rowSpan > 1) out.append(" rowspan=\"$rowSpan\"")
                if (bg != null) out.append(" style=\"background:$bg\"")
                out.append(">")
                tc.child("txBody")?.let { renderParagraphs(it, ctx, null, null, null, out) }
                out.append("</td>")
            }
            out.append("</tr>")
        }
        out.append("</table></div>")
    }

    private const val CSS = """
body.pptx{background:#dfe3e8}
html[data-theme="dark"] body.pptx{background:#0d0d0d}
.deck{padding:12px 10px 100px;max-width:1100px;margin:0 auto}
.slide-wrap{margin-bottom:22px}
.slide-label{font-size:12px;color:var(--muted);margin:0 2px 6px}
.slide{position:relative;width:100%;container-type:inline-size;overflow:hidden;box-shadow:0 2px 10px rgba(0,0,0,.25);border-radius:3px}
.shape{overflow:hidden}
.shape .text{display:flex;flex-direction:column;width:100%;height:100%;overflow:hidden}
.para{line-height:1.2;word-wrap:break-word}
.bu{display:inline-block;margin-right:.35em}
.pic{object-fit:fill}
.ptable{width:100%;height:100%;border-collapse:collapse}
.ptable td{border:1px solid rgba(128,128,128,.55);padding:.3cqw .6cqw;vertical-align:top}
.placeholder-box{background:rgba(128,128,128,.12);border:1px dashed rgba(128,128,128,.6);color:#666}
details.notes{margin-top:8px;font-size:14px;color:var(--fg)}
details.notes summary{cursor:pointer;color:var(--accent)}
details.notes div{padding:8px 4px;color:var(--muted)}
"""
}
