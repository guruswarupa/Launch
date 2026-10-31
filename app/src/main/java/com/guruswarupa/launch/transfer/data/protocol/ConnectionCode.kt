package com.guruswarupa.launch.transfer.data.protocol

import com.guruswarupa.launch.transfer.security.hexToBytes
import com.guruswarupa.launch.transfer.security.toHex

/**
 * What a QR code / typed code carries: where to connect, optionally the receiver's certificate
 * fingerprint and a one-time pairing secret. Contains no file data and no long-lived credentials.
 */
class ConnectionCode(val host: String, val port: Int, val fingerprint: String? = null, val secret: ByteArray? = null) {
    fun toUri(): String = buildString {
        append("launchtx://v1?h=").append(host).append("&p=").append(port)
        if (fingerprint != null) append("&f=").append(fingerprint)
        if (secret != null) append("&s=").append(secret.toHex())
    }

    companion object {
        private val hostRegex = Regex("^[A-Za-z0-9.-]{1,253}$")
        private val fpRegex = Regex("^[0-9a-f]{64}$")

        /** Accepts `launchtx://v1?h=..&p=..&f=..&s=..` or a bare `host:port`. Returns null if invalid. */
        fun parse(text: String): ConnectionCode? {
            val t = text.trim()
            if (t.startsWith("launchtx://")) return parseUri(t)
            val idx = t.lastIndexOf(':')
            if (idx <= 0) return null
            return build(t.substring(0, idx), t.substring(idx + 1), null, null)
        }

        private fun parseUri(t: String): ConnectionCode? {
            val query = t.substringAfter('?', "")
            if (!t.startsWith("launchtx://v1?") || query.isEmpty()) return null
            val params = query.split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
            return build(params["h"], params["p"], params["f"], params["s"])
        }

        private fun build(host: String?, port: String?, fp: String?, secret: String?): ConnectionCode? {
            if (host == null || !hostRegex.matches(host)) return null
            val p = port?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            if (fp != null && !fpRegex.matches(fp)) return null
            val s = if (secret == null) null else secret.hexToBytes()?.takeIf { it.size == 16 } ?: return null
            // A secret is only meaningful together with the fingerprint it is bound to.
            if (s != null && fp == null) return null
            return ConnectionCode(host, p, fp, s)
        }
    }
}
