package com.guruswarupa.launch.transfer.security

import com.guruswarupa.launch.transfer.data.protocol.FrameCodec
import com.guruswarupa.launch.transfer.data.protocol.Message
import com.guruswarupa.launch.transfer.data.protocol.PROTOCOL_VERSION
import com.guruswarupa.launch.transfer.data.protocol.ProtocolException
import com.guruswarupa.launch.transfer.data.protocol.cleanLabel
import com.guruswarupa.launch.transfer.data.protocol.expect
import com.guruswarupa.launch.transfer.data.protocol.send
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pairing binds the TLS channel to a human- or QR-verified secret.
 *  - secret mode (QR): both sides prove knowledge of a 128-bit one-time secret, MAC'd over both certificate
 *    fingerprints and both session nonces, so a man-in-the-middle with different certificates fails.
 *  - sas mode (discovery / manual): both users compare an 8-digit short authentication string derived from
 *    the same values; a man-in-the-middle's certificates yield different digits on each side.
 */
object Pairing {
    fun sas(senderFp: String, receiverFp: String, senderSession: String, receiverSession: String): String {
        val d = MessageDigest.getInstance("SHA-256")
            .digest("Launch-sas-v1|$senderFp|$receiverFp|$senderSession|$receiverSession".toByteArray())
        var n = 0L
        for (i in 0 until 4) n = (n shl 8) or (d[i].toLong() and 0xff)
        val s = (n % 100_000_000L).toString().padStart(8, '0')
        return s.substring(0, 4) + " " + s.substring(4)
    }

    fun proof(secret: ByteArray, role: Char, senderFp: String, receiverFp: String, senderSession: String, receiverSession: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
        return mac.doFinal("Launch-pair-v1|$role|$senderFp|$receiverFp|$senderSession|$receiverSession".toByteArray()).toHex()
    }

    fun proofMatches(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
}

/** Result of the HELLO/PAIR exchange. [sas] is non-null when the users must compare codes. */
class PeerInfo(val device: String, val sas: String?, val usedSecret: Boolean)

object Handshake {
    private const val MODE_SECRET = "secret"
    private const val MODE_SAS = "sas"

    fun sender(codec: FrameCodec, device: String, localFp: String, peerFp: String, secret: ByteArray?): PeerInfo {
        val mine = randomHex(16)
        codec.send(Message.Hello(PROTOCOL_VERSION, mine, device, if (secret != null) MODE_SECRET else MODE_SAS))
        val peer = codec.expect<Message.Hello>()
        if (peer.version < 1) throw ProtocolException("version_unsupported", "unsupported version")
        if (peer.session.length != 32 || peer.session.hexToBytes() == null) throw ProtocolException("bad_message", "bad session")
        val label = cleanLabel(peer.device)
        if (secret == null) return PeerInfo(label, Pairing.sas(localFp, peerFp, mine, peer.session), false)

        codec.send(Message.Pair(Pairing.proof(secret, 'C', localFp, peerFp, mine, peer.session)))
        val reply = codec.expect<Message.Pair>()
        if (!Pairing.proofMatches(Pairing.proof(secret, 'S', localFp, peerFp, mine, peer.session), reply.proof)) {
            throw ProtocolException("pair_failed", "pairing proof rejected")
        }
        return PeerInfo(label, null, true)
    }

    fun receiver(codec: FrameCodec, device: String, localFp: String, peerFp: String, secret: ByteArray?): PeerInfo {
        val hello = codec.expect<Message.Hello>()
        if (hello.version < 1) throw ProtocolException("version_unsupported", "unsupported version")
        if (hello.session.length != 32 || hello.session.hexToBytes() == null) throw ProtocolException("bad_message", "bad session")
        val mine = randomHex(16)
        codec.send(Message.Hello(minOf(PROTOCOL_VERSION, hello.version), mine, device, MODE_SAS))
        val label = cleanLabel(hello.device)
        if (hello.mode != MODE_SECRET) return PeerInfo(label, Pairing.sas(peerFp, localFp, hello.session, mine), false)

        if (secret == null) throw ProtocolException("pair_failed", "no active pairing secret")
        val pair = codec.expect<Message.Pair>()
        if (!Pairing.proofMatches(Pairing.proof(secret, 'C', peerFp, localFp, hello.session, mine), pair.proof)) {
            throw ProtocolException("pair_failed", "pairing proof rejected")
        }
        codec.send(Message.Pair(Pairing.proof(secret, 'S', peerFp, localFp, hello.session, mine)))
        return PeerInfo(label, null, true)
    }
}
