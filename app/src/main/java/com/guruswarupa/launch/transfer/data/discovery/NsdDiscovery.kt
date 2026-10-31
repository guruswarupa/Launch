package com.guruswarupa.launch.transfer.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.Inet4Address
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A device found on the network. [tag] is an unauthenticated hint to tell same-named devices apart. */
data class Peer(val key: String, val name: String, val tag: String, val host: String, val port: Int)

/**
 * DNS-SD discovery (`_launchtx._tcp.`). Advertised data is untrusted: the real identity is verified
 * during pairing, never taken from the service record.
 */
class NsdDiscovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var lock: WifiManager.MulticastLock? = null

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var ownTag = ""
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    @Synchronized private fun acquireLock() {
        if (lock == null) lock = wifi?.createMulticastLock("launch-transfer")?.apply { setReferenceCounted(false); acquire() }
    }

    @Synchronized private fun releaseLockIfIdle() {
        if (registration == null && discovery == null) { lock?.release(); lock = null }
    }

    @Synchronized fun register(port: Int, deviceName: String, tag: String) {
        if (registration != null) return
        ownTag = tag
        acquireLock()
        val info = NsdServiceInfo().apply {
            serviceName = "Launch-$tag"
            serviceType = TYPE
            this.port = port
            setAttribute("n", deviceName.take(40))
            setAttribute("f", tag)
            setAttribute("v", "1")
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        registration = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { registration = null }
    }

    @Synchronized fun unregister() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
        releaseLockIfIdle()
    }

    @Synchronized fun startDiscovery(ownTag: String) {
        if (discovery != null) return
        this.ownTag = ownTag
        acquireLock()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, code: Int) { synchronized(this@NsdDiscovery) { discovery = null } }
            override fun onStopDiscoveryFailed(t: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType.trimEnd('.') == TYPE.trimEnd('.')) enqueue(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _peers.value = _peers.value.filterNot { it.key == info.serviceName }
            }
        }
        discovery = l
        runCatching { nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { discovery = null }
    }

    @Synchronized fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        queue.clear()
        _peers.value = emptyList()
        releaseLockIfIdle()
    }

    // NsdManager can resolve only one service at a time.
    @Synchronized private fun enqueue(info: NsdServiceInfo) {
        queue.addLast(info)
        resolveNext()
    }

    @Suppress("DEPRECATION")
    @Synchronized private fun resolveNext() {
        if (resolving) return
        val next = queue.removeFirstOrNull() ?: return
        resolving = true
        val done = { synchronized(this) { resolving = false; resolveNext() } }
        runCatching {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, code: Int) = done()
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val host = (i.host as? Inet4Address)?.hostAddress
                    val tag = i.attributes["f"]?.toString(Charsets.UTF_8)?.take(8) ?: ""
                    if (host != null && tag != ownTag) {
                        val name = i.attributes["n"]?.toString(Charsets.UTF_8)?.filter { !it.isISOControl() }?.take(40)?.ifBlank { null } ?: i.serviceName
                        val peer = Peer(i.serviceName, name, tag, host, i.port)
                        _peers.value = _peers.value.filterNot { it.key == peer.key } + peer
                    }
                    done()
                }
            })
        }.onFailure { done() }
    }

    companion object { const val TYPE = "_launchtx._tcp." }
}
