package com.guruswarupa.launch.transfer.data.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    private val hash = "a".repeat(64)

    private fun roundTrip(m: Message): Message {
        val out = ByteArrayOutputStream()
        FrameCodec(ByteArrayInputStream(ByteArray(0)), out).send(m)
        return FrameCodec(ByteArrayInputStream(out.toByteArray()), ByteArrayOutputStream()).receiveMessage()
    }

    private fun readRaw(vararg bytes: Int): Frame =
        FrameCodec(ByteArrayInputStream(ByteArray(bytes.size) { bytes[it].toByte() }), ByteArrayOutputStream()).read()

    @Test fun messagesRoundTrip() {
        val msgs = listOf(
            Message.Hello(1, "ab12", "Pixel"),
            Message.TransferPropose("t1", 1, 3),
            Message.FileList("t1", listOf(FileMeta("1", "a.txt", 3, "DCIM/Camera"))),
            Message.TransferAccept("t1"),
            Message.TransferReject("t1", "declined"),
            Message.FileBegin("t1", "f1"),
            Message.FileEnd("t1", "f1", 3, hash),
            Message.FileEnd("t1", "f1", skipped = true),
            Message.TransferComplete("t1", true, 2, 1),
            Message.Cancel("t1"),
            Message.Error("bad_state", "x"),
        )
        for (m in msgs) assertEquals(m, roundTrip(m))
    }

    @Test fun chunkFrameRoundTrip() {
        val out = ByteArrayOutputStream()
        FrameCodec(ByteArrayInputStream(ByteArray(0)), out).write(FrameKind.CHUNK, byteArrayOf(1, 2, 3))
        val f = FrameCodec(ByteArrayInputStream(out.toByteArray()), ByteArrayOutputStream()).read()
        assertEquals(FrameKind.CHUNK, f.kind)
        assertEquals(listOf<Byte>(1, 2, 3), f.payload.toList())
    }

    @Test fun rejectsZeroLength() {
        assertEquals("bad_frame", assertThrows(ProtocolException::class.java) { readRaw(0, 0, 0, 0) }.code)
    }

    @Test fun rejectsOversizedControlBeforeAllocating() {
        // Declares ~2 GiB but supplies no payload; must fail on the limit, not on EOF/OOM.
        val e = assertThrows(ProtocolException::class.java) { readRaw(0x7f, 0xff, 0xff, 0xff, 0x01) }
        assertEquals("bad_frame", e.code)
    }

    @Test fun rejectsUnknownKind() {
        assertEquals("bad_frame", assertThrows(ProtocolException::class.java) { readRaw(0, 0, 0, 2, 0x09, 0) }.code)
    }

    @Test fun rejectsMalformedAndUnknownMessages() {
        for (s in listOf("{", "{\"type\":\"NOPE\"}", "{\"type\":\"CANCEL\"}", "{\"type\":\"CANCEL\",\"transfer\":\"t\",\"x\":1}")) {
            val e = assertThrows(ProtocolException::class.java) { MessageCodec.decode(s.toByteArray()) }
            assertEquals("bad_message", e.code)
        }
    }

    @Test fun writerRefusesOversizedChunk() {
        val codec = FrameCodec(ByteArrayInputStream(ByteArray(0)), DataOutputStream(ByteArrayOutputStream()))
        assertThrows(ProtocolException::class.java) { codec.write(FrameKind.CHUNK, ByteArray(Limits.MAX_CHUNK_BYTES + 1)) }
    }

    private fun header(count: Int = 1, total: Long = 0) = Message.TransferPropose("t", count, total)

    @Test fun validHeaderAndFilesPass() {
        ProposalValidator.validateHeader(header(2, 5))
        var sum = ProposalValidator.validateFile(FileMeta("1", "a", 0), 0, 0)
        sum = ProposalValidator.validateFile(FileMeta("2", "b", 5, "x/y"), 1, sum)
        assertEquals(5L, sum)
    }

    @Test fun rejectsBadHeaders() {
        for (h in listOf(header(0), header(Limits.MAX_FILES + 1), header(1, -1), header(1, Limits.MAX_TOTAL_BYTES + 1), Message.TransferPropose("", 1, 1))) {
            assertThrows(ProtocolException::class.java) { ProposalValidator.validateHeader(h) }
        }
    }

    @Test fun rejectsBadFiles() {
        val cases = mapOf(
            "wrongId" to { ProposalValidator.validateFile(FileMeta("2", "a", 1), 0, 0) },
            "dupId" to { ProposalValidator.validateFile(FileMeta("1", "a", 1), 1, 0) },
            "negative" to { ProposalValidator.validateFile(FileMeta("1", "a", -1), 0, 0) },
            "longName" to { ProposalValidator.validateFile(FileMeta("1", "é".repeat(200), 1), 0, 0) },
            "overflow" to { ProposalValidator.validateFile(FileMeta("2", "b", 1), 1, Long.MAX_VALUE) },
        )
        for ((label, f) in cases) assertThrows(label, ProtocolException::class.java) { f() }
    }

    @Test fun sanitizesDirectories() {
        assertEquals(listOf("DCIM", "Camera"), DirPath.sanitize("DCIM/Camera"))
        assertEquals(listOf("etc"), DirPath.sanitize("../../etc"))
        assertEquals(listOf("a", "b"), DirPath.sanitize("/a/./b//"))
        assertEquals(listOf("a_b"), DirPath.sanitize("a\\b"))
        assertEquals(emptyList<String>(), DirPath.sanitize(""))
        assertThrows(ProtocolException::class.java) { DirPath.sanitize("a/".repeat(20)) }
        assertThrows(ProtocolException::class.java) { DirPath.sanitize("x".repeat(2000)) }
    }

    @Test fun sanitizesNames() {
        assertEquals("passwd", FilenameSanitizer.sanitize("..").ifEmpty { "x" }.let { FilenameSanitizer.sanitize("passwd") })
        assertEquals("file", FilenameSanitizer.sanitize(".."))
        assertEquals("file", FilenameSanitizer.sanitize("   "))
        assertEquals(".._etc_passwd".trim('.'), FilenameSanitizer.sanitize("../etc/passwd"))
        assertEquals("a_b", FilenameSanitizer.sanitize("a\u0000b"))
        assertEquals("a_b_c", FilenameSanitizer.sanitize("a\\b/c"))
        assertEquals("фото.jpg", FilenameSanitizer.sanitize("фото.jpg"))
        assertTrue(FilenameSanitizer.sanitize("é".repeat(300)).toByteArray().size <= Limits.MAX_NAME_BYTES)
    }
}
