package com.shelf.launcher.ui

import androidx.activity.enableEdgeToEdge
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.PinnedShortcuts
import com.shelf.launcher.ui.theme.ShelfLauncherTheme
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * "Add to home screen" from another app (DroidDeck's game shortcuts, a browser, an emulator…): Android asks the
 * home app — Voltcap — to confirm. A small card: Add to Voltcap / Cancel. Added shortcuts become games in a
 * category named after the app ([PinnedShortcuts]). Widgets aren't supported (cancelled).
 */
class PinShortcutActivity : ComponentActivity() {
    private var request: LauncherApps.PinItemRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        com.shelf.launcher.CrashGuard.install(this)
        super.onCreate(savedInstanceState)
        // Update 136: drawn edge to edge like Voltcap's home, with the bars hidden from the start. Without it Android
        // painted a black navigation-bar strip along the bottom while this window was open, even with the bar hidden.
        enableEdgeToEdge()
        runCatching { SystemBars.apply(window) }
        com.shelf.launcher.data.Features.init(this)
        if (Build.VERSION.SDK_INT < 26) { finish(); return }
        val apps = getSystemService(LauncherApps::class.java)
        val req = runCatching { apps?.getPinItemRequest(intent) }.getOrNull()
        val info = req?.shortcutInfo
        if (req == null || !req.isValid || req.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || info == null) {
            finish(); return
        }
        request = req
        val label = (info.longLabel ?: info.shortLabel)?.toString()?.takeIf { it.isNotBlank() } ?: info.id
        val appName = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.`package`, 0)).toString() }
            .getOrDefault(info.`package`)
        val theme = runCatching { com.shelf.launcher.data.ThemeStore(this).load() }.getOrNull()
        val appearance = runCatching { com.shelf.launcher.data.AppearanceStore(this).load() }.getOrNull()
        val add = {
            // accept() pins it for us (the app sees it as added); then it's a game in the library.
            if (runCatching { req.accept() }.getOrDefault(false)) {
                PinnedShortcuts.add(this, PinnedShortcuts.Entry(info.`package`, info.id, label))
            }
            finish()
        }
        setContent {
            val body: @Composable () -> Unit = { Card(label, appName, onAdd = add, onCancel = { finish() }) }
            if (theme != null && appearance != null) ShelfLauncherTheme(appTheme = theme, appearance = appearance) { body() }
            else ShelfLauncherTheme { body() }
        }
    }

    // Update 130: Hide navigation bar applies to this window too (it used to bring the bar back while open).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) runCatching { SystemBars.apply(window) }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    @Composable
    private fun Card(label: String, appName: String, onAdd: () -> Unit, onCancel: () -> Unit) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).pointerInput(Unit) { detectTapGestures { onCancel() } },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.widthIn(max = 420.dp).padding(16.dp).clip(uiRounded(18.dp))
                    .background(ShelfTheme.colors.surface.copy(alpha = 0.97f))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(18.dp)
            ) {
                Text("Add to Voltcap", color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = headingFont())
                Spacer(Modifier.height(6.dp))
                Text(label, color = ShelfTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("A shortcut from $appName. It shows up as a game under \"$appName\" and opens that game directly.", color = ShelfTheme.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    Chip("Cancel", onClick = onCancel)
                    Chip("Add", accent = true, onClick = onAdd)
                }
            }
        }
    }
}
