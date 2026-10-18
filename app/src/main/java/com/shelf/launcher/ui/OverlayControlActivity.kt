package com.shelf.launcher.ui

import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.perf.PerfSettings
import com.shelf.launcher.ui.theme.ShelfLauncherTheme
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * The in-game quick panel: what the overlays show, their size and opacity, moving them, and the power
 * tier — reachable without going back to Voltcap. Opens from a long press on the Voltcap overlay / power
 * Quick Settings tiles (Android's tile settings) and from a tap on the in-game notification. A small card
 * over the game; tap outside, Done or B to close it. Every change applies to the running overlays at once.
 */
class OverlayControlActivity : ComponentActivity() {
    /** (Update 241, #82) Opened by the overlay tile only to start the overlays (no panel): see [onResume]. */
    private var startHop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        com.shelf.launcher.CrashGuard.install(this)
        super.onCreate(savedInstanceState)
        startHop = savedInstanceState == null && intent?.getBooleanExtra(EXTRA_START_OVERLAYS, false) == true
        if (startHop) return
        // Update 136: drawn edge to edge like Voltcap's home, with the bars hidden from the start. Without it Android
        // painted a black navigation-bar strip along the bottom while this window was open, even with the bar hidden.
        enableEdgeToEdge()
        runCatching { SystemBars.apply(window) }
        com.shelf.launcher.data.Features.init(this)
        PerfSettings.config(this)
        com.shelf.launcher.device.SystemSync.start(this)
        val theme = runCatching { com.shelf.launcher.data.ThemeStore(this).load() }.getOrNull()
        val appearance = runCatching { com.shelf.launcher.data.AppearanceStore(this).load() }.getOrNull()
        setContent {
            if (theme != null && appearance != null) ShelfLauncherTheme(appTheme = theme, appearance = appearance) { Panel { finish() } }
            else ShelfLauncherTheme { Panel { finish() } }
        }
    }

    /**
     * (Update 241, #82) Android refused to start the overlay service from the Quick Settings tile (background-start rules).
     * Now an activity of Voltcap's is in front, which may start it: start it on the game's screen and close at once, so
     * the game is back in front with the overlay over it. Nothing is drawn, no panel.
     */
    override fun onResume() {
        super.onResume()
        if (!startHop) return
        startHop = false
        val r = runCatching { PerfOverlayService.applyNow(applicationContext, gameInFront = true) }.getOrNull()
        android.util.Log.i("OverlayTile", "start from the tile's activity hop: $r (Update 241, #82)")
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
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
    private fun Panel(onDone: () -> Unit) {
        val ctx = this
        val cfg by PerfSettings.state.collectAsState()
        // Overlay choice / moving changes rebuild the running overlays (sizes and opacity follow live).
        fun change(rebuild: Boolean = false, f: (com.shelf.launcher.data.perf.PerfConfig) -> com.shelf.launcher.data.perf.PerfConfig) {
            PerfSettings.update(ctx, f)
            if (rebuild) PerfOverlayService.reconfigure(ctx)
            com.shelf.launcher.device.tune.GameNotification.refresh(ctx)
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { onDone() } },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.widthIn(max = 460.dp).padding(16.dp)
                    .voltSurface(uiRounded(18.dp), base = ShelfTheme.colors.surface, alpha = 0.97f)
                    .pointerInput(Unit) { detectTapGestures { } } // taps on the card stay on it
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("Overlays over the game", color = ShelfTheme.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                MutedNote("Changes show on the game right away.")
                if (com.shelf.launcher.data.Features.quickMenuInGame.state.value) {
                    Spacer(Modifier.height(6.dp))
                    Chip("Quick menu: achievements and Bluetooth  ›", accent = true) {
                        startActivity(android.content.Intent(ctx, QuickMenuActivity::class.java))
                        onDone()
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Show", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                ChipFlow {
                    Chip((if (cfg.showDuringGames) "✓ " else "") + "Stats", accent = cfg.showDuringGames) {
                        change(rebuild = true) { it.copy(showDuringGames = !it.showDuringGames) }
                    }
                    Chip((if (cfg.musicDuringGames) "✓ " else "") + "Music", accent = cfg.musicDuringGames) {
                        change(rebuild = true) { it.copy(musicDuringGames = !it.musicDuringGames) }
                    }
                    Chip((if (cfg.raDuringGames) "✓ " else "") + "Trophies", accent = cfg.raDuringGames) {
                        change(rebuild = true) { it.copy(raDuringGames = !it.raDuringGames) }
                    }
                }
                // Update 202: what the small trophies box shows, part by part (applies live, no rebuild needed).
                if (cfg.raDuringGames) {
                    Spacer(Modifier.height(10.dp))
                    Text("Trophies box shows", color = ShelfTheme.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    RaOverlayPartChips(cfg) { g -> change(f = g) }
                }
                Spacer(Modifier.height(10.dp))
                PercentSlider("Size", cfg.overlayScale, { v -> change { it.copy(overlayScale = v) } }, min = 50, max = 200, live = true)
                PercentSlider("Opacity", cfg.overlayOpacity, { v -> change { it.copy(overlayOpacity = v) } }, min = 20, max = 100, live = true)
                Spacer(Modifier.height(6.dp))
                Text("Moving", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                var positionsReset by remember { mutableStateOf(false) }
                ChipFlow {
                    Chip((if (cfg.overlaysMovable) "✓ " else "") + "Drag to move", accent = cfg.overlaysMovable) {
                        change(rebuild = true) { it.copy(overlaysMovable = !it.overlaysMovable) }
                    }
                    val arrows = com.shelf.launcher.data.Features.overlayArrows.value
                    Chip((if (arrows) "✓ " else "") + "Fold arrows", accent = arrows) {
                        com.shelf.launcher.data.Features.overlayArrows.toggle()
                        PerfOverlayService.reconfigure(ctx)
                    }
                    Chip(if (positionsReset) "✓ Back in the corners" else "Back to the corners", accent = false) {
                        OverlayPositions.clearAll(); positionsReset = true
                        PerfOverlayService.reconfigure(ctx)
                    }
                }
                MutedNote("Drag an overlay with a finger, anywhere on the screen: it stays where you let go, or snaps into a corner when you let go near one. " +
                    "The small arrow in its outer corner folds it away to a tab in that corner; tap the tab to bring it back. " +
                    "Drag one of its corners, or pinch it with two fingers, to make that overlay bigger or smaller. Back to the corners also undoes those sizes.")
                // The first check asks the root service several times: off the main thread (update 91).
                val tunerSupported by androidx.compose.runtime.produceState(false) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { com.shelf.launcher.device.tune.Tuner.supported }.getOrDefault(false)
                    }
                }
                if (tunerSupported) {
                    Spacer(Modifier.height(10.dp))
                    Text("Power", color = ShelfTheme.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    var tick by remember { mutableIntStateOf(0) }
                    // Also re-read when a Quick Settings tile changed it while this panel is open.
                    val sync by com.shelf.launcher.device.SystemSync.version.collectAsState()
                    val g = remember(tick, sync) { com.shelf.launcher.device.tune.TuneStore(ctx).global() }
                    val label = if (g.autoTdp) "AutoTDP ${g.autoMode.label}" else g.tier.label
                    ChipFlow {
                        Chip("Power: $label  ›", accent = true) {
                            Thread {
                                runCatching { com.shelf.launcher.device.tune.Tuner.cycleTile(ctx) }
                                com.shelf.launcher.device.tune.GameNotification.refresh(ctx)
                                runOnUiThread { tick++ }
                            }.start()
                        }
                        Chip("Underclock: ${g.effectiveUnderclock.label}  ›", accent = g.underclocked) {
                            Thread {
                                runCatching { com.shelf.launcher.device.tune.Tuner.cycleUnderclock(ctx) }
                                com.shelf.launcher.device.tune.GameNotification.refresh(ctx)
                                runOnUiThread { tick++ }
                            }.start()
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Sound", color = ShelfTheme.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                ChipFlow {
                    Chip("Volume mixer  ›", accent = false) {
                        runCatching { ctx.startActivity(com.shelf.launcher.device.audio.AppMixer.openIntent(ctx)) }
                        onDone()
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Chip("Done", onClick = onDone)
                }
            }
        }
    }

    companion object {
        /** (Update 241, #82) Start the overlays and close (the overlay tile's fallback), instead of showing the panel. */
        const val EXTRA_START_OVERLAYS = "start_overlays"
    }
}
