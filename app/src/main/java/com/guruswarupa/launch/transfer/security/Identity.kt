package com.guruswarupa.launch.transfer.security

import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/** A device's long-lived TLS identity: a self-signed EC certificate. Identity = certificate fingerprint. */
class Identity(val privateKey: PrivateKey, val certificate: X509Certificate) {
    val fingerprint: String = sha256Hex(certificate.encoded)

    companion object {
        private const val DAY_MS = 86_400_000L

        fun generate(): Identity {
            val pair = KeyPairGenerator.getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
            val name = X500Name("CN=Launch")
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(
                name,
                BigInteger(63, SecureRandom()).add(BigInteger.ONE),
                Date(now - DAY_MS),
                Date(now + 3650 * DAY_MS),
                name,
                pair.public,
            )
            val signer = JcaContentSignerBuilder("SHA256withECDSA").build(pair.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            return Identity(pair.private, cert)
        }
    }
}

/** Persists the identity in app-private storage (not hardware-backed; see SECURITY.md). */
class IdentityStore(private val dir: File) {
    fun loadOrCreate(): Identity {
        val keyFile = File(dir, "identity.key")
        val certFile = File(dir, "identity.crt")
        if (keyFile.isFile && certFile.isFile) {
            runCatching {
                val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                val cert = CertificateFactory.getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certFile.readBytes())) as X509Certificate
                return Identity(key, cert)
            }
        }
        val identity = Identity.generate()
        dir.mkdirs()
        certFile.writeBytes(identity.certificate.encoded)
        keyFile.writeBytes(identity.privateKey.encoded)
        keyFile.setReadable(false, false)
        keyFile.setReadable(true, true)
        return identity
    }
}
