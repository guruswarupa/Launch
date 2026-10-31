package com.guruswarupa.launch.transfer.domain

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** [ref] is a content:// URI or absolute path used to open the file; null if it can't be reopened. */
@Serializable
data class HistoryFile(val name: String, val dir: String, val size: Long, val ref: String? = null, val skipped: Boolean = false)

@Serializable
data class HistoryEntry(
    val id: String,
    val time: Long,
    val incoming: Boolean,
    val peer: String,
    val files: Int,
    val skipped: Int,
    val bytes: Long,
    val list: List<HistoryFile>,
    /** Files that happened in the transfer but are not in [list] (list is capped). */
    val notListed: Int = 0,
    val restored: Boolean = false,
)

fun interface HistoryRecorder { fun record(entry: HistoryEntry) }

/** Transfer history, stored only on this device. Shows what was sent/received; never leaves the phone. */
class HistoryStore(private val file: File) : HistoryRecorder {
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private fun load(): List<HistoryEntry> = runCatching {
        json.decodeFromString(ListSerializer(HistoryEntry.serializer()), file.readText())
    }.getOrDefault(emptyList())

    @Synchronized private fun save(list: List<HistoryEntry>) {
        _entries.value = list
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(HistoryEntry.serializer()), list))
            tmp.renameTo(file)
        }
    }

    @Synchronized override fun record(entry: HistoryEntry) = save((listOf(entry) + _entries.value).take(MAX_ENTRIES))
    @Synchronized fun delete(id: String) = save(_entries.value.filterNot { it.id == id })
    @Synchronized fun clear() = save(emptyList())

    companion object {
        const val MAX_ENTRIES = 50
        const val MAX_FILES_LISTED = 2000
    }
}
