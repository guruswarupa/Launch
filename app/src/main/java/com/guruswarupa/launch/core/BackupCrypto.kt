package com.guruswarupa.launch.core

import android.content.Context
import android.net.Uri
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {

    private val MAGIC = "LNCHBK1\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val ITERATIONS = 310000
    private const val KEY_BITS = 256
    private const val CHUNK_SIZE = 256 * 1024
    private const val FINAL_FLAG = 0x80000000.toInt()

    class WrongPassphraseException : IOException("Wrong passphrase or corrupted backup")

    fun isEncrypted(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(MAGIC.size)
                readFully(input, header) && header.contentEquals(MAGIC)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun nonceFor(base: ByteArray, counter: Long): ByteArray {
        val nonce = base.copyOf()
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        for (i in 0 until 8) nonce[NONCE_SIZE - 8 + i] = (nonce[NONCE_SIZE - 8 + i].toInt() xor counterBytes[i].toInt()).toByte()
        return nonce
    }

    private fun aad(counter: Long, final: Boolean): ByteArray =
        ByteBuffer.allocate(9).putLong(counter).put(if (final) 1 else 0).array()

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int = buffer.size): Boolean {
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    fun encryptingStream(target: OutputStream, passphrase: CharArray): OutputStream {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val baseNonce = ByteArray(NONCE_SIZE).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        target.write(MAGIC)
        target.write(salt)
        target.write(baseNonce)

        return object : OutputStream() {
            private val buffer = ByteArray(CHUNK_SIZE)
            private var filled = 0
            private var counter = 0L
            private var closed = false

            private fun writeChunk(final: Boolean) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonceFor(baseNonce, counter)))
                cipher.updateAAD(aad(counter, final))
                val encrypted = cipher.doFinal(buffer, 0, filled)
                val header = if (final) encrypted.size or FINAL_FLAG else encrypted.size
                target.write(ByteBuffer.allocate(4).putInt(header).array())
                target.write(encrypted)
                counter++
                filled = 0
            }

            override fun write(b: Int) {
                if (filled == CHUNK_SIZE) writeChunk(false)
                buffer[filled++] = b.toByte()
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                var offset = off
                var remaining = len
                while (remaining > 0) {
                    if (filled == CHUNK_SIZE) writeChunk(false)
                    val n = minOf(remaining, CHUNK_SIZE - filled)
                    System.arraycopy(b, offset, buffer, filled, n)
                    filled += n
                    offset += n
                    remaining -= n
                }
            }

            override fun flush() {
                target.flush()
            }

            override fun close() {
                if (closed) return
                closed = true
                writeChunk(true)
                target.flush()
                target.close()
            }
        }
    }

    fun decryptingStream(source: InputStream, passphrase: CharArray): InputStream {
        val header = ByteArray(MAGIC.size)
        if (!readFully(source, header) || !header.contentEquals(MAGIC)) throw IOException("Not an encrypted backup")
        val salt = ByteArray(SALT_SIZE)
        val baseNonce = ByteArray(NONCE_SIZE)
        if (!readFully(source, salt) || !readFully(source, baseNonce)) throw EOFException("Truncated backup")
        val key = deriveKey(passphrase, salt)

        return object : InputStream() {
            private var plain = ByteArray(0)
            private var position = 0
            private var counter = 0L
            private var finished = false

            private fun fill(): Boolean {
                if (finished) return false
                val lengthBytes = ByteArray(4)
                if (!readFully(source, lengthBytes)) throw EOFException("Backup is truncated")
                val header = ByteBuffer.wrap(lengthBytes).int
                val final = header and FINAL_FLAG != 0
                val length = header and FINAL_FLAG.inv()
                if (length < 16 || length > CHUNK_SIZE + 16) throw IOException("Corrupted backup")
                val encrypted = ByteArray(length)
                if (!readFully(source, encrypted)) throw EOFException("Backup is truncated")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonceFor(baseNonce, counter)))
                cipher.updateAAD(aad(counter, final))
                plain = try {
                    cipher.doFinal(encrypted)
                } catch (_: Exception) {
                    throw WrongPassphraseException()
                }
                position = 0
                counter++
                if (final) finished = true
                return true
            }

            override fun read(): Int {
                while (position >= plain.size) {
                    if (!fill()) return -1
                }
                return plain[position++].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                while (position >= plain.size) {
                    if (!fill()) return -1
                }
                val n = minOf(len, plain.size - position)
                System.arraycopy(plain, position, b, off, n)
                position += n
                return n
            }

            override fun close() {
                source.close()
            }
        }
    }
}
