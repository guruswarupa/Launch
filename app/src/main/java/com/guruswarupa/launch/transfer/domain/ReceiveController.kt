package com.guruswarupa.launch.transfer.domain

import com.guruswarupa.launch.transfer.data.protocol.ConnectionCode
import com.guruswarupa.launch.transfer.data.protocol.FrameCodec
import com.guruswarupa.launch.transfer.data.protocol.Limits
import com.guruswarupa.launch.transfer.data.protocol.Message
import com.guruswarupa.launch.transfer.data.protocol.ProtocolException
import com.guruswarupa.launch.transfer.data.protocol.send
import com.guruswarupa.launch.transfer.data.transport.Decision
import com.guruswarupa.launch.transfer.data.transport.ReceiveOutcome
import com.guruswarupa.launch.transfer.data.transport.ReceiveStore
import com.guruswarupa.launch.transfer.data.transport.Timing
import com.guruswarupa.launch.transfer.data.transport.TransferReceiver
import com.guruswarupa.launch.transfer.security.Handshake
import com.guruswarupa.launch.transfer.security.Identity
import com.guruswarupa.launch.transfer.security.Tls
import com.guruswarupa.launch.transfer.security.randomBytes
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReceiveController(
    private val scope: CoroutineScope,
    private val identity: Lazy<Identity>,
    private val deviceName: () -> String,
    private val store: ReceiveStore,
    private val restoreStore: ReceiveStore?,
    private val history: HistoryRecorder?,
    private val localAddress: () -> String?,
    private val maxTotal: Long = Limits.MAX_TOTAL_BYTES,
) {
    private val _state = MutableStateFlow<ReceiveState>(ReceiveState.Off)
    val state: StateFlow<ReceiveState> = _state.asStateFlow()

    /** Current QR / typed connection code; rotates after every use. Null when not listening. */
    private val _code = MutableStateFlow<ConnectionCode?>(null)
    val code: StateFlow<ConnectionCode?> = _code.asStateFlow()

    @Volatile var port: Int = 0
        private set

    private var server: SSLServerSocket? = null
    private var acceptJob: Job? = null
    private val busy = AtomicBoolean(false)
    @Volatile private var activeSocket: SSLSocket? = null
    @Volatile private var decision: CompletableFuture<Decision>? = null
    @Volatile private var secret: ByteArray = randomBytes(16)
    @Volatile private var failedPairings = 0

    /** Starts listening on an ephemeral port and returns it. */
    fun start(): Int {
        if (server != null) return port
        val ctx = Tls.context(identity.value, null)
        val ss = ctx.serverSocketFactory.createServerSocket(0, 4) as SSLServerSocket
        ss.needClientAuth = true
        ss.enabledProtocols = ss.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
        server = ss
        port = ss.localPort
        rotateSecret()
        _state.value = ReceiveState.Listening
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(ss) }
        return port
    }

    fun stop() {
        decision?.complete(Decision(false))
        runCatching { activeSocket?.close() }
        runCatching { server?.close() }
        server = null
        port = 0
        _code.value = null
        _state.value = ReceiveState.Off
    }

    /** [restore]: put files back in their original folders instead of Downloads/Launch. */
    fun accept(restore: Boolean = false) { decision?.complete(Decision(true, restore)) }
    fun decline() { decision?.complete(Decision(false)) }

    /** Cancels an in-flight session from the receiving user's side. */
    fun cancelTransfer() {
        decision?.complete(Decision(false))
        activeSocket?.let { s ->
            runCatching { FrameCodec(s.inputStream, s.outputStream).send(Message.Cancel("-")) }
            runCatching { s.close() }
        }
        if (_state.value is ReceiveState.Receiving || _state.value is ReceiveState.Incoming) _state.value = ReceiveState.Listening
    }

    /** Dismisses a Done/Failed result and returns to listening. */
    fun acknowledge() {
        val s = _state.value
        if (s is ReceiveState.Done || s is ReceiveState.Failed) _state.value = ReceiveState.Listening
    }

    private fun rotateSecret() {
        secret = randomBytes(16)
        failedPairings = 0
        val ip = localAddress()
        _code.value = if (ip == null) null else ConnectionCode(ip, port, identity.value.fingerprint, secret)
    }

    /** Re-reads the local address (e.g. after a Wi-Fi change). */
    fun refreshAddress() {
        if (server != null) _code.value = localAddress()?.let { ConnectionCode(it, port, identity.value.fingerprint, secret) }
    }

    private fun acceptLoop(ss: SSLServerSocket) {
        while (!ss.isClosed) {
            val s = try { ss.accept() as SSLSocket } catch (e: IOException) { break }
            if (!busy.compareAndSet(false, true)) {
                scope.launch(Dispatchers.IO) { rejectBusy(s) }
                continue
            }
            scope.launch(Dispatchers.IO) {
                try { session(s) } finally { busy.set(false); activeSocket = null }
            }
        }
    }

    private fun rejectBusy(s: SSLSocket) {
        runCatching {
            s.soTimeout = 3_000
            s.startHandshake()
            FrameCodec(s.inputStream, s.outputStream).send(Message.Error("busy", "busy"))
        }
        runCatching { s.close() }
    }

    private fun session(s: SSLSocket) {
        activeSocket = s
        var visible = false
        try {
            s.soTimeout = Timing.HANDSHAKE
            Tls.harden(s)
            s.startHandshake()
            val codec = FrameCodec(s.inputStream, s.outputStream)
            val peerFp = Tls.peerFingerprint(s)
            val info = try {
                Handshake.receiver(codec, deviceName(), identity.value.fingerprint, peerFp, secret)
            } catch (e: ProtocolException) {
                if (e.code == "pair_failed" && ++failedPairings >= 3) rotateSecret()
                throw e
            }
            if (info.usedSecret) rotateSecret()

            val started = System.nanoTime()
            val outcome = TransferReceiver.receive(codec, store, restoreStore, { s.soTimeout = it }, maxTotal, decide = { p ->
                visible = true
                val future = CompletableFuture<Decision>().also { decision = it }
                _state.value = ReceiveState.Incoming(info.device, p.preview, p.count, p.folders, p.total, p.hasOriginalPaths && restoreStore != null)
                val answer = try { future.get(Timing.CONSENT_MS, TimeUnit.MILLISECONDS) } catch (e: TimeoutException) { Decision(false) }
                decision = null
                answer
            }) { p -> _state.value = ReceiveState.Receiving(info.device, p, speed(p.doneBytes, started)) }

            _state.value = when (outcome) {
                is ReceiveOutcome.Completed -> {
                    history?.record(HistoryEntry(
                        id = com.guruswarupa.launch.transfer.security.randomHex(8), time = System.currentTimeMillis(), incoming = true, peer = info.device,
                        files = outcome.saved, skipped = outcome.skipped, bytes = outcome.totalBytes,
                        list = outcome.records.map { HistoryFile(it.name, it.dir, it.size, it.ref) },
                        notListed = outcome.saved - outcome.records.size, restored = outcome.restored,
                    ))
                    ReceiveState.Done(info.device, outcome.saved, outcome.skipped, outcome.totalBytes, outcome.restored)
                }
                ReceiveOutcome.Declined -> ReceiveState.Listening
                ReceiveOutcome.CancelledByPeer -> ReceiveState.Failed(Errors.describe("cancelled"))
            }
        } catch (t: Throwable) {
            if (t is ProtocolException && !t.fromPeer) {
                runCatching { FrameCodec(s.inputStream, s.outputStream).send(Message.Error(t.code, "error")) }
            }
            // Connections that never reached the consent screen (port scans, failed pairing) stay invisible.
            if (visible && server != null) {
                _state.value = if (_state.value == ReceiveState.Listening) ReceiveState.Listening
                else ReceiveState.Failed(Errors.describe(t))
            }
        } finally {
            runCatching { s.close() }
        }
    }
}
