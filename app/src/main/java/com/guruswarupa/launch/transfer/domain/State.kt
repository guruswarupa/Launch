package com.guruswarupa.launch.transfer.domain

import com.guruswarupa.launch.transfer.data.protocol.FileMeta
import com.guruswarupa.launch.transfer.data.transport.Progress
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import com.guruswarupa.launch.transfer.data.protocol.ProtocolException

/** A file to send. [dir] is its relative folder (e.g. "DCIM/Camera"), empty for a loose file. */
class SourceFile(
    val name: String,
    val size: Long,
    val dir: String = "",
    /** Folder the file lives in, relative to the storage root, when known (used to restore it there). */
    val origin: String? = null,
    /** content:// URI or absolute path for reopening the file from history. */
    val ref: String? = null,
    val open: () -> InputStream,
)

sealed interface SendState {
    data object Idle : SendState
    data object Preparing : SendState
    data object Connecting : SendState
    data class WaitingForReceiver(val peer: String) : SendState
    data class Transferring(val peer: String, val progress: Progress, val bytesPerSecond: Long) : SendState
    data class Done(val peer: String, val files: Int, val skipped: Int, val bytes: Long) : SendState
    data class Declined(val peer: String) : SendState
    data class Failed(val message: String) : SendState
    data object Cancelled : SendState
}

sealed interface ReceiveState {
    data object Off : ReceiveState
    data object Listening : ReceiveState
    data class Incoming(val peer: String, val preview: List<FileMeta>, val count: Int, val folders: Int, val total: Long, val canRestore: Boolean) : ReceiveState
    data class Receiving(val peer: String, val progress: Progress, val bytesPerSecond: Long) : ReceiveState
    data class Done(val peer: String, val saved: Int, val skipped: Int, val bytes: Long, val restored: Boolean) : ReceiveState
    data class Failed(val message: String) : ReceiveState
}

object Errors {
    fun describe(code: String): String = when (code) {
        "pair_failed" -> "Pairing failed. Scan the QR code again or check you are connecting to the right device."
        "limits_exceeded", "limits" -> "That transfer is larger than the other device accepts."
        "size_mismatch", "hash_mismatch" -> "A file did not arrive intact. Please try again."
        "no_space" -> "Not enough free storage on the receiving device."
        "storage_error" -> "The file could not be saved."
        "version_unsupported" -> "The other device uses an incompatible version of this app."
        "timeout" -> "The other device stopped responding."
        "empty" -> "There is nothing to send."
        "busy" -> "The other device is busy with another transfer."
        "cancelled" -> "The other device cancelled the transfer."
        "bad_frame", "bad_message", "bad_state" -> "The other device sent unexpected data."
        else -> "Something went wrong. Please try again."
    }

    fun describe(t: Throwable): String = when (t) {
        is ProtocolException -> describe(t.code)
        is SocketTimeoutException -> describe("timeout")
        is IOException -> "Connection lost. Check both devices are on the same Wi-Fi network and try again."
        else -> describe("internal")
    }
}

/** Average speed since [startNanos], guarding against division by zero. */
internal fun speed(bytes: Long, startNanos: Long): Long {
    val ms = (System.nanoTime() - startNanos) / 1_000_000
    return if (ms < 250) 0 else bytes * 1000 / ms
}
