package com.guruswarupa.launch.transfer.platform

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import com.guruswarupa.launch.transfer.domain.SourceFile
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.file.Files
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One thing the user ticked: a document, a folder from the system picker, or a path on shared storage. */
sealed interface Item {
    val id: String
    val label: String
    val isFolder: Boolean
}

data class DiskItem(val file: File) : Item {
    override val id get() = "d:" + file.path
    override val label: String get() = file.name.ifEmpty { "Phone storage" }
    override val isFolder get() = file.isDirectory
}

data class DocItem(val picked: PickedFile) : Item {
    override val id get() = "u:" + picked.uri
    override val label get() = picked.name
    override val isFolder get() = false
}

data class TreeItem(val uri: Uri, val name: String) : Item {
    override val id get() = "t:$uri"
    override val label get() = name
    override val isFolder get() = true
}

data class Stats(val files: Int = 0, val bytes: Long = 0, val loading: Boolean = false) {
    operator fun plus(o: Stats) = Stats(files + o.files, bytes + o.bytes, loading || o.loading)
}

fun interface Visitor { fun file(f: SourceFile) }

fun storageRoot(): File = Environment.getExternalStorageDirectory()

/** Visits every regular file under [item], with its folder path relative to the selection (root folder name included). */
suspend fun walk(item: Item, resolver: ContentResolver, visit: Visitor) {
    when (item) {
        is DocItem -> visit.file(SourceFile(item.picked.name, item.picked.size, ref = item.picked.uri.toString()) {
            resolver.openInputStream(item.picked.uri) ?: throw FileNotFoundException("cannot open file")
        })
        is DiskItem -> walkDisk(item.file, "", visit, top = true)
        is TreeItem -> {
            val rootId = DocumentsContract.getTreeDocumentId(item.uri)
            // Primary-storage trees carry their real path in the document id ("primary:DCIM/Camera").
            val origin = if (rootId.startsWith("primary:")) rootId.removePrefix("primary:").trim('/') else null
            walkTree(resolver, item.uri, rootId, item.name, origin, visit)
        }
    }
}

private suspend fun walkDisk(entry: File, parentDir: String, visit: Visitor, top: Boolean) {
    currentCoroutineContext().ensureActive()
    if (Files.isSymbolicLink(entry.toPath())) return
    if (entry.isFile) {
        val origin = entry.parentFile?.takeIf { isWithin(storageRoot(), it) }?.path?.removePrefix(storageRoot().path)?.trim('/')
        visit.file(SourceFile(entry.name, entry.length(), parentDir, origin, entry.path) { FileInputStream(entry) })
        return
    }
    val children = entry.listFiles() ?: return
    // A selected storage root contributes its children directly rather than a folder named after the root.
    val dir = if (top && entry == storageRoot()) "" else if (parentDir.isEmpty()) entry.name else "$parentDir/${entry.name}"
    for (c in children) walkDisk(c, dir, visit, top = false)
}

private suspend fun walkTree(resolver: ContentResolver, tree: Uri, docId: String, dir: String, origin: String?, visit: Visitor) {
    currentCoroutineContext().ensureActive()
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
    val subdirs = ArrayList<Pair<String, String>>()
    val cols = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
    )
    try {
        resolver.query(childrenUri, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    subdirs += id to name
                } else {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    val size = if (c.isNull(3)) -1L else c.getLong(3)
                    visit.file(SourceFile(name, size.coerceAtLeast(0), dir, origin, docUri.toString()) {
                        resolver.openInputStream(docUri) ?: throw FileNotFoundException("cannot open file")
                    })
                }
            }
        }
    } catch (e: Exception) { return }
    for ((id, name) in subdirs) {
        walkTree(resolver, tree, id, "$dir/$name", origin?.let { if (it.isEmpty()) name else "$it/$name" }, visit)
    }
}

suspend fun statsOf(item: Item, resolver: ContentResolver): Stats {
    var files = 0
    var bytes = 0L
    walk(item, resolver) { files++; bytes += it.size }
    return Stats(files, bytes)
}

/** True if [ancestor] is [file] or one of its parents. */
fun isWithin(ancestor: File, file: File): Boolean {
    var f: File? = file
    while (f != null) { if (f == ancestor) return true; f = f.parentFile }
    return false
}
