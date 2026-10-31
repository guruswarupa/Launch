package com.guruswarupa.launch.transfer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guruswarupa.launch.transfer.domain.ReceiveState
import com.guruswarupa.launch.transfer.platform.qrBitmap

@Composable
fun ReceiveScreen(vm: AppViewModel) {
    val on by vm.receiving.collectAsStateWithLifecycle()
    val state by vm.receive.state.collectAsStateWithLifecycle()
    val code by vm.receive.code.collectAsStateWithLifecycle()
    val wifi by vm.wifi.collectAsStateWithLifecycle()
    KeepScreenOn(state is ReceiveState.Receiving || state is ReceiveState.Incoming)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Receive files")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(if (on) "Receiving is on" else "Receiving is off", style = MaterialTheme.typography.titleMedium)
                Text("This device is discoverable on your Wi-Fi network only while receiving is on.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(on, { vm.setReceiving(it) }, modifier = Modifier.semantics { stateDescription = if (on) "On" else "Off" })
        }
        if (on && !wifi) StatusCard("No Wi-Fi network", "Connect both devices to the same Wi-Fi network (or turn on a hotspot) to transfer.")

        when (val s = state) {
            ReceiveState.Off -> {}
            ReceiveState.Listening -> ListeningCard(code?.toUri(), code?.let { "${it.host}:${it.port}" })
            is ReceiveState.Incoming -> IncomingCard(vm, s)
            is ReceiveState.Receiving -> StatusCard("Receiving from ${s.peer}") {
                ProgressBlock(s.progress, s.bytesPerSecond)
                LOutlinedButton({ vm.receive.cancelTransfer() }, Modifier.fillMaxWidth()) { Text("Cancel") }
            }
            is ReceiveState.Done -> StatusCard("✓ Received ${s.saved} file(s) from ${s.peer}", "${size(s.bytes)}, verified. " + (if (s.restored) "Put back in their original folders." else "Saved in Downloads/Launch.") + if (s.skipped > 0) " ${s.skipped} file(s) were skipped by the sender." else "") {
                LButton({ vm.receive.acknowledge() }, Modifier.fillMaxWidth()) { Text("Done") }
            }
            is ReceiveState.Failed -> StatusCard("Transfer failed", s.message) {
                LButton({ vm.receive.acknowledge() }, Modifier.fillMaxWidth()) { Text("OK") }
            }
        }
    }
}

@Composable
private fun ListeningCard(uri: String?, address: String?) {
    StatusCard("Ready to receive", "Ask the sender to pick this device in their list, or scan this code.") {
        if (uri != null) {
            val bitmap = remember(uri) { qrBitmap(uri).asImageBitmap() }
            Image(
                bitmap, "QR code for connecting to this device",
                Modifier.size(240.dp).align(Alignment.CenterHorizontally),
            )
            SelectionContainer { Text("Code: $address", style = MaterialTheme.typography.bodyMedium) }
        } else {
            Text("Not connected to a local network yet.")
        }
        Text("Only accept files from people you know.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun IncomingCard(vm: AppViewModel, s: ReceiveState.Incoming) {
    val access by vm.allFilesAccess.collectAsStateWithLifecycle()
    val requestAccess = rememberAllFilesRequest(vm)
    LifecycleResumeEffect(Unit) { vm.refreshAccess(); onPauseOrDispose {} }
    var restore by remember { mutableStateOf(true) }
    StatusCard("${s.peer} wants to send you files", "${s.count} file(s)" + (if (s.folders > 0) " in ${s.folders} folder(s)" else "") + ", ${size(s.total)} total.") {
        FileList(s.preview, s.count)
        if (s.canRestore) {
            if (access) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(restore, { restore = it })
                    Text("Put files back in their original folders", Modifier.weight(1f))
                }
                Text(
                    if (restore) "Existing files are never overwritten; duplicates get a number." else "Files will go to Downloads/Launch.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("To put files back in their original folders, allow access to all files. Otherwise they go to Downloads/Launch.", style = MaterialTheme.typography.bodySmall)
                LOutlinedButton(requestAccess, Modifier.fillMaxWidth()) { Text("Allow access to all files") }
            }
        }
        ButtonRow("Accept", { vm.receive.accept(restore = s.canRestore && access && restore) }, "Reject", { vm.receive.decline() })
    }
}
