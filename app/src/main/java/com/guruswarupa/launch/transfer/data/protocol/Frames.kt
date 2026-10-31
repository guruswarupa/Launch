package com.guruswarupa.launch.transfer.data.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class ProtocolException(val code: String, message: String, val fromPeer: Boolean = false) : IOException(message)

object FrameKind {
    const val CONTROL: Int = 0x01
    const val CHUNK: Int = 0x02
}

object Limits {
    const val MAX_CONTROL_BYTES = 65_536
    const val MAX_CHUNK_BYTES = 1_048_576
    const val MAX_FILES = 200_000
    const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024 * 1024
    const val MAX_NAME_BYTES = 255
    const val MAX_DIR_BYTES = 1024
    const val MAX_DIR_DEPTH = 16
    /** Files per FILE_LIST message; keeps every control frame far below [MAX_CONTROL_BYTES]. */
    const val FILE_LIST_BATCH = 16
}

class Frame(val kind: Int, val payload: ByteArray)

/** Length-prefixed framing: `u32 length | u8 kind | payload`, length = 1 + payload size. */
class FrameCodec(input: InputStream, output: OutputStream) {
    private val din = DataInputStream(input)
    private val dout = DataOutputStream(output)

    @Synchronized
    fun write(kind: Int, payload: ByteArray, offset: Int = 0, len: Int = payload.size) {
        require(offset >= 0 && len >= 0 && offset + len <= payload.size)
        if (len > maxPayload(kind)) throw ProtocolException("bad_frame", "frame too large")
        dout.writeInt(len + 1)
        dout.writeByte(kind)
        dout.write(payload, offset, len)
        dout.flush()
    }

    fun read(): Frame {
        val length = din.readInt()
        if (length < 1) throw ProtocolException("bad_frame", "invalid frame length")
        // The kind byte is read before the size check so the per-kind limit applies,
        // and nothing is allocated until the length is known to be acceptable.
        val kind = din.readUnsignedByte()
        val max = maxPayload(kind)
        val payloadLen = length - 1
        if (payloadLen > max) throw ProtocolException("bad_frame", "frame too large")
        val payload = ByteArray(payloadLen)
        din.readFully(payload)
        return Frame(kind, payload)
    }

    private fun maxPayload(kind: Int): Int = when (kind) {
        FrameKind.CONTROL -> Limits.MAX_CONTROL_BYTES - 1
        FrameKind.CHUNK -> Limits.MAX_CHUNK_BYTES
        else -> throw ProtocolException("bad_frame", "unknown frame kind")
    }
}
