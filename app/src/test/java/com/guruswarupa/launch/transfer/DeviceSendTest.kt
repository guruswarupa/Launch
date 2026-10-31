package com.guruswarupa.launch.transfer

import com.guruswarupa.launch.transfer.domain.SendController
import com.guruswarupa.launch.transfer.domain.SendState
import com.guruswarupa.launch.transfer.domain.SourceFile
import com.guruswarupa.launch.transfer.security.Identity
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Manual harness: sends a file from this machine to a real device. Skipped unless LAUNCH_TX_TARGET=host:port. */
class DeviceSendTest {
    @Test fun sendToDevice() {
        val target = System.getenv("LAUNCH_TX_TARGET")
        assumeTrue(target != null)
        val out = File(System.getenv("LAUNCH_TX_OUT") ?: "/tmp/launch-tx-device-test.log")
        fun log(s: String) { out.appendText(s + "\n") }
        val (host, port) = target!!.split(":")
        val data = ByteArray(5 * 1024 * 1024) { ((it * 131) xor (it shr 8)).toByte() }
        log("sha256=" + MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val c = SendController(scope, lazyOf(Identity.generate()), { "Laptop" })
        c.start(listOf(SourceFile("launch-tx-test.bin", data.size.toLong()) { data.inputStream() }), SendController.Target(host, port.toInt()))
        runBlocking {
            val end = withTimeout(180_000) { c.state.first { it is SendState.Done || it is SendState.Failed || it is SendState.Declined || it is SendState.Cancelled } }
            log("final=$end")
        }
    }
}
