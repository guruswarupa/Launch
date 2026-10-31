package com.guruswarupa.launch.transfer.domain

import com.guruswarupa.launch.transfer.data.protocol.FrameCodec
import com.guruswarupa.launch.transfer.data.protocol.Message
import com.guruswarupa.launch.transfer.data.protocol.ProtocolException
import com.guruswarupa.launch.transfer.data.protocol.send
import com.guruswarupa.launch.transfer.data.transport.OutgoingFile
import com.guruswarupa.launch.transfer.data.transport.SendOutcome
import com.guruswarupa.launch.transfer.data.transport.Timing
import com.guruswarupa.launch.transfer.data.transport.TransferSender
import com.guruswarupa.launch.transfer.security.Handshake
import com.guruswarupa.launch.transfer.security.Identity
import com.guruswarupa.launch.transfer.security.Tls
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SendController(
    private val scope: CoroutineScope,
    private val identity: Lazy<Identity>,
    private val deviceName: () -> String,
    private val history: HistoryRecorder? = null,
) {
    class Target(val host: String, val port: Int, val pinnedFingerprint: String? = null, val secret: ByteArray? = null)

    private val _state = MutableStateFlow<SendState>(SendState.Idle)
    val state: StateFlow<SendState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var userCancelled = false
    private var last: Pair<suspend () -> List<SourceFile>, Target>? = null

    val isActive: Boolean get() = job?.isActive == true

    fun start(files: List<SourceFile>, target: Target) = start({ files }, target)

    /** [files] is evaluated on a background thread, so large folders can be enumerated lazily. */
    fun start(files: suspend () -> List<SourceFile>, target: Target) {
        if (isActive) return
        last = files to target
        userCancelled = false
        job = scope.launch(Dispatchers.IO) { run(files, target) }
    }

    fun retry() { last?.let { (f, t) -> start(f, t) } }

    fun cancel() {
        userCancelled = true
        runCatching {
            socket?.let { s ->
                runCatching { FrameCodec(s.inputStream, s.outputStream).send(Message.Cancel("-")) }
                s.close()
            }
        }
        job?.cancel()
        if (_state.value !is SendState.Done) _state.value = SendState.Cancelled
    }

    /** Returns to the idle screen after a finished/failed attempt. */
    fun reset() { if (!isActive) _state.value = SendState.Idle }

    private suspend fun run(source: suspend () -> List<SourceFile>, target: Target) {
        var peer = ""
        try {
            _state.value = SendState.Preparing
            val sources = source()
            val outgoing = sources.map { OutgoingFile(it.name, it.dir, it.size, it.origin, it.open) }
            if (outgoing.isEmpty()) throw ProtocolException("empty", "nothing to send")
            _state.value = SendState.Connecting
            val ctx = Tls.context(identity.value, target.pinnedFingerprint)
            val s = ctx.socketFactory.createSocket() as SSLSocket
            socket = s
            s.connect(InetSocketAddress(target.host, target.port), Timing.CONNECT)
            s.soTimeout = Timing.HANDSHAKE
            Tls.harden(s)
            s.startHandshake()
            val codec = FrameCodec(s.inputStream, s.outputStream)
            val info = Handshake.sender(codec, deviceName(), identity.value.fingerprint, Tls.peerFingerprint(s), target.secret)
            peer = info.device
            _state.value = SendState.WaitingForReceiver(peer)
            val started = System.nanoTime()
            val outcome = TransferSender.send(codec, outgoing, { s.soTimeout = it }) { p ->
                _state.value = SendState.Transferring(peer, p, speed(p.doneBytes, started))
            }
            _state.value = when (outcome) {
                is SendOutcome.Completed -> {
                    record(peer, sources, outcome)
                    SendState.Done(peer, outgoing.size - outcome.skipped, outcome.skipped, outgoing.sumOf { it.size })
                }
                is SendOutcome.Rejected ->
                    if (outcome.reason == "declined") SendState.Declined(peer) else SendState.Failed(Errors.describe(outcome.reason))
                SendOutcome.CancelledByPeer -> SendState.Failed(Errors.describe("cancelled"))
            }
        } catch (t: Throwable) {
            if (userCancelled || t is kotlinx.coroutines.CancellationException) {
                _state.value = SendState.Cancelled
            } else {
                if (t is ProtocolException && !t.fromPeer) {
                    runCatching { socket?.let { FrameCodec(it.inputStream, it.outputStream).send(Message.Error(t.code, "error")) } }
                }
                _state.value = SendState.Failed(Errors.describe(t))
            }
        } finally {
            runCatching { socket?.close() }
            socket = null
        }
    }

    private fun record(peer: String, sources: List<SourceFile>, outcome: SendOutcome.Completed) {
        val listed = sources.take(HistoryStore.MAX_FILES_LISTED).mapIndexed { i, f ->
            HistoryFile(f.name, f.dir, f.size, f.ref, skipped = i in outcome.skippedIndexes)
        }
        history?.record(HistoryEntry(
            id = com.guruswarupa.launch.transfer.security.randomHex(8), time = System.currentTimeMillis(), incoming = false, peer = peer,
            files = sources.size - outcome.skipped, skipped = outcome.skipped, bytes = sources.sumOf { it.size },
            list = listed, notListed = sources.size - listed.size,
        ))
    }
}
