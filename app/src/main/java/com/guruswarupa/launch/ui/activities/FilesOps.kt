package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object FilesOps {

    val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")
    val videoExt = setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v", "ts")
    val audioExt = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr")
    val docExt = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "txt", "csv", "md", "json", "xml", "html", "log")
    val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

    fun ext(f: File) = f.extension.lowercase(Locale.ROOT)

    fun mime(f: File): String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(f)) ?: "application/octet-stream"

    fun formatSize(b: Long): String {
        if (b < 1024) return "$b B"
        val u = arrayOf("KB", "MB", "GB", "TB")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
        return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, u[i])
    }

    fun typeName(f: File): String {
        if (f.isDirectory) return "Folder"
        val e = ext(f)
        return when {
            e in imageExt -> "Image"
            e in videoExt -> "Video"
            e in audioExt -> "Audio"
            e in docExt -> "Document"
            e == "apk" -> "Android package"
            e in archiveExt -> "Archive"
            e.isEmpty() -> "File"
            else -> e.uppercase(Locale.ROOT) + " file"
        }
    }

    fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var n = 1
        while (target.exists()) { target = File(dir, "$base ($n)$ext"); n++ }
        return target
    }

    fun countFiles(files: List<File>): Int {
        var n = 0
        fun walk(f: File) { if (f.isDirectory) { n++; f.listFiles()?.forEach { walk(it) } } else n++ }
        files.forEach { walk(it) }
        return n
    }

    fun sizeOf(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    class Progress(private val handle: ProgressHandle, val total: Int) {
        private var done = 0
        val cancelled get() = handle.cancelled
        fun step(label: String) { done++; handle.update(done, total, label) }
    }

    fun copyTree(src: File, dst: File, p: Progress) {
        if (p.cancelled) return
        if (src.isDirectory) {
            dst.mkdirs()
            p.step(src.name)
            src.listFiles()?.forEach { copyTree(it, File(dst, it.name), p) }
        } else {
            p.step(src.name)
            src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
            dst.setLastModified(src.lastModified())
        }
    }

    fun deleteTree(f: File, p: Progress) {
        if (p.cancelled) return
        if (f.isDirectory) f.listFiles()?.forEach { deleteTree(it, p) }
        p.step(f.name)
        f.delete()
    }

    fun zip(files: List<File>, out: File, p: Progress) {
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            fun add(f: File, path: String) {
                if (p.cancelled) return
                if (f.isDirectory) {
                    p.step(f.name)
                    z.putNextEntry(ZipEntry("$path/")); z.closeEntry()
                    f.listFiles()?.forEach { add(it, "$path/${it.name}") }
                } else {
                    p.step(f.name)
                    z.putNextEntry(ZipEntry(path).apply { time = f.lastModified() })
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
            files.forEach { add(it, it.name) }
        }
    }

    fun unzip(zip: File, dest: File, p: Progress) {
        dest.mkdirs()
        val destPath = dest.canonicalPath
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            var e = z.nextEntry
            while (e != null && !p.cancelled) {
                val out = File(dest, e.name)
                if (out.canonicalPath.startsWith(destPath + File.separator)) {
                    p.step(out.name)
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { z.copyTo(it) }
                    }
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
    }

    fun zipEntryCount(zip: File): Int = try {
        java.util.zip.ZipFile(zip).use { it.size() }
    } catch (e: Exception) { 0 }

    fun search(root: File, query: String, limit: Int, cancelled: () -> Boolean): List<File> {
        val out = ArrayList<File>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty() && out.size < limit && !cancelled()) {
            val d = stack.removeLast()
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                if (k.name.startsWith(".")) continue
                if (k.name.contains(query, true)) out.add(k)
                if (k.isDirectory) stack.add(k)
            }
        }
        return out
    }

    fun scanCategory(root: File, key: String, cancelled: () -> Boolean): List<File> {
        val out = ArrayList<File>()
        fun match(f: File): Boolean {
            val e = ext(f)
            return when (key) {
                "images" -> e in imageExt
                "videos" -> e in videoExt
                "audio" -> e in audioExt
                "documents" -> e in docExt
                "apks" -> e == "apk"
                "archives" -> e in archiveExt
                else -> true
            }
        }
        val start = if (key == "downloads") File(root, "Download") else root
        val androidDir = File(root, "Android").absolutePath
        val stack = ArrayDeque<File>()
        stack.add(start)
        while (stack.isNotEmpty() && out.size < 6000 && !cancelled()) {
            val d = stack.removeLast()
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                if (k.name.startsWith(".")) continue
                if (k.isDirectory) {
                    if (k.absolutePath != androidDir) stack.add(k)
                } else if (match(k)) out.add(k)
            }
        }
        out.sortByDescending { it.lastModified() }
        return if (key == "recent") out.take(150) else out
    }
}

