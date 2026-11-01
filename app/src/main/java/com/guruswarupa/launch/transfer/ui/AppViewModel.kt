package com.guruswarupa.launch.transfer.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guruswarupa.launch.transfer.data.discovery.NsdDiscovery
import com.guruswarupa.launch.transfer.data.discovery.Peer
import com.guruswarupa.launch.transfer.data.protocol.ConnectionCode
import com.guruswarupa.launch.transfer.domain.HistoryStore
import com.guruswarupa.launch.transfer.domain.ReceiveController
import com.guruswarupa.launch.transfer.domain.SendController
import com.guruswarupa.launch.transfer.domain.SendState
import com.guruswarupa.launch.transfer.platform.AndroidReceiveStore
import com.guruswarupa.launch.transfer.data.protocol.FilenameSanitizer
import com.guruswarupa.launch.transfer.domain.SourceFile
import com.guruswarupa.launch.transfer.platform.DiskItem
import com.guruswarupa.launch.transfer.platform.DocItem
import com.guruswarupa.launch.transfer.platform.Item
import com.guruswarupa.launch.transfer.platform.Stats
import com.guruswarupa.launch.transfer.platform.TreeItem
import com.guruswarupa.launch.transfer.platform.isWithin
import com.guruswarupa.launch.transfer.platform.statsOf
import com.guruswarupa.launch.transfer.platform.walk
import android.provider.DocumentsContract
import com.guruswarupa.launch.transfer.platform.describe
import com.guruswarupa.launch.transfer.platform.localIpv4
import com.guruswarupa.launch.transfer.security.IdentityStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val identity = lazy { IdentityStore(File(app.filesDir, "transfer/identity")).loadOrCreate() }
    private val deviceName: String = (Build.MODEL ?: "Android device").take(40)
    private val resolver = app.contentResolver

    val history = HistoryStore(File(app.filesDir, "transfer/history.json"))
    val send = SendController(viewModelScope, identity, { deviceName }, history)
    val receive = ReceiveController(
        viewModelScope, identity, { deviceName }, AndroidReceiveStore(app), null, history, ::localIpv4,
    )
    val discovery = NsdDiscovery(app)

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    /** Everything the user has ticked; folders count as one item and are expanded when sending. */
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private val _stats = MutableStateFlow<Map<String, Stats>>(emptyMap())
    val stats: StateFlow<Map<String, Stats>> = _stats.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _wifi = MutableStateFlow(localIpv4() != null)
    val wifi: StateFlow<Boolean> = _wifi.asStateFlow()

    private val _receiving = MutableStateFlow(false)
    val receiving: StateFlow<Boolean> = _receiving.asStateFlow()

    private var monitor: Job? = null

    /** Polls the local-network state while the UI is visible. */
    fun startMonitoring() {
        if (monitor?.isActive == true) return
        monitor = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _wifi.value = localIpv4() != null
                receive.refreshAddress()
                delay(3_000)
            }
        }
    }

    fun stopMonitoring() { monitor?.cancel() }

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val picked = uris.mapNotNull { describe(resolver, it) }
            add(picked.map { DocItem(it) })
        }
    }

    fun addTree(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            val name = runCatching {
                resolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull() ?: "Folder"
            add(listOf(TreeItem(uri, FilenameSanitizer.sanitize(name))))
        }
    }

    private fun add(new: List<Item>) {
        var list = _items.value
        for (item in new) {
            if (list.any { it.id == item.id }) continue
            if (item is DiskItem) {
                if (list.any { it is DiskItem && isWithin(it.file, item.file) }) continue   // already covered
                list = list.filterNot { it is DiskItem && isWithin(item.file, it.file) }      // now covered by this one
            }
            list = list + item
            computeStats(item)
        }
        _items.value = list
    }

    private fun computeStats(item: Item) {
        if (item is DocItem) { _stats.value = _stats.value + (item.id to Stats(1, item.picked.size.coerceAtLeast(0))); return }
        _stats.value = _stats.value + (item.id to Stats(loading = true))
        viewModelScope.launch(Dispatchers.IO) {
            val st = runCatching { statsOf(item, resolver) }.getOrDefault(Stats())
            _stats.value = _stats.value + (item.id to st)
        }
    }

    fun removeItem(item: Item) {
        _items.value = _items.value - item
        _stats.value = _stats.value - item.id
    }

    fun clearItems() { _items.value = emptyList(); _stats.value = emptyMap() }

    fun dismissNotice() { _notice.value = null }

    fun startBrowsing() {
        viewModelScope.launch(Dispatchers.IO) { discovery.startDiscovery(identity.value.fingerprint.take(8)) }
    }

    fun stopBrowsing() = discovery.stopDiscovery()

    fun sendTo(peer: Peer) = startSend(SendController.Target(peer.host, peer.port))

    fun sendToCode(text: String) {
        val code = ConnectionCode.parse(text)
        if (code == null) { _notice.value = "That code isn't valid."; return }
        startSend(SendController.Target(code.host, code.port, code.fingerprint, code.secret))
    }

    private fun startSend(target: SendController.Target) {
        val items = _items.value
        if (items.isEmpty()) { _notice.value = "Choose files or folders first."; return }
        send.start({
            val out = ArrayList<SourceFile>()
            for (item in items) walk(item, resolver) { out += it }
            out
        }, target)
    }

    fun finishSend() {
        val wasDone = send.state.value is SendState.Done
        send.reset()
        if (wasDone) clearItems()
    }

    fun setReceiving(on: Boolean) {
        if (on == _receiving.value) return
        _receiving.value = on
        viewModelScope.launch(Dispatchers.IO) {
            if (on) {
                val port = receive.start()
                discovery.register(port, deviceName, identity.value.fingerprint.take(8))
            } else {
                receive.stop()
                discovery.unregister()
            }
        }
    }

    override fun onCleared() {
        send.cancel()
        receive.stop()
        discovery.unregister()
        discovery.stopDiscovery()
    }
}
