package com.guruswarupa.launch.transfer.platform

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.guruswarupa.launch.transfer.data.protocol.FilenameSanitizer
import com.guruswarupa.launch.transfer.data.transport.ReceiveStore
import com.guruswarupa.launch.transfer.data.transport.Saved
import com.guruswarupa.launch.transfer.data.transport.StagedFile
import com.guruswarupa.launch.transfer.data.transport.uniqueName
import com.guruswarupa.launch.transfer.security.randomHex
import java.io.File
import java.io.IOException
import java.io.OutputStream

class PickedFile(val uri: Uri, val name: String, val size: Long)

/** Reads display name and size of a document the user picked with the system picker. */
fun describe(resolver: ContentResolver, uri: Uri): PickedFile? = try {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (!c.moveToFirst()) return null
        val name = c.getString(0)?.let(FilenameSanitizer::sanitize) ?: "file"
        val size = if (c.isNull(1)) -1L else c.getLong(1)
        PickedFile(uri, name, size)
    }
} catch (e: Exception) { null }

/**
 * Saves received files to Downloads/Launch. On Android 10+ via MediaStore (no storage permission needed,
 * item stays pending/invisible until verified); on older versions in the app's own external Downloads folder.
 */
class AndroidReceiveStore(private val context: Context) : ReceiveStore {
    override fun freeBytes(): Long = try {
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    } catch (e: Exception) { 0L }

    override fun stage(dir: List<String>, displayName: String): StagedFile =
        if (Build.VERSION.SDK_INT >= 29) MediaStoreStaged(context.contentResolver, dir, displayName) else LegacyStaged(context, dir, displayName)

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    private inner class MediaStoreStaged(private val resolver: ContentResolver, dir: List<String>, wanted: String) : StagedFile {
        private val relativePath = (listOf(Environment.DIRECTORY_DOWNLOADS, "Launch") + dir).joinToString("/") + "/"
        private val name: String
        private val uri: Uri
        override val out: OutputStream

        init {
            name = uniqueName(wanted, ::exists)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("cannot create file")
            out = try {
                (resolver.openOutputStream(uri) ?: throw IOException("cannot open file")).buffered(64 * 1024)
            } catch (e: IOException) {
                resolver.delete(uri, null, null)
                throw e
            }
        }

        private fun exists(candidate: String): Boolean =
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(candidate, relativePath), null,
            )?.use { it.count > 0 } ?: false

        override fun commit(): Saved {
            out.close()
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return Saved(relativePath.replaceFirst("Download/", "Downloads/") + name, uri.toString())
        }

        override fun abort() {
            runCatching { out.close() }
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    private class LegacyStaged(context: Context, sub: List<String>, wanted: String) : StagedFile {
        private val dir = sub.fold(File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Launch")) { f, s -> File(f, s) }.apply { mkdirs() }
        private val temp = File(dir, ".launch-${randomHex(8)}.part")
        private val final = File(dir, uniqueName(wanted) { File(dir, it).exists() })
        override val out: OutputStream = temp.outputStream().buffered(64 * 1024)

        override fun commit(): Saved {
            out.close()
            // Temp file is renamed only after verification; never overwrites an existing file.
            if (final.exists() || !temp.renameTo(final)) { temp.delete(); throw IOException("cannot save file") }
            return Saved(final.path, final.path)
        }

        override fun abort() {
            runCatching { out.close() }
            temp.delete()
        }
    }
}
