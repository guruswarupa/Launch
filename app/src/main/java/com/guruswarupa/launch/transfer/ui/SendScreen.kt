package com.guruswarupa.launch.transfer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guruswarupa.launch.transfer.domain.SendState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun SendScreen(vm: AppViewModel) {
    val state by vm.send.state.collectAsStateWithLifecycle()
    if (state !is SendState.Idle) {
        SendProgress(vm, state)
        return
    }
    val items by vm.items.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val peers by vm.discovery.peers.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var showCodeDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        vm.startBrowsing()
        onDispose { vm.stopBrowsing() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addFiles(it) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::addTree) }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let(vm::sendToCode) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("Send files") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LOutlinedButton({ picker.launch(arrayOf("*/*")) }, Modifier.weight(1f)) { Text("Add files") }
                LOutlinedButton({ folderPicker.launch(null) }, Modifier.weight(1f)) { Text("Add folder") }
            }
        }
        items(items, key = { it.id }) { item ->
            val st = stats[item.id]
            ItemCard { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((if (item.isFolder) "📁 " else "") + item.label, maxLines = 1)
                    Text(
                        when {
                            st == null || st.loading -> "Counting…"
                            item.isFolder -> "${st.files} file(s) · ${size(st.bytes)}"
                            else -> size(st.bytes)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LTextButton({ vm.removeItem(item) }) { Text("Remove") }
            } }
        }
        if (items.isNotEmpty()) {
            item {
                val files = items.sumOf { stats[it.id]?.files ?: 0 }
                val bytes = items.sumOf { stats[it.id]?.bytes ?: 0L }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("$files file(s) · ${size(bytes)} total", style = MaterialTheme.typography.titleSmall)
                    LTextButton({ vm.clearItems() }) { Text("Clear all") }
                }
            }
        }
        item { SectionHeader("Nearby devices") }
        if (peers.isEmpty()) {
            item {
                Text(
                    "Looking for devices that are receiving on this Wi-Fi network. If none appear, scan their QR code or enter their code.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(peers, key = { it.key }) { p ->
            ItemCard(onClick = { vm.sendTo(p) }) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text("#${p.tag} · ${p.host}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LOutlinedButton({
                    scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(false).setPrompt("Scan the QR code on the receiving device"))
                }, Modifier.weight(1f)) { Text("Scan QR") }
                LOutlinedButton({ showCodeDialog = true }, Modifier.weight(1f)) { Text("Enter code") }
            }
        }
        notice?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
    }

    if (showCodeDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCodeDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant, shape = LaunchShape,
            title = { Text("Enter connection code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Type the address shown on the receiving device (for example 192.168.1.20:41234).")
                    OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Address or code") })
                }
            },
            confirmButton = { LTextButton({ showCodeDialog = false; vm.sendToCode(text) }) { Text("Connect") } },
            dismissButton = { LTextButton({ showCodeDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SendProgress(vm: AppViewModel, state: SendState) {
    KeepScreenOn(vm.send.isActive)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Sending")
        when (state) {
            SendState.Preparing -> StatusCard("Reading files…", "Collecting everything you selected.")
            SendState.Connecting -> StatusCard("Connecting…")
            is SendState.WaitingForReceiver -> StatusCard("Waiting for ${state.peer} to accept…", "Nothing is sent until they accept.")
            is SendState.Transferring -> StatusCard("Sending to ${state.peer}") { ProgressBlock(state.progress, state.bytesPerSecond) }
            is SendState.Done -> StatusCard("✓ Sent to ${state.peer}", "${state.files} file(s), ${size(state.bytes)}. The receiver verified every file." + if (state.skipped > 0) " ${state.skipped} file(s) were skipped because they changed or couldn't be read." else "")
            is SendState.Declined -> StatusCard("${state.peer} declined the transfer", "No files were sent.")
            is SendState.Failed -> StatusCard("Transfer failed", state.message)
            SendState.Cancelled -> StatusCard("Transfer cancelled", "No complete files were kept unless already marked received.")
            SendState.Idle -> {}
        }
        when (state) {
            is SendState.Done, is SendState.Declined, SendState.Cancelled -> LButton({ vm.finishSend() }, Modifier.fillMaxWidth()) { Text("Done") }
            is SendState.Failed -> ButtonRow("Try again", { vm.send.retry() }, "Back", { vm.finishSend() })
            else -> LOutlinedButton({ vm.send.cancel() }, Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
