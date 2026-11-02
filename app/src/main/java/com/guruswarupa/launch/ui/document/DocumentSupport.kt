package com.guruswarupa.launch.ui.document

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

internal object Html {
    fun esc(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (c in text) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                '\u0000' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun page(title: String, body: String, extraCss: String = "", bodyClass: String = ""): String = """
<!DOCTYPE html>
<html data-theme="light" data-wrap="off">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=6.0">
<title>${esc(title)}</title>
<style>
:root{--bg:#ffffff;--fg:#1b1b1b;--muted:#6b6b6b;--border:#d9d9d9;--link:#0b57d0;--code-bg:#f5f5f7;--head-bg:#f0f1f3;--accent:#0b57d0;}
html[data-theme="dark"]{--bg:#121212;--fg:#e6e6e6;--muted:#9a9a9a;--border:#333333;--link:#8ab4f8;--code-bg:#1d1d1f;--head-bg:#232326;--accent:#8ab4f8;}
html[data-theme="sepia"]{--bg:#f4ecd8;--fg:#3b2f1e;--muted:#7a6a52;--border:#d8c9a8;--link:#8a4b08;--code-bg:#ebe1c8;--head-bg:#e8dcc0;--accent:#8a4b08;}
*{box-sizing:border-box}
html,body{margin:0;padding:0}
body{background:var(--bg);color:var(--fg);font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:16px;line-height:1.6;-webkit-text-size-adjust:100%;word-wrap:break-word;overflow-wrap:anywhere}
a{color:var(--link)}
img{max-width:100%;height:auto}
.doc{max-width:900px;margin:0 auto;padding:22px 18px 120px}
table{border-collapse:collapse}
$extraCss
</style>
</head>
<body class="$bodyClass">
$body
</body>
</html>
""".trimIndent()
}

internal class XmlNode(val name: String, val attrs: Map<String, String>) {
    val children = ArrayList<XmlNode>()
    val text = StringBuilder()
    var mixed: ArrayList<Any>? = null

    fun attr(key: String): String? = attrs[key]
    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }
    fun childrenNamed(name: String): List<XmlNode> = children.filter { it.name == name }

    fun find(name: String): XmlNode? {
        for (c in children) {
            if (c.name == name) return c
            c.find(name)?.let { return it }
        }
        return null
    }

    fun findAll(name: String, out: MutableList<XmlNode> = ArrayList()): List<XmlNode> {
        for (c in children) {
            if (c.name == name) out.add(c)
            c.findAll(name, out)
        }
        return out
    }

    fun allText(): String {
        val sb = StringBuilder()
        fun walk(n: XmlNode) {
            sb.append(n.text)
            n.children.forEach { walk(it) }
        }
        walk(this)
        return sb.toString()
    }
}

internal fun localName(name: String): String = name.substringAfter(':')

internal fun parseXml(input: InputStream, maxNodes: Int = 4_000_000, keepMixed: Boolean = false): XmlNode? {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(input, null)
    val stack = ArrayList<XmlNode>()
    var root: XmlNode? = null
    var count = 0
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                if (++count > maxNodes) return root
                val attrs = HashMap<String, String>(parser.attributeCount)
                for (i in 0 until parser.attributeCount) {
                    val full = parser.getAttributeName(i)
                    val value = parser.getAttributeValue(i)
                    attrs[full] = value
                    attrs.putIfAbsent(localName(full), value)
                }
                val node = XmlNode(localName(parser.name), attrs)
                if (keepMixed) node.mixed = ArrayList()
                if (stack.isEmpty()) root = node else {
                    stack.last().children.add(node)
                    stack.last().mixed?.add(node)
                }
                stack.add(node)
            }
            XmlPullParser.TEXT -> stack.lastOrNull()?.let {
                it.text.append(parser.text)
                it.mixed?.add(parser.text)
            }
            XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        }
        event = parser.next()
    }
    return root
}

internal class Relationship(val id: String, val target: String, val external: Boolean, val type: String)

internal class ZipPackage(file: File) : Closeable {
    private val zip = ZipFile(file)
    private var imageBudget = 40L * 1024 * 1024

    fun exists(name: String): Boolean = zip.getEntry(name) != null

    fun names(): List<String> = zip.entries().asSequence().map { it.name }.toList()

    fun stream(name: String): InputStream? = zip.getEntry(name)?.let { zip.getInputStream(it) }

    fun bytes(name: String, limit: Long = 64L * 1024 * 1024): ByteArray? {
        val entry = zip.getEntry(name) ?: return null
        if (entry.size > limit) return null
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    fun xml(name: String, keepMixed: Boolean = false): XmlNode? {
        val entry = zip.getEntry(name) ?: return null
        if (entry.size > 256L * 1024 * 1024) return null
        return zip.getInputStream(entry).use { parseXml(it, keepMixed = keepMixed) }
    }

    fun relationships(relsPath: String, baseDir: String): Map<String, Relationship> {
        val root = xml(relsPath) ?: return emptyMap()
        val map = LinkedHashMap<String, Relationship>()
        for (rel in root.childrenNamed("Relationship")) {
            val id = rel.attr("Id") ?: continue
            val target = rel.attr("Target") ?: continue
            val external = rel.attr("TargetMode").equals("External", ignoreCase = true)
            map[id] = Relationship(id, if (external) target else resolve(baseDir, target), external, rel.attr("Type").orEmpty())
        }
        return map
    }

    fun imageDataUri(path: String?): String? {
        if (path == null) return null
        val ext = path.substringAfterLast('.', "").lowercase()
        val mime = when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            else -> return null
        }
        val data = bytes(path, 8L * 1024 * 1024) ?: return null
        if (data.size > imageBudget) return null
        imageBudget -= data.size
        return "data:$mime;base64," + Base64.encodeToString(data, Base64.NO_WRAP)
    }

    override fun close() {
        zip.close()
    }

    companion object {
        fun resolve(baseDir: String, target: String): String {
            if (target.startsWith("/")) return target.removePrefix("/")
            val parts = ArrayList<String>()
            (baseDir + target).split('/').forEach { part ->
                when (part) {
                    "", "." -> {}
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                    else -> parts.add(part)
                }
            }
            return parts.joinToString("/")
        }
    }
}

internal fun safeCssColor(value: String?): String? {
    if (value == null) return null
    val v = value.trim()
    if (Regex("^[0-9a-fA-F]{6}$").matches(v)) return "#$v"
    if (Regex("^[0-9a-fA-F]{8}$").matches(v)) return "#" + v.substring(2)
    return null
}

internal fun emuToPx(emu: Long): Int = (emu / 9525L).toInt()

internal fun romanNumeral(number: Int, upper: Boolean): String {
    if (number <= 0 || number >= 4000) return number.toString()
    val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    var n = number
    val sb = StringBuilder()
    for (i in values.indices) {
        while (n >= values[i]) {
            sb.append(symbols[i])
            n -= values[i]
        }
    }
    return if (upper) sb.toString() else sb.toString().lowercase()
}

internal fun alphaNumeral(number: Int, upper: Boolean): String {
    if (number <= 0) return number.toString()
    val sb = StringBuilder()
    var n = number
    while (n > 0) {
        n--
        sb.append(('a' + n % 26))
        n /= 26
    }
    val s = sb.reverse().toString()
    return if (upper) s.uppercase() else s
}
