package com.guruswarupa.launch.transfer.ui

import android.content.Context
import androidx.annotation.AttrRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.theme.ThemeManager

/** Corner radius used by every Launch card and button. */
val LaunchShape = RoundedCornerShape(24.dp)

private fun Context.attr(@AttrRes id: Int): Color = Color(ThemeManager.color(this, id))

/** Builds the Material colour scheme from Launch's own theme attributes, so palette/accent changes carry over. */
private fun launchColorScheme(context: Context): ColorScheme {
    val base = if (ThemeManager.isLight(context)) lightColorScheme() else darkColorScheme()
    val text = context.attr(R.attr.appTextPrimary)
    val accent = context.attr(R.attr.appAccent)
    return base.copy(
        primary = accent,
        onPrimary = context.attr(R.attr.appOnAccent),
        primaryContainer = context.attr(R.attr.appSurfaceVariant),
        onPrimaryContainer = text,
        secondary = context.attr(R.attr.appAccentMuted),
        secondaryContainer = context.attr(R.attr.appSurfaceVariant),
        onSecondaryContainer = text,
        background = Color.Transparent,
        onBackground = text,
        surface = context.attr(R.attr.appSurface),
        onSurface = text,
        surfaceVariant = context.attr(R.attr.appSurfaceElevated),
        onSurfaceVariant = context.attr(R.attr.appTextSecondary),
        outline = context.attr(R.attr.appOutline),
        outlineVariant = context.attr(R.attr.appDivider),
        error = context.attr(R.attr.appError),
    )
}

@Composable
fun LaunchTransferTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = remember(context) { launchColorScheme(context) }
    MaterialTheme(colorScheme = scheme, content = content)
}

// Buttons in Launch's settings style: 52dp tall, 24dp corners, outlined surface; the accent fill is for the main action.

@Composable
fun LButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(
        onClick, modifier.heightMin(), enabled = enabled, shape = LaunchShape,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        content = content,
    )
}

@Composable
fun LOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(
        onClick, modifier.heightMin(), enabled = enabled, shape = LaunchShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
        content = content,
    )
}

@Composable
fun LTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    TextButton(
        onClick, modifier, enabled = enabled, shape = LaunchShape,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        content = content,
    )
}

private fun Modifier.heightMin() = this.then(Modifier.defaultMinSize(minHeight = 52.dp))
