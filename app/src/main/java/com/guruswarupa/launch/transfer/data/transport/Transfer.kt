package com.guruswarupa.launch.transfer.data.transport

import com.guruswarupa.launch.transfer.data.protocol.FileMeta
import com.guruswarupa.launch.transfer.data.protocol.FilenameSanitizer
import com.guruswarupa.launch.transfer.data.protocol.FrameCodec
import com.guruswarupa.launch.transfer.data.protocol.FrameKind
import com.guruswarupa.launch.transfer.data.protocol.Message
import com.guruswarupa.launch.transfer.data.protocol.DirPath
import com.guruswarupa.launch.transfer.data.protocol.Limits
import com.guruswarupa.launch.transfer.data.protocol.MessageCodec
import com.guruswarupa.launch.transfer.data.protocol.ProposalValidator
import com.guruswarupa.launch.transfer.data.protocol.ProtocolException
import com.guruswarupa.launch.transfer.data.protocol.expect
import com.guruswarupa.launch.transfer.data.protocol.receiveMessage
import com.guruswarupa.launch.transfer.data.protocol.send
import com.guruswarupa.launch.transfer.security.randomHex
import com.guruswarupa.launch.transfer.security.toHex
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

fun interface Timeouts { fun set(millis: Int) }

object Timing {
    const val CONNECT = 10_000
    const val HANDSHAKE = 10_000
    const val IDLE = 30_000
    /** Receiver: how long to wait for the sender to propose after pairing (sender user may be comparing codes). */
    const val PROPOSE_WAIT = 120_000
    /** Receiver user must decide within this long. */
    const val CONSENT_MS = 120_000L
    /** Sender: waits for the receiver's decision; longer than [CONSENT_MS]. */
    const val SENDER_CONSENT_WAIT = 150_000
}

const val CHUNK_SIZE = 64 * 1024

class OutgoingFile(val name: String, val dir: String, val size: Long, val origin: String?, val open: () -> InputStream)

class Progress(
    val fileIndex: Int,
    val fileCount: Int,
    val fileName: String,
    val doneBytes: Long,
    val totalBytes: Long,
)

class ProgressThrottle(private val intervalMs: Long = 100) {
    private var last = 0L
    fun ready(force: Boolean = false): Boolean {
        val now = System.nanoTime() / 1_000_000
        if (force || now - last >= intervalMs) { last = now; return true }
        return false
    }
}

sealed interface SendOutcome {
    data class Completed(val skipped: Int, val skippedIndexes: Set<Int>) : SendOutcome
    data class Rejected(val reason: String) : SendOutcome
    data object CancelledByPeer : SendOutcome
}

object TransferSender {
    fun send(codec: FrameCodec, files: List<OutgoingFile>, timeouts: Timeouts, onProgress: (Progress) -> Unit): SendOutcome {
        val transfer = randomHex(16)
        val total = files.sumOf { it.size }
        codec.send(Message.TransferPropose(transfer, files.size, total))
        var index = 0
        for (batch in files.chunked(Limits.FILE_LIST_BATCH)) {
            codec.send(Message.FileList(transfer, batch.map { FileMeta((++index).toString(), it.name, it.size, it.dir, it.origin) }))
        }

        timeouts.set(Timing.SENDER_CONSENT_WAIT)
        when (val reply = codec.receiveMessage()) {
            is Message.TransferAccept -> if (reply.transfer != transfer) throw ProtocolException("bad_state", "wrong transfer")
            is Message.TransferReject -> return SendOutcome.Rejected(reply.reason.take(32))
            is Message.Cancel -> return SendOutcome.CancelledByPeer
            is Message.Error -> throw ProtocolException(reply.code.take(32), "peer reported an error", fromPeer = true)
            else -> throw ProtocolException("bad_state", "unexpected message")
        }
        timeouts.set(Timing.IDLE)

        val buf = ByteArray(CHUNK_SIZE)
        val throttle = ProgressThrottle()
        var done = 0L
        val skippedIdx = HashSet<Int>()
        for ((i, file) in files.withIndex()) {
            val id = (i + 1).toString()
            codec.send(Message.FileBegin(transfer, id))
            var sent = 0L
            var healthy = true
            val digest = MessageDigest.getInstance("SHA-256")
            val input = try { file.open() } catch (e: IOException) { null }
            if (input == null) healthy = false else input.use {
                while (true) {
                    // A file that vanished or changed since it was listed is skipped, not fatal.
                    val n = try { it.read(buf) } catch (e: IOException) { healthy = false; break }
                    if (n < 0) break
                    if (sent + n > file.size) { healthy = false; break }
                    digest.update(buf, 0, n)
                    codec.write(FrameKind.CHUNK, buf, 0, n)
                    sent += n
                    done += n
                    if (throttle.ready()) onProgress(Progress(i, files.size, file.name, done, total))
                }
            }
            if (healthy && sent != file.size) healthy = false
            if (healthy) {
                codec.send(Message.FileEnd(transfer, id, sent, digest.digest().toHex()))
            } else {
                codec.send(Message.FileEnd(transfer, id, skipped = true))
                skippedIdx += i
                done += file.size - sent
            }
            if (throttle.ready()) onProgress(Progress(i, files.size, file.name, done, total))
        }
        onProgress(Progress(files.size - 1, files.size, files.last().name, done, total))

        return when (val end = codec.receiveMessage()) {
            is Message.TransferComplete ->
                if (end.ok) SendOutcome.Completed(skippedIdx.size, skippedIdx) else throw ProtocolException("hash_mismatch", "receiver could not verify files", fromPeer = true)
            is Message.Cancel -> SendOutcome.CancelledByPeer
            is Message.Error -> throw ProtocolException(end.code.take(32), "peer reported an error", fromPeer = true)
            else -> throw ProtocolException("bad_state", "unexpected message")
        }
    }
}

