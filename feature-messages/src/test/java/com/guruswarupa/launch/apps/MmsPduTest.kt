package com.guruswarupa.launch.apps

import com.guruswarupa.launch.apps.messages.MmsPart
import com.guruswarupa.launch.apps.messages.MmsPdu
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsPduTest {

    @Test
    fun sendRequestRoundTripKeepsEveryPart() {
        val image = ByteArray(300) { (it % 251).toByte() }
        val pdu = MmsPdu.sendReq("+919876543210", listOf(
            MmsPart("text/plain", "text.txt", "<text>", "héllo 😊".toByteArray(Charsets.UTF_8)),
            MmsPart("image/jpeg", "photo.jpg", "<photo>", image)
        ), "tx123")
        // starts with m-send-req and carries the transaction id
        assertEquals(0x8C, pdu[0].toInt() and 0xFF)
        assertEquals(0x80, pdu[1].toInt() and 0xFF)
        val parts = MmsPdu.bodyOfSendReq(pdu)
        assertEquals(3, parts.size)                      // SMIL + text + image
        assertEquals("application/smil", parts[0].contentType)
        assertEquals("text/plain", parts[1].contentType)
        assertEquals("héllo 😊", String(parts[1].data, Charsets.UTF_8))
        assertEquals("image/jpeg", parts[2].contentType)
        assertEquals("photo.jpg", parts[2].name)
        assertArrayEquals(image, parts[2].data)
    }

    @Test
    fun largePartLengthsUseMultiByteLengths() {
        val big = ByteArray(70_000) { 7 }
        val parts = MmsPdu.bodyOfSendReq(MmsPdu.sendReq("5550100", listOf(MmsPart("video/3gpp", "clip.3gp", null, big)), "t"))
        assertEquals(big.size, parts.last().data.size)
        assertEquals("video/3gpp", parts.last().contentType)
    }

    @Test
    fun smilMentionsEachMedia() {
        val pdu = MmsPdu.sendReq("5550100", listOf(MmsPart("audio/amr", "voice.amr", null, ByteArray(10)), MmsPart("image/png", "a.png", null, ByteArray(10))), "t")
        val smil = String(MmsPdu.bodyOfSendReq(pdu)[0].data, Charsets.UTF_8)
        assertTrue(smil.contains("<audio src=\"voice.amr\""))
        assertTrue(smil.contains("<img src=\"a.png\""))
    }

    @Test
    fun readsANotificationIndication() {
        val loc = "http://mms.example.net/get?id=42"
        val pdu = ArrayList<Byte>()
        fun b(vararg x: Int) = x.forEach { pdu.add(it.toByte()) }
        fun t(s: String) { s.toByteArray().forEach { pdu.add(it) }; pdu.add(0) }
        b(0x8C, 0x82)                       // m-notification-ind
        b(0x98); t("txn-9")                 // transaction id
        b(0x8D, 0x92)                       // version
        b(0x89, 0x0F, 0x80); t("+919999999999/TYPE=PLMN").also { }  // from (length is patched below)
        b(0x8A, 0x80)
        b(0x8E, 0x02, 0x01, 0xF4)           // size = 500
        b(0x83); t(loc)
        val bytes = pdu.toByteArray()
        // fix the from-length: value is 0x80 token + text + NUL
        val fromIdx = bytes.indexOf(0x89.toByte())
        bytes[fromIdx + 1] = ("+919999999999/TYPE=PLMN".length + 2).toByte()
        val n = MmsPdu.parseNotification(bytes)
        assertNotNull(n)
        assertEquals("txn-9", n!!.transactionId)
        assertEquals(loc, n.location)
        assertEquals(500L, n.size)
        assertEquals("+919999999999", n.from)
    }

    @Test
    fun garbageIsRejectedNotCrashed() {
        assertNull(MmsPdu.parseNotification(byteArrayOf(1, 2, 3)))
        assertNull(MmsPdu.parseRetrieveConf(byteArrayOf(0x8C.toByte())))
    }
}
