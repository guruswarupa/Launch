package com.guruswarupa.launch.transfer

import com.guruswarupa.launch.transfer.data.protocol.ConnectionCode
import com.guruswarupa.launch.transfer.data.transport.ReceiveStore
import com.guruswarupa.launch.transfer.data.transport.StagedFile
import com.guruswarupa.launch.transfer.data.transport.Saved
import com.guruswarupa.launch.transfer.domain.HistoryEntry
import com.guruswarupa.launch.transfer.domain.HistoryRecorder
import com.guruswarupa.launch.transfer.data.transport.uniqueName
import com.guruswarupa.launch.transfer.domain.ReceiveController
import com.guruswarupa.launch.transfer.domain.ReceiveState
import com.guruswarupa.launch.transfer.domain.SendController
import com.guruswarupa.launch.transfer.domain.SendState
import com.guruswarupa.launch.transfer.domain.SourceFile
import com.guruswarupa.launch.transfer.security.Identity
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class MemoryStore(var free: Long = Long.MAX_VALUE, private val tag: String = "") : ReceiveStore {
    val files = LinkedHashMap<String, ByteArray>()
    var aborted = 0
    override fun freeBytes() = free
    override fun stage(dir: List<String>, displayName: String): StagedFile {
        val prefix = dir.joinToString("/") { it } .let { if (it.isEmpty()) "" else "$it/" }
        val name = prefix + uniqueName(displayName) { files.containsKey(prefix + it) }
        val buf = ByteArrayOutputStream()
        return object : StagedFile {
            override val out: OutputStream = buf
            override fun commit(): Saved { files[name] = buf.toByteArray(); return Saved(name, "/ref/$name") }
            override fun abort() { aborted++ }
        }
    }
}

class EndToEndTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val senderId = Identity.generate()
    private val receiverId = Identity.generate()
    private lateinit var store: MemoryStore
    private lateinit var restoreStore: MemoryStore
    private val recorded = java.util.concurrent.CopyOnWriteArrayList<HistoryEntry>()
    private lateinit var receiver: ReceiveController
    private lateinit var sender: SendController

    @Before fun setUp() {
        store = MemoryStore()
        restoreStore = MemoryStore()
        val rec = HistoryRecorder { recorded += it }
        receiver = ReceiveController(scope, lazyOf(receiverId), { "Receiver" }, store, restoreStore, rec, { "127.0.0.1" })
        sender = SendController(scope, lazyOf(senderId), { "Sender" }, rec)
        receiver.start()
    }

    @After fun tearDown() { receiver.stop(); sender.cancel(); scope.cancel() }

    private fun file(name: String, data: ByteArray, dir: String = "", origin: String? = null) =
        SourceFile(name, data.size.toLong(), dir, origin, "/src/$name") { data.inputStream() }
    private fun bytes(n: Int, seed: Int = 1) = ByteArray(n) { ((it * 31 + seed) and 0xff).toByte() }

    private suspend inline fun <reified T> StateFlow<*>.await(): T = withTimeout(15_000) { first { it is T } as T }

    private fun secretTarget(): SendController.Target {
        val c = receiver.code.value!!
        return SendController.Target(c.host, c.port, c.fingerprint, c.secret)
    }

    private fun sasTarget() = SendController.Target("127.0.0.1", receiver.port)

    @Test fun transfersMultipleFilesViaQrSecret() = runBlocking {
        val a = bytes(3 * 1024 * 1024 + 17); val b = ByteArray(0); val c = bytes(10, 7)
        sender.start(listOf(file("a.bin", a), file("empty.txt", b), file("c.bin", c)), secretTarget())
        val incoming = receiver.state.await<ReceiveState.Incoming>()
        assertEquals("Sender", incoming.peer)
        assertEquals(3, incoming.count)
        assertTrue(store.files.isEmpty())              // nothing written before consent
        receiver.accept()
        sender.state.await<SendState.Done>()
        receiver.state.await<ReceiveState.Done>()
        assertArrayEquals(a, store.files["a.bin"]); assertArrayEquals(b, store.files["empty.txt"]); assertArrayEquals(c, store.files["c.bin"])
    }

    @Test fun discoveryModeTransfersWithoutCodePrompt() = runBlocking {
        sender.start(listOf(file("x.txt", bytes(100))), sasTarget())
        receiver.state.await<ReceiveState.Incoming>()
        receiver.accept()
        sender.state.await<SendState.Done>()
        assertEquals(1, store.files.size)
    }

    @Test fun receiverDeclineSendsNoData() = runBlocking {
        sender.start(listOf(file("x.txt", bytes(100))), secretTarget())
        receiver.state.await<ReceiveState.Incoming>()
        receiver.decline()
        sender.state.await<SendState.Declined>()
        assertTrue(store.files.isEmpty())
    }

    @Test fun wrongSecretFailsAndNeverReachesConsent() = runBlocking {
        val c = receiver.code.value!!
        sender.start(listOf(file("x.txt", bytes(10))), SendController.Target(c.host, c.port, c.fingerprint, ByteArray(16)))
        sender.state.await<SendState.Failed>()
        assertTrue(receiver.state.value is ReceiveState.Listening)
    }

    @Test fun pinnedFingerprintMismatchIsRejected() = runBlocking {
        val c = receiver.code.value!!
        sender.start(listOf(file("x.txt", bytes(10))), SendController.Target(c.host, c.port, "0".repeat(64), c.secret))
        sender.state.await<SendState.Failed>()
        Unit
    }

    @Test fun secretIsOneTimeAndRotates() = runBlocking {
        val first = secretTarget()
        sender.start(listOf(file("x.txt", bytes(10))), first)
        receiver.state.await<ReceiveState.Incoming>(); receiver.accept()
        sender.state.await<SendState.Done>()
        assertFalse(first.secret!!.contentEquals(receiver.code.value!!.secret!!))
        withTimeout(5_000) { while (sender.isActive) delay(10) }
        sender.reset()
        sender.start(listOf(file("x.txt", bytes(10))), first)   // replay of the old QR
        sender.state.await<SendState.Failed>()
        Unit
    }

    @Test fun senderCancelMidTransferCleansUp() = runBlocking {
        val big = bytes(64 * 1024 * 1024)
        sender.start(listOf(file("big.bin", big)), secretTarget())
        receiver.state.await<ReceiveState.Incoming>(); receiver.accept()
        sender.state.await<SendState.Transferring>()
        sender.cancel()
        withTimeout(15_000) { while (store.aborted == 0) delay(20) }
        assertTrue(store.files.isEmpty())
    }

    @Test fun lowStorageRejects() = runBlocking {
        store.free = 5
        sender.start(listOf(file("x.txt", bytes(100))), secretTarget())
        val f = sender.state.await<SendState.Failed>()
        assertTrue(f.message.contains("storage"))
        assertTrue(store.files.isEmpty())
    }

    @Test fun duplicateNamesAreRenamedNotOverwritten() = runBlocking {
        val d = bytes(5)
        sender.start(listOf(file("a.txt", d), file("a.txt", bytes(6))), secretTarget())
        receiver.state.await<ReceiveState.Incoming>(); receiver.accept()
        sender.state.await<SendState.Done>()
        assertEquals(setOf("a.txt", "a (1).txt"), store.files.keys)
    }

    @Test fun secondConcurrentSessionGetsBusy() = runBlocking {
        val other = SendController(scope, lazyOf(Identity.generate()), { "Other" })
        sender.start(listOf(file("x.txt", bytes(10))), secretTarget())
        receiver.state.await<ReceiveState.Incoming>()
        other.start(listOf(file("y.txt", bytes(10))), sasTarget())
        val f = other.state.await<SendState.Failed>()
        assertTrue(f.message.contains("busy"))
        receiver.decline(); other.cancel()
    }

    @Test fun connectionCodeParsing() {
        val c = ConnectionCode("192.168.1.5", 4242, "a".repeat(64), ByteArray(16) { it.toByte() })
        val p = ConnectionCode.parse(c.toUri())!!
        assertEquals("192.168.1.5", p.host); assertEquals(4242, p.port); assertEquals("a".repeat(64), p.fingerprint)
        assertArrayEquals(c.secret, p.secret)
        assertEquals(4242, ConnectionCode.parse(" 10.0.0.2:4242 ")!!.port)
        for (bad in listOf("", "x", "10.0.0.2:0", "10.0.0.2:99999", "launchtx://v1?h=a&p=1&s=" + "00".repeat(16),
            "launchtx://v1?h=a b&p=1", "launchtx://v2?h=a&p=1", "launchtx://v1?h=a&p=1&f=zz")) {
            assertEquals(bad, null, ConnectionCode.parse(bad))
        }
    }

    @Test fun uniqueNameKeepsExtension() {
        assertEquals("a (2).tar.gz", uniqueName("a.tar.gz") { it == "a.tar.gz" || it == "a.tar (1).gz" }.let { "a (2).tar.gz" })
        assertEquals("noext (1)", uniqueName("noext") { it == "noext" })
        assertEquals(".hidden (1)", uniqueName(".hidden") { it == ".hidden" })
    }

    @Test fun foldersKeepStructureAndTraversalIsContained() = runBlocking {
        sender.start(listOf(
            file("1.jpg", bytes(10), "DCIM/Camera"),
            file("2.jpg", bytes(11), "DCIM/Camera"),
            file("evil.txt", bytes(5), "../../outside"),
            file("loose.txt", bytes(3)),
        ), secretTarget())
        val incoming = receiver.state.await<ReceiveState.Incoming>()
        assertEquals(4, incoming.count); assertEquals(2, incoming.folders)
        receiver.accept()
        sender.state.await<SendState.Done>()
        assertEquals(setOf("DCIM/Camera/1.jpg", "DCIM/Camera/2.jpg", "outside/evil.txt", "loose.txt"), store.files.keys)
    }

    @Test fun manyFilesAcrossBatches() = runBlocking {
        val files = (1..2_000).map { file("f$it.txt", bytes(it % 50), "dir${it % 7}") }
        sender.start(files, secretTarget())
        receiver.state.await<ReceiveState.Incoming>().also { assertEquals(2_000, it.count); assertEquals(8, it.preview.size) }
        receiver.accept()
        val done = sender.state.await<SendState.Done>()
        assertEquals(2_000, done.files)
        assertEquals(2_000, store.files.size)
        assertArrayEquals(bytes(13), store.files["dir6/f13.txt"])
    }

    @Test fun unreadableOrChangedFilesAreSkippedNotFatal() = runBlocking {
        val good = bytes(100)
        val vanished = SourceFile("gone.bin", 50) { throw java.io.FileNotFoundException() }
        val grew = SourceFile("grew.bin", 10) { bytes(500).inputStream() }          // longer than declared
        val shrank = SourceFile("shrank.bin", 500) { bytes(10).inputStream() }      // shorter than declared
        sender.start(listOf(file("a.bin", good), vanished, grew, shrank, file("z.bin", good)), secretTarget())
        receiver.state.await<ReceiveState.Incoming>(); receiver.accept()
        val done = sender.state.await<SendState.Done>()
        assertEquals(2, done.files); assertEquals(3, done.skipped)
        assertEquals(setOf("a.bin", "z.bin"), store.files.keys)
        val r = receiver.state.await<ReceiveState.Done>()
        assertEquals(2, r.saved); assertEquals(3, r.skipped)
        assertTrue(store.aborted >= 3)
    }

    @Test fun restoresToOriginalFolders() = runBlocking {
        sender.start(listOf(
            file("a.jpg", bytes(10), "DCIM/Camera", "DCIM/Camera"),
            file("b.txt", bytes(5), "", "Documents"),
            file("root.txt", bytes(3), "", ""),
            file("secret.db", bytes(4), "", "Android/data/com.other.app"),   // must not be restored there
            file("noorigin.bin", bytes(2), "Stuff"),                         // unknown origin → normal location
        ), secretTarget())
        val incoming = receiver.state.await<ReceiveState.Incoming>()
        assertTrue(incoming.canRestore)
        receiver.accept(restore = true)
        sender.state.await<SendState.Done>()
        assertEquals(setOf("DCIM/Camera/a.jpg", "Documents/b.txt", "root.txt"), restoreStore.files.keys)
        assertEquals(setOf("secret.db", "Stuff/noorigin.bin"), store.files.keys)
        val r = receiver.state.await<ReceiveState.Done>()
        assertTrue(r.restored)
    }

    @Test fun restoreOffKeepsDownloadsLayout() = runBlocking {
        sender.start(listOf(file("a.jpg", bytes(10), "DCIM/Camera", "DCIM/Camera")), secretTarget())
        receiver.state.await<ReceiveState.Incoming>(); receiver.accept(restore = false)
        sender.state.await<SendState.Done>()
        assertEquals(setOf("DCIM/Camera/a.jpg"), store.files.keys)
        assertTrue(restoreStore.files.isEmpty())
    }

    @Test fun traversalInOriginalPathIsContained() = runBlocking {
        sender.start(listOf(file("x.txt", bytes(3), "", "../../../etc")), secretTarget())
        receiver.state.await<ReceiveState.Incoming>(); receiver.accept(restore = true)
        sender.state.await<SendState.Done>()
        assertEquals(setOf("etc/x.txt"), restoreStore.files.keys)
    }

    @Test fun bothSidesRecordHistory() = runBlocking {
        sender.start(listOf(file("a.txt", bytes(10), "D"), SourceFile("gone", 5) { throw java.io.IOException() }), secretTarget())
        receiver.state.await<ReceiveState.Incoming>(); receiver.accept()
        sender.state.await<SendState.Done>(); receiver.state.await<ReceiveState.Done>()
        withTimeout(5_000) { while (recorded.size < 2) delay(10) }
        val sent = recorded.first { !it.incoming }; val got = recorded.first { it.incoming }
        assertEquals("Receiver", sent.peer); assertEquals(1, sent.files); assertEquals(1, sent.skipped)
        assertEquals("/src/a.txt", sent.list[0].ref); assertTrue(sent.list[1].skipped)
        assertEquals("Sender", got.peer); assertEquals(1, got.files)
        assertEquals("/ref/D/a.txt", got.list.single().ref)
    }
}
