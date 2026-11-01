package com.guruswarupa.launch.transfer.ui

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guruswarupa.launch.transfer.data.protocol.FileMeta
import com.guruswarupa.launch.transfer.data.transport.Progress

@Composable
fun size(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** A Launch-style card: translucent surface, 1dp outline, 24dp corners. */
@Composable
fun LaunchCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = LaunchShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun StatusCard(title: String, body: String? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    LaunchCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** One list entry (a device, a selected folder, a history item) as its own outlined card. */
@Composable
fun ItemCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val inner: @Composable () -> Unit = { Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), content = content) }
    if (onClick == null) {
        Surface(Modifier.fillMaxWidth(), shape = LaunchShape, color = colors.surface, border = BorderStroke(1.dp, colors.outline), content = inner)
    } else {
        Surface(onClick, Modifier.fillMaxWidth(), shape = LaunchShape, color = colors.surface, border = BorderStroke(1.dp, colors.outline), content = inner)
    }
}

/** Small accent heading used above groups, like the section labels in Launch settings. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(start = 12.dp, top = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.8.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

/** A tappable row with a title, a one-line description and a chevron. */
@Composable
fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(LaunchShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProgressBlock(p: Progress, bytesPerSecond: Long) {
    val fraction = if (p.totalBytes == 0L) 1f else (p.doneBytes.toFloat() / p.totalBytes).coerceIn(0f, 1f)
    val percent = (fraction * 100).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Transfer $percent percent complete" },
        )
        Text("$percent% · ${size(p.doneBytes)} of ${size(p.totalBytes)}", style = MaterialTheme.typography.bodyMedium)
        Text("File ${p.fileIndex + 1} of ${p.fileCount}: ${p.fileName}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        if (bytesPerSecond > 0) Text("${size(bytesPerSecond)}/s", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FileList(files: List<FileMeta>, count: Int = files.size) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (f in files) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text((if (f.dir.isNotEmpty()) f.dir + "/" else "") + f.name, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                Text(size(f.size), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (count > files.size) Text("+ ${count - files.size} more files", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CodeBox(code: String) {
    Text(
        code,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { contentDescription = "Verification code ${code.replace(" ", ", ")}" },
    )
}

@Composable
fun ButtonRow(primaryLabel: String, onPrimary: () -> Unit, secondaryLabel: String, onSecondary: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LOutlinedButton(onSecondary, Modifier.weight(1f)) { Text(secondaryLabel) }
        LButton(onPrimary, Modifier.weight(1f)) { Text(primaryLabel) }
    }
}
