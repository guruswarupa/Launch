package com.guruswarupa.launch.transfer.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guruswarupa.launch.transfer.platform.DiskItem
import com.guruswarupa.launch.transfer.platform.storageRoot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class Entry(val file: File, val isDir: Boolean, val size: Long)

/** In-app browser of shared storage with checkboxes, so whole folders — or the entire phone — can be ticked at once. */
@Composable
fun BrowseScreen(vm: AppViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val access by vm.allFilesAccess.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val root = remember { storageRoot() }
    var path by rememberSaveable { mutableStateOf(root.path) }
    val dir = File(path)

    LifecycleResumeEffect(Unit) { vm.refreshAccess(); onPauseOrDispose {} }

    val requestAccess = rememberAllFilesRequest(vm)

    BackHandler {
        if (access && dir != root) path = dir.parentFile?.path ?: root.path else onDone()
    }

    if (!access) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenTitle("Browse phone")
            StatusCard(
                "Allow access to all files",
                "To tick whole folders or your entire phone (for example when moving to a new phone), Launch needs access to files on this device. " +
                    "It only reads what you tick, and only sends it to the device you choose on your local network.",
            )
            LButton(requestAccess, Modifier.fillMaxWidth()) { Text("Allow access") }
            LOutlinedButton(onDone, Modifier.fillMaxWidth()) { Text("Back") }
        }
        return
    }

    val entries by produceState<List<Entry>?>(null, path, items) {
        value = withContext(Dispatchers.IO) {
            dir.listFiles()?.map { Entry(it, it.isDirectory, if (it.isFile) it.length() else 0) }
                ?.sortedWith(compareBy({ !it.isDir }, { it.file.name.lowercase() }))
                ?: emptyList()
        }
    }
    val total = items.sumOf { stats[it.id]?.files ?: 0 }
    val totalBytes = items.sumOf { stats[it.id]?.bytes ?: 0L }
    val counting = items.any { stats[it.id]?.loading == true }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ScreenTitle("Browse phone")
            Text(
                (if (dir == root) "Phone storage" else "Phone storage" + dir.path.removePrefix(root.path)),
                style = MaterialTheme.typography.bodySmall, maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dir != root) LOutlinedButton({ path = dir.parentFile?.path ?: root.path }) { Text("Up") }
                LButton({ vm.selectAllIn(dir) }) { Text("Select all") }
                LOutlinedButton({ vm.clearItems() }, enabled = items.isNotEmpty()) { Text("Clear") }
            }
        }
        val list = entries
        Surface(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = LaunchShape,
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            if (list == null) item { Text("Loading…", Modifier.padding(16.dp)) }
            else if (list.isEmpty()) item { Text("This folder is empty or can't be read.", Modifier.padding(16.dp)) }
            else items(list, key = { it.file.path }) { e ->
                val checked = vm.isSelected(e.file)
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { if (e.isDir) path = e.file.path else vm.toggleDisk(e.file) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked, { vm.toggleDisk(e.file) },
                        modifier = Modifier.semantics { contentDescription = "Select ${e.file.name}" },
                    )
                    Column(Modifier.weight(1f)) {
                        Text((if (e.isDir) "📁 " else "") + e.file.name, maxLines = 1)
                        Text(if (e.isDir) "Folder" else size(e.size), style = MaterialTheme.typography.bodySmall)
                    }
                    if (e.isDir) Text("›", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 12.dp))
                }
            }
        }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (items.isEmpty()) "Nothing selected"
                else "${if (counting) "Counting… " else ""}$total file(s) · ${size(totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            LButton(onDone) { Text("Done") }
        }
    }
}
