package com.guruswarupa.launch.transfer.security

import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Mutual TLS with self-signed certificates. TLS only provides encryption here; authentication is done
 * by pinning a fingerprint learned out-of-band (QR) and/or by the pairing step bound to both fingerprints.
 */
object Tls {
    private val KEY_PASSWORD = "launch".toCharArray()

    fun context(identity: Identity, pinnedPeerFingerprint: String?): SSLContext {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("identity", identity.privateKey, KEY_PASSWORD, arrayOf(identity.certificate))
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, KEY_PASSWORD) }
        val trust = PinningTrustManager(pinnedPeerFingerprint)
        return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(trust), SecureRandom()) }
    }

    fun harden(socket: SSLSocket) {
        val protocols = socket.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }
        socket.enabledProtocols = protocols.toTypedArray()
    }

    fun peerFingerprint(socket: SSLSocket): String {
        val cert = socket.session.peerCertificates.firstOrNull() ?: throw CertificateException("no peer certificate")
        return sha256Hex(cert.encoded)
    }

    private class PinningTrustManager(private val pinned: String?) : X509TrustManager {
        private fun check(chain: Array<X509Certificate>?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("empty chain")
            if (pinned == null) return
            val actual = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
            if (!MessageDigest.isEqual(actual, pinned.hexToBytes() ?: ByteArray(0))) {
                throw CertificateException("peer fingerprint mismatch")
            }
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = check(chain)
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = check(chain)
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}

