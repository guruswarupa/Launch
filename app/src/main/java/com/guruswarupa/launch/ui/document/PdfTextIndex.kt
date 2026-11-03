package com.guruswarupa.launch.ui.document

import android.content.Context
import android.graphics.RectF
import android.util.LruCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.util.Locale

class PdfTextIndex(context: Context, private val file: File) {

    class Match(val page: Int, val start: Int, val length: Int)

    class PageGeometry(val width: Float, val height: Float, val rects: FloatArray)

    private val appContext = context.applicationContext
    private var document: PDDocument? = null
    private val lock = Any()
    private var texts: Array<String?> = emptyArray()
    private val geometry = LruCache<Int, PageGeometry>(14)

    @Volatile var pageCount = 0
        private set
    @Volatile var indexed = 0
        private set
    @Volatile var finished = false
        private set
    @Volatile var totalChars = 0L
        private set
    @Volatile var failed = false
        private set

    private class Stripper : PDFTextStripper() {
        val sb = StringBuilder()
        val rects = ArrayList<Float>()

        private fun addEmpty() { rects.add(0f); rects.add(0f); rects.add(0f); rects.add(0f) }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            for (tp in textPositions) {
                val u = tp.unicode ?: continue
                val w = tp.widthDirAdj
                val h = tp.heightDir
                val left = tp.xDirAdj
                val top = tp.yDirAdj - h
                for (ch in u) {
                    sb.append(if (ch == '\n' || ch == '\r' || ch == ' ') ' ' else ch)
                    rects.add(left); rects.add(top); rects.add(left + w); rects.add(top + h * 1.2f)
                }
            }
        }

        override fun writeWordSeparator() { sb.append(' '); addEmpty() }
        override fun writeLineSeparator() { sb.append(' '); addEmpty() }
        override fun writeParagraphSeparator() { sb.append(' '); addEmpty() }
        override fun writePageStart() {}
        override fun writePageEnd() {}
        override fun writeParagraphStart() {}
        override fun writeParagraphEnd() {}
    }

    private fun open(): PDDocument? {
        document?.let { return it }
        return try {
            PDFBoxResourceLoader.init(appContext)
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).also { document = it }
        } catch (e: Throwable) {
            failed = true
            null
        }
    }

    private fun extract(index: Int): Pair<Stripper, PageGeometry>? {
        val doc = open() ?: return null
        val s = Stripper()
        s.sortByPosition = true
        s.startPage = index + 1
        s.endPage = index + 1
        s.getText(doc)
        val page = doc.getPage(index)
        val box = page.cropBox ?: page.mediaBox
        val rot = ((page.rotation % 360) + 360) % 360
        val w = if (rot == 90 || rot == 270) box.height else box.width
        val h = if (rot == 90 || rot == 270) box.width else box.height
        return s to PageGeometry(w, h, s.rects.toFloatArray())
    }

    fun buildIndex(cancelled: () -> Boolean, onProgress: (Int, Int) -> Unit) {
        synchronized(lock) {
            val doc = open() ?: run { finished = true; return }
            val n = doc.numberOfPages
            pageCount = n
            if (texts.size != n) texts = arrayOfNulls(n)
        }
        for (i in 0 until pageCount) {
            if (cancelled()) return
            if (texts[i] != null) continue
            synchronized(lock) {
                try {
                    val r = extract(i)
                    if (r != null) {
                        texts[i] = r.first.sb.toString().lowercase(Locale.ROOT)
                        totalChars += r.first.sb.length
                        geometry.put(i, r.second)
                    } else texts[i] = ""
                } catch (e: Throwable) {
                    texts[i] = ""
                }
            }
            indexed = i + 1
            if (i % 3 == 0 || i == pageCount - 1) onProgress(indexed, pageCount)
        }
        finished = true
        onProgress(pageCount, pageCount)
    }

    fun search(query: String, cancelled: () -> Boolean): List<Match> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Match>()
        for (i in 0 until indexed.coerceAtMost(texts.size)) {
            if (cancelled()) break
            val t = texts[i] ?: continue
            var from = 0
            while (true) {
                val at = t.indexOf(q, from)
                if (at < 0) break
                out.add(Match(i, at, q.length))
                from = at + q.length
                if (out.size > 5000) return out
            }
        }
        return out
    }

    fun geometry(page: Int): PageGeometry? {
        geometry.get(page)?.let { return it }
        synchronized(lock) {
            geometry.get(page)?.let { return it }
            return try {
                val r = extract(page) ?: return null
                geometry.put(page, r.second)
                r.second
            } catch (e: Throwable) { null }
        }
    }

    fun rectsFor(match: Match): List<RectF> {
        val g = geometry(match.page) ?: return emptyList()
        val out = ArrayList<RectF>()
        var cur: RectF? = null
        val end = (match.start + match.length).coerceAtMost(g.rects.size / 4)
        for (c in match.start until end) {
            val l = g.rects[c * 4]; val t = g.rects[c * 4 + 1]; val r = g.rects[c * 4 + 2]; val b = g.rects[c * 4 + 3]
            if (r <= l && b <= t) continue
            val nl = l / g.width; val nt = t / g.height; val nr = r / g.width; val nb = b / g.height
            val rect = cur
            if (rect != null && nt < rect.bottom && nb > rect.top && nl <= rect.right + 0.02f) {
                rect.left = minOf(rect.left, nl); rect.right = maxOf(rect.right, nr)
                rect.top = minOf(rect.top, nt); rect.bottom = maxOf(rect.bottom, nb)
            } else {
                cur = RectF(nl, nt, nr, nb)
                out.add(cur)
            }
        }
        return out
    }

    fun close() {
        synchronized(lock) {
            try { document?.close() } catch (_: Throwable) {}
            document = null
        }
    }
}