internal class FilesThumbs(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 10).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun hasThumb(f: File): Boolean {
        if (!f.isFile) return false
        val e = FilesOps.ext(f)
        return (e in FilesOps.imageExt && e != "svg") || e in FilesOps.videoExt || e == "apk"
    }

    fun load(f: File, target: ImageView, px: Int, onDone: (Boolean) -> Unit) {
        val key = f.absolutePath + "|" + f.lastModified()
        target.tag = key
        cache.get(key)?.let { target.setImageBitmap(it); onDone(true); return }
        onDone(false)
        pool.execute {
            val bmp = decode(f, px)
            if (bmp != null) cache.put(key, bmp)
            main.post {
                if (target.tag == key && bmp != null) { target.setImageBitmap(bmp); onDone(true) }
            }
        }
    }

    private fun decode(f: File, px: Int): Bitmap? = try {
        val e = FilesOps.ext(f)
        when {
            e in FilesOps.imageExt -> {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, o)
                var s = 1
                while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
            }
            e in FilesOps.videoExt -> {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(f.absolutePath)
                    r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { Bitmap.createScaledBitmap(it, px, (px * it.height / it.width.toFloat()).toInt().coerceAtLeast(1), true) }
                } finally { r.release() }
            }
            e == "apk" -> {
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(f.absolutePath, 0)
                val app = info?.applicationInfo
                if (app != null) {
                    app.sourceDir = f.absolutePath
                    app.publicSourceDir = f.absolutePath
                    app.loadIcon(pm).toBitmap(px, px)
                } else null
            }
            else -> null
        }
    } catch (t: Throwable) { null }
}

internal object FilesTrash {
    const val RETENTION_DAYS = 30
    private const val DAY_MS = 24L * 60 * 60 * 1000

    class Item(val folder: File, val data: File, val original: File, val deletedAt: Long) {
        val daysLeft: Int get() = (RETENTION_DAYS - ((System.currentTimeMillis() - deletedAt) / DAY_MS)).toInt().coerceAtLeast(0)
    }

    fun dir(root: File) = File(root, ".LaunchTrash")

    fun moveToTrash(root: File, f: File): Boolean {
        val folder = File(dir(root), "${System.currentTimeMillis()}_${(1000..9999).random()}")
        if (!folder.mkdirs()) return false
        File(dir(root), ".nomedia").let { if (!it.exists()) runCatching { it.createNewFile() } }
        val target = File(folder, f.name)
        File(folder, ".meta").writeText(f.absolutePath + "\n" + System.currentTimeMillis())
        if (f.renameTo(target)) return true
        return try {
            if (f.isDirectory) f.copyRecursively(target) else f.copyTo(target)
            f.deleteRecursively()
            true
        } catch (e: Exception) {
            folder.deleteRecursively()
            false
        }
    }

    fun items(root: File): List<Item> {
        val kids = dir(root).listFiles() ?: return emptyList()
        val out = ArrayList<Item>()
        for (folder in kids) {
            if (!folder.isDirectory) continue
            val meta = File(folder, ".meta")
            val data = folder.listFiles()?.firstOrNull { it.name != ".meta" }
            if (data == null) { folder.deleteRecursively(); continue }
            val lines = runCatching { meta.readLines() }.getOrDefault(emptyList())
            val original = File(lines.getOrNull(0) ?: File(root, data.name).absolutePath)
            val at = lines.getOrNull(1)?.toLongOrNull() ?: folder.lastModified()
            out.add(Item(folder, data, original, at))
        }
        return out.sortedByDescending { it.deletedAt }
    }

    fun restore(root: File, item: Item): Boolean {
        val parent = item.original.parentFile?.takeIf { it.absolutePath.startsWith(root.absolutePath) } ?: File(root, "Restored")
        parent.mkdirs()
        val target = FilesOps.uniqueTarget(parent, item.data.name)
        val ok = item.data.renameTo(target) || runCatching {
            if (item.data.isDirectory) item.data.copyRecursively(target) else item.data.copyTo(target)
            item.data.deleteRecursively()
            true
        }.getOrDefault(false)
        if (ok) item.folder.deleteRecursively()
        return ok
    }

    fun purgeExpired(root: File): Int {
        val limit = System.currentTimeMillis() - RETENTION_DAYS * DAY_MS
        var n = 0
        items(root).filter { it.deletedAt < limit }.forEach { it.folder.deleteRecursively(); n++ }
        return n
    }

    fun empty(root: File) { dir(root).listFiles()?.forEach { if (it.isDirectory) it.deleteRecursively() } }
}
