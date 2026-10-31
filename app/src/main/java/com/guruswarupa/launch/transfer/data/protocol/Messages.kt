package com.guruswarupa.launch.transfer.data.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

const val PROTOCOL_VERSION = 1

@Serializable
/** [dir] groups files by the selected folder; [orig] is the folder the file originally lived in (relative to the sender's storage root), if known. */
data class FileMeta(val id: String, val name: String, val size: Long, val dir: String = "", val orig: String? = null)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface Message {
    @Serializable @SerialName("HELLO")
    data class Hello(val version: Int, val session: String, val device: String, val mode: String = "sas") : Message

    @Serializable @SerialName("PAIR")
    data class Pair(val proof: String) : Message

    @Serializable @SerialName("TRANSFER_PROPOSE")
    data class TransferPropose(val transfer: String, val count: Int, val total: Long) : Message

    @Serializable @SerialName("FILE_LIST")
    data class FileList(val transfer: String, val files: List<FileMeta>) : Message

    @Serializable @SerialName("TRANSFER_ACCEPT")
    data class TransferAccept(val transfer: String) : Message

    @Serializable @SerialName("TRANSFER_REJECT")
    data class TransferReject(val transfer: String, val reason: String) : Message

    @Serializable @SerialName("FILE_BEGIN")
    data class FileBegin(val transfer: String, val file: String) : Message

    @Serializable @SerialName("FILE_END")
    data class FileEnd(val transfer: String, val file: String, val bytes: Long = 0, val sha256: String = "", val skipped: Boolean = false) : Message

    @Serializable @SerialName("TRANSFER_COMPLETE")
    data class TransferComplete(val transfer: String, val ok: Boolean, val saved: Int = 0, val skipped: Int = 0) : Message

    @Serializable @SerialName("CANCEL")
    data class Cancel(val transfer: String) : Message

    @Serializable @SerialName("ERROR")
    data class Error(val code: String, val message: String) : Message
}

object MessageCodec {
    private val json = Json { ignoreUnknownKeys = false; classDiscriminator = "type" }

    fun encode(message: Message): ByteArray = json.encodeToString(Message.serializer(), message).toByteArray(Charsets.UTF_8)

    fun decode(payload: ByteArray): Message = try {
        json.decodeFromString(Message.serializer(), String(payload, Charsets.UTF_8))
    } catch (e: Exception) {
        throw ProtocolException("bad_message", "malformed control message")
    }
}

fun FrameCodec.send(message: Message) = write(FrameKind.CONTROL, MessageCodec.encode(message))

fun FrameCodec.receiveMessage(): Message {
    val frame = read()
    if (frame.kind != FrameKind.CONTROL) throw ProtocolException("bad_state", "expected control message")
    return MessageCodec.decode(frame.payload)
}

/** Reads the next control message; a peer ERROR becomes a [ProtocolException] flagged `fromPeer`. */
inline fun <reified T : Message> FrameCodec.expect(): T {
    val m = receiveMessage()
    if (m is Message.Error) throw ProtocolException(m.code.take(32), "peer reported an error", fromPeer = true)
    return m as? T ?: throw ProtocolException("bad_state", "unexpected message")
}
