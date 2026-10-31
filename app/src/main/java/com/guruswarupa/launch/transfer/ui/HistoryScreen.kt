package com.guruswarupa.launch.transfer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guruswarupa.launch.transfer.domain.HistoryEntry
import com.guruswarupa.launch.transfer.domain.HistoryFile
import java.io.File
import java.text.DateFormat
import java.util.Date

private fun whenText(t: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))

private fun title(e: HistoryEntry) = if (e.incoming) "Received from ${e.peer}" else "Sent to ${e.peer}"

@Composable
private fun ConfirmDialog(text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant, shape = LaunchShape,
        text = { Text(text) },
        confirmButton = { LTextButton({ onDismiss(); onConfirm() }) { Text(confirmLabel) } },
        dismissButton = { LTextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun HistoryScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val entries by vm.history.entries.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) ConfirmDialog("Clear the whole history? Your files are not deleted.", "Clear", { vm.history.clear() }) { confirmClear = false }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            ScreenTitle("History")
            if (entries.isNotEmpty()) LTextButton({ confirmClear = true }) { Text("Clear all") }
        }
        Text("Stored only on this phone. Shows the files you sent and received.", style = MaterialTheme.typography.bodySmall)
        if (entries.isEmpty()) Text("No transfers yet.", Modifier.padding(top = 16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.id }) { e ->
                ItemCard(onClick = { onOpen(e.id) }) {
                    Text((if (e.incoming) "↓ " else "↑ ") + title(e), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${whenText(e.time)} · ${e.files} file(s) · ${size(e.bytes)}" + if (e.skipped > 0) " · ${e.skipped} skipped" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(vm: AppViewModel, id: String) {
    val entries by vm.history.entries.collectAsStateWithLifecycle()
    val entry = entries.firstOrNull { it.id == id }
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }
    if (entry == null) { Text("This entry was removed.", Modifier.padding(16.dp)); return }
    if (confirmRemove) ConfirmDialog("Remove this entry from history? The files are not deleted.", "Remove", { vm.history.delete(entry.id) }) { confirmRemove = false }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title(entry), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        Text(
            "${whenText(entry.time)} · ${entry.files} file(s) · ${size(entry.bytes)}" +
                (if (entry.restored) " · put back in original folders" else "") +
                (if (entry.skipped > 0) " · ${entry.skipped} skipped" else ""),
            style = MaterialTheme.typography.bodySmall,
        )
        LOutlinedButton({ confirmRemove = true }) { Text("Remove from history") }
        LazyColumn(Modifier.fillMaxSize()) {
            items(entry.list) { f ->
                Column(Modifier.fillMaxWidth().clickable(enabled = f.ref != null && !f.skipped) { openFile(context, f) }.padding(vertical = 8.dp)) {
                    Text(f.name, maxLines = 1)
                    Text(
                        (if (f.dir.isNotEmpty()) f.dir + "/ · " else "") + size(f.size) + if (f.skipped) " · skipped" else "",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    )
                }
            }
            if (entry.notListed > 0) item { Text("+ ${entry.notListed} more file(s) not listed", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Opens a file from history in whatever app handles its type. */
private fun openFile(context: Context, f: HistoryFile) {
    val ref = f.ref ?: return
    try {
        val uri = if (ref.startsWith("content:")) Uri.parse(ref) else {
            val file = File(ref)
            if (!file.exists()) { Toast.makeText(context, "That file no longer exists.", Toast.LENGTH_SHORT).show(); return }
            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.name.substringAfterLast('.', "").lowercase()) ?: "*/*"
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't open the file (it may have been moved or deleted).", Toast.LENGTH_SHORT).show()
    }
}
