package com.guruswarupa.launch.transfer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.theme.ThemeManager
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Screen { Home, Send, Receive, History, HistoryDetail }

class TransferActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Same transparent system bars as Launch's settings screens.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LaunchTransferTheme {
                // Transparent surface whose only job is to set the default text colour from Launch's theme.
                Surface(Modifier.fillMaxSize(), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
                    Box(Modifier.fillMaxSize()) { Backdrop(); App() }
                }
            }
        }
    }
}

/** The user's wallpaper under a scrim, exactly like the settings screens. */
@Composable
private fun Backdrop() {
    val context = LocalContext.current
    val scrim = remember(context) {
        val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val alpha = (prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40) * 255 / 100).coerceIn(0, 255)
        val base = ThemeManager.color(context, R.attr.appScrim)
        Color(android.graphics.Color.argb(alpha, android.graphics.Color.red(base), android.graphics.Color.green(base), android.graphics.Color.blue(base)))
    }
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; WallpaperDisplayHelper.applySystemWallpaper(this) } },
        modifier = Modifier.fillMaxSize(),
    )
    Box(Modifier.fillMaxSize().background(scrim))
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    var detailId by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) {
        vm.startMonitoring()
        onDispose { vm.stopMonitoring() }
    }

    BackHandler(enabled = screen != Screen.Home) {
        if (screen == Screen.Send) {
            if (vm.send.isActive) vm.send.cancel()
            vm.send.reset()
        }
        screen = if (screen == Screen.HistoryDetail) Screen.History else Screen.Home
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (screen) {
            Screen.Home -> HomeScreen(vm, { screen = Screen.Send }, { screen = Screen.Receive }, { screen = Screen.History })
            Screen.Send -> SendScreen(vm)
            Screen.Receive -> ReceiveScreen(vm)
            Screen.History -> HistoryScreen(vm) { detailId = it; screen = Screen.HistoryDetail }
            Screen.HistoryDetail -> HistoryDetailScreen(vm, detailId)
        }
    }
}

@Composable
private fun HomeScreen(vm: AppViewModel, onSend: () -> Unit, onReceive: () -> Unit, onHistory: () -> Unit) {
    val receiving by vm.receiving.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("File Transfer")
        Text(
            "Move files and folders between phones on the same Wi-Fi network. No account, no cloud.",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionHeader("Transfer")
        LaunchCard {
            ActionRow("Send files", "Choose files or folders, or your whole phone", onSend)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow(if (receiving) "Receive files · on" else "Receive files", "Let nearby devices send you files", onReceive)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow("History", "Files you sent and received", onHistory)
        }
    }
}

@Composable
fun KeepScreenOn(keepOn: Boolean) {
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
}
