package com.guruswarupa.launch.transfer.security

import java.security.MessageDigest
import java.security.SecureRandom

private val rng = SecureRandom()
private const val HEX = "0123456789abcdef"

fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        sb.append(HEX[(b.toInt() shr 4) and 0xf]).append(HEX[b.toInt() and 0xf])
    }
    return sb.toString()
}

fun String.hexToBytes(): ByteArray? {
    if (length % 2 != 0) return null
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val hi = HEX.indexOf(this[i * 2].lowercaseChar())
        val lo = HEX.indexOf(this[i * 2 + 1].lowercaseChar())
        if (hi < 0 || lo < 0) return null
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

fun randomHex(byteCount: Int): String = ByteArray(byteCount).also { rng.nextBytes(it) }.toHex()

fun randomBytes(byteCount: Int): ByteArray = ByteArray(byteCount).also { rng.nextBytes(it) }