/** [location] is for display; [ref] is a content:// URI or absolute path that can be used to open the file later. */
class Saved(val location: String, val ref: String)

interface StagedFile {
    val out: OutputStream
    /** Makes the verified file visible and says where it ended up. */
    fun commit(): Saved
    /** Discards everything written so far. Safe to call more than once. */
    fun abort()
}

interface ReceiveStore {
    fun freeBytes(): Long
    /** Starts a new file in sub-folder [dir] (already sanitized segments). Must never overwrite an existing file. */
    fun stage(dir: List<String>, displayName: String): StagedFile
}

/** What the receiving user is asked to approve. [preview] holds the first few files only. */
class Proposal(
    val transfer: String, val count: Int, val total: Long, val preview: List<FileMeta>, val folders: Int,
    /** True if the sender told us where files originally lived, so they can be put back there. */
    val hasOriginalPaths: Boolean,
)

/** The receiving user's answer. [restore] puts files back in their original folders instead of Downloads/Launch. */
class Decision(val accept: Boolean, val restore: Boolean = false)

class SavedRecord(val name: String, val dir: String, val size: Long, val ref: String)

sealed interface ReceiveOutcome {
    data class Completed(
        val saved: Int, val skipped: Int, val totalBytes: Long, val location: String, val restored: Boolean,
        /** First [TransferReceiver.MAX_RECORDS] saved files, for the history view. */
        val records: List<SavedRecord>,
    ) : ReceiveOutcome
    data object Declined : ReceiveOutcome
    data object CancelledByPeer : ReceiveOutcome
}

object TransferReceiver {
    private const val PREVIEW = 8
    const val MAX_RECORDS = 2000

    fun receive(
        codec: FrameCodec,
        store: ReceiveStore,
        restoreStore: ReceiveStore?,
        timeouts: Timeouts,
        maxTotal: Long,
        decide: (Proposal) -> Decision,
        onProgress: (Progress) -> Unit,
    ): ReceiveOutcome {
        timeouts.set(Timing.PROPOSE_WAIT)
        val propose = codec.expect<Message.TransferPropose>()
        try {
            ProposalValidator.validateHeader(propose, maxTotal)
        } catch (e: ProtocolException) {
            codec.send(Message.TransferReject(propose.transfer.take(64), "limits"))
            throw e
        }

        // The file list arrives in small batches so no control frame is ever large.
        val names = ArrayList<String>(minOf(propose.count, 4096))
        val dirs = ArrayList<List<String>>(minOf(propose.count, 4096))
        val origs = ArrayList<List<String>?>(minOf(propose.count, 4096))
        val sizes = ArrayList<Long>(minOf(propose.count, 4096))
        val preview = ArrayList<FileMeta>()
        val folderSet = HashSet<List<String>>()
        var sum = 0L
        try {
            while (names.size < propose.count) {
                val list = codec.expect<Message.FileList>()
                if (list.transfer != propose.transfer || list.files.isEmpty() || list.files.size > 64 ||
                    names.size + list.files.size > propose.count) throw ProtocolException("bad_message", "bad file list")
                for (f in list.files) {
                    sum = ProposalValidator.validateFile(f, names.size, sum, maxTotal)
                    val dir = DirPath.sanitize(f.dir)
                    val name = FilenameSanitizer.sanitize(f.name)
                    // Never restore into another app's private area; fall back to Downloads/Launch.
                    val orig = f.orig?.let { DirPath.sanitize(it) }?.takeIf { it.firstOrNull() != "Android" }
                    names += name; dirs += dir; origs += orig; sizes += f.size
                    if (dir.isNotEmpty() && folderSet.size < 100_000) folderSet += dir.take(1)
                    if (preview.size < PREVIEW) preview += FileMeta(f.id, name, f.size, dir.joinToString("/"))
                }
            }
            if (sum != propose.total) throw ProtocolException("size_mismatch", "total does not match files")
        } catch (e: ProtocolException) {
            if (!e.fromPeer) codec.send(Message.TransferReject(propose.transfer, "limits"))
            throw e
        }
        if (propose.total > store.freeBytes()) {
            codec.send(Message.TransferReject(propose.transfer, "no_space"))
            throw ProtocolException("no_space", "not enough free space")
        }
        val decision = decide(Proposal(propose.transfer, propose.count, propose.total, preview, folderSet.size, origs.any { it != null }))
        if (!decision.accept) {
            codec.send(Message.TransferReject(propose.transfer, "declined"))
            return ReceiveOutcome.Declined
        }
        codec.send(Message.TransferAccept(propose.transfer))
        timeouts.set(Timing.IDLE)

        val throttle = ProgressThrottle()
        var done = 0L
        var saved = 0
        var skipped = 0
        var location = ""
        val records = ArrayList<SavedRecord>()
        var restoredAny = false
        for (i in 0 until propose.count) {
            val id = (i + 1).toString()
            when (val begin = codec.receiveMessage()) {
                is Message.FileBegin -> if (begin.transfer != propose.transfer || begin.file != id) throw ProtocolException("bad_state", "unexpected file")
                is Message.Cancel -> return ReceiveOutcome.CancelledByPeer
                is Message.Error -> throw ProtocolException(begin.code.take(32), "peer reported an error", fromPeer = true)
                else -> throw ProtocolException("bad_state", "unexpected message")
            }
            val size = sizes[i]
            val restoreTo = if (decision.restore && restoreStore != null) origs[i] else null
            val staged = try {
                if (restoreTo != null) restoreStore!!.stage(restoreTo, names[i]) else store.stage(dirs[i], names[i])
            } catch (e: IOException) { throw ProtocolException("storage_error", "could not create file") }
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                var skip = false
                loop@ while (true) {
                    val frame = codec.read()
                    if (frame.kind == FrameKind.CHUNK) {
                        received += frame.payload.size
                        if (received > size) throw ProtocolException("size_mismatch", "more data than declared")
                        digest.update(frame.payload)
                        try {
                            staged.out.write(frame.payload)
                        } catch (e: IOException) {
                            throw ProtocolException("storage_error", "could not write file")
                        }
                        done += frame.payload.size
                        if (throttle.ready()) onProgress(Progress(i, propose.count, names[i], done, propose.total))
                        continue
                    }
                    when (val m = MessageCodec.decode(frame.payload)) {
                        is Message.FileEnd -> {
                            if (m.transfer != propose.transfer || m.file != id) throw ProtocolException("bad_state", "unexpected file")
                            if (m.skipped) { skip = true; break@loop }
                            if (m.bytes != received || received != size) throw ProtocolException("size_mismatch", "size mismatch")
                            if (!MessageDigest.isEqual(digest.digest().toHex().toByteArray(), m.sha256.toByteArray())) {
                                throw ProtocolException("hash_mismatch", "hash mismatch")
                            }
                            break@loop
                        }
                        is Message.Cancel -> { staged.abort(); return ReceiveOutcome.CancelledByPeer }
                        is Message.Error -> throw ProtocolException(m.code.take(32), "peer reported an error", fromPeer = true)
                        else -> throw ProtocolException("bad_state", "unexpected message")
                    }
                }
                if (skip) {
                    staged.abort()
                    skipped++
                    done += size - received
                } else {
                    try {
                        staged.out.flush()
                        val s = staged.commit()
                        location = s.location
                        if (restoreTo != null) restoredAny = true
                        if (records.size < MAX_RECORDS) records += SavedRecord(names[i], (restoreTo ?: dirs[i]).joinToString("/"), size, s.ref)
                    } catch (e: IOException) {
                        throw ProtocolException("storage_error", "could not save file")
                    }
                    saved++
                }
                if (throttle.ready()) onProgress(Progress(i, propose.count, names[i], done, propose.total))
            } catch (t: Throwable) {
                staged.abort()
                throw t
            }
        }
        onProgress(Progress(propose.count - 1, propose.count, names.last(), done, propose.total))
        codec.send(Message.TransferComplete(propose.transfer, true, saved, skipped))
        return ReceiveOutcome.Completed(saved, skipped, propose.total, location, restoredAny, records)
    }
}

/** Picks a name not already taken, e.g. `photo.jpg` -> `photo (1).jpg`. Never overwrites. */
fun uniqueName(name: String, exists: (String) -> Boolean): String {
    if (!exists(name)) return name
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    val stem = name.substring(0, dot)
    val ext = name.substring(dot)
    var i = 1
    while (true) {
        val candidate = "$stem ($i)$ext"
        if (!exists(candidate)) return candidate
        i++
    }
}
