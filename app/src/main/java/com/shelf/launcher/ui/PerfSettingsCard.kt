package com.shelf.launcher.ui

import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.EmojiEvents
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.perf.PerfConfig
import com.shelf.launcher.data.perf.PerfCorner
import com.shelf.launcher.data.perf.PerfSampler
import com.shelf.launcher.data.perf.PerfSettings
import com.shelf.launcher.data.perf.SurfaceFlingerFpsSource
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings > Device > Performance: overlay options, permissions and what this device can really report. */
@Composable
fun PerfSettingsCard() {
    val context = LocalContext.current
    val cfg by PerfSettings.state.collectAsState()
    LaunchedEffectInit(context)
    // Permissions are granted outside the app: look again whenever we come back.
    var refresh by remember { mutableStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val canOverlay = remember(refresh) { PerfOverlayService.canDraw(context) }
    val hasDump = remember(refresh) { SurfaceFlingerFpsSource.hasDump(context) }
    // One read when opened / refreshed; nothing keeps sampling while this card is visible.
    val probe by produceState<com.shelf.launcher.data.perf.PerfSnapshot?>(null, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { PerfSampler.probe(context) }.getOrNull() }
    }

    // Overlays over other apps: only ever when one is switched on below; Android's own switch one tap away.
    SettingsHeading("Over other apps")
    ShelfCard {
        Text(
            "Display over other apps: " + (if (canOverlay) "allowed" else "not allowed"),
            color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
        MutedNote(
            "Voltcap never draws over other apps on its own: only the overlays you switch on below (performance, music, " +
                "achievements) appear over a game, and nothing else. Android's permission is only needed for those."
        )
        Spacer(Modifier.height(8.dp))
        Chip(if (canOverlay) "Open \"Display over other apps\"" else "Allow \"Display over other apps\"", accent = !canOverlay) {
            com.shelf.launcher.data.Permissions.openSettings(context, com.shelf.launcher.data.VoltPermission.OVERLAY)
        }
    }
    Spacer(Modifier.height(12.dp))
    // Size and opacity of every overlay over games (also − / + in the overlay notification and the R1 panel).
    SettingsHeading("Overlay size & transparency")
    ShelfCard {
        PercentSlider("Size (100 = normal)", cfg.overlayScale, { v -> PerfSettings.update(context) { it.copy(overlayScale = v) } }, min = 50, max = 200)
        PercentSlider("Opacity (lower = more see-through)", cfg.overlayOpacity, { v -> PerfSettings.update(context) { it.copy(overlayOpacity = v) } }, min = 20, max = 100)
        MutedNote("Applies to the performance readout, the music player and the achievements overlay, live while a game runs. While playing, one quiet notification covers the overlays and the power tuner together, with Size, Opacity and Power buttons; the Quick Settings tiles (Overlay, Power tier) do the same from Android's shade.")
    }
    Spacer(Modifier.height(12.dp))
    // Update 224 (#107 / #108): the overlays' own controls (fold arrows, the achievements list ▲ ▼) quiet down when untouched.
    SettingsHeading("Overlay controls · Auto-hide")
    ShelfCard {
        SwitchRow(
            Icons.Filled.Smartphone, "Auto-fade controls",
            "The overlays' arrows fade when you don't touch them, and come back at the next touch. They always stay where they are and keep working, even when invisible. Readouts, music and achievements themselves never fade.",
            cfg.ctrlFade,
            onChange = { v -> PerfSettings.update(context) { it.copy(ctrlFade = v) } }
        )
        if (cfg.ctrlFade) {
            PercentSlider("Fade after", cfg.ctrlFadeAfterS, { v ->
                // Fully hiding comes after fading: a hide time at or before the new fade time moves along with it.
                PerfSettings.update(context) { it.copy(ctrlFadeAfterS = v, ctrlHideAfterS = if (it.ctrlHideAfterS in 1..v) v + 1 else it.ctrlHideAfterS) }
            }, min = 1, max = 60, valueText = { "$it s" })
            ChipFlow {
                listOf(2, 3, 5, 10, 15, 30).forEach { sec ->
                    Chip((if (cfg.ctrlFadeAfterS == sec) "✓ " else "") + "$sec s", accent = cfg.ctrlFadeAfterS == sec) {
                        PerfSettings.update(context) { it.copy(ctrlFadeAfterS = sec, ctrlHideAfterS = if (it.ctrlHideAfterS in 1..sec) sec + 1 else it.ctrlHideAfterS) }
                    }
                }
            }
            PercentSlider("Faded visibility", cfg.ctrlFadedPct, { v -> PerfSettings.update(context) { it.copy(ctrlFadedPct = v) } }, min = 0, max = 100)
            PercentSlider("Fully hide after", cfg.ctrlHideAfterS, { v ->
                PerfSettings.update(context) { it.copy(ctrlHideAfterS = if (v == 0) 0 else maxOf(v, it.ctrlFadeAfterS + 1)) }
            }, min = 0, max = 120, valueText = { if (it == 0) "Never" else "$it s" })
            ChipFlow {
                listOf(0, 10, 15, 30, 60).forEach { sec ->
                    val on = cfg.ctrlHideAfterS == sec
                    Chip((if (on) "✓ " else "") + (if (sec == 0) "Never" else "$sec s"), accent = on, enabled = sec == 0 || sec > cfg.ctrlFadeAfterS) {
                        PerfSettings.update(context) { it.copy(ctrlHideAfterS = sec) }
                    }
                }
                Chip("Reset (5 s / 15 % / 15 s)", accent = false) {
                    PerfSettings.update(context) { it.copy(ctrlFade = true, ctrlFadeAfterS = 5, ctrlFadedPct = 15, ctrlHideAfterS = 15) }
                }
            }
            MutedNote(
                "0–${cfg.ctrlFadeAfterS} s: normal · then ${cfg.ctrlFadedPct} % · " +
                    (if (cfg.ctrlHideAfterS == 0) "never fully hidden" else "invisible from ${cfg.ctrlHideAfterS} s (still tappable)") +
                    ". Counted from your last touch; fully hiding always comes after fading. A folded overlay's tab only fades."
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    SettingsHeading("Performance overlay")
    ShelfCard {
        SwitchRow(
            Icons.Filled.Smartphone, "Performance overlay during games",
            "A small readout on top of the game. A game can also turn it on by itself in its game settings.",
            cfg.showDuringGames,
            onChange = { v ->
                PerfSettings.update(context) { it.copy(showDuringGames = v) }
                // Switching it on: quietly give the permissions where the firmware allows it (AYN).
                if (v && com.shelf.launcher.data.perf.PerfAutoSetup.possible) {
                    Thread { runCatching { com.shelf.launcher.data.perf.PerfAutoSetup.run(context.applicationContext) } }.start()
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        SectionLabel("Contents")
        ChipFlow {
            fun box(label: String, on: Boolean, set: (PerfConfig, Boolean) -> PerfConfig) =
                label to (on to set)
            listOf(
                box("FPS", cfg.fps) { c, v -> c.copy(fps = v) },
                box("Frame time", cfg.frameTime) { c, v -> c.copy(frameTime = v) },
                box("CPU", cfg.cpu) { c, v -> c.copy(cpu = v) },
                box("GPU", cfg.gpu) { c, v -> c.copy(gpu = v) },
                box("RAM", cfg.ram) { c, v -> c.copy(ram = v) },
                box("Temperature", cfg.temp) { c, v -> c.copy(temp = v) },
                box("Power", cfg.power) { c, v -> c.copy(power = v) },
                box("Battery left", cfg.batteryLeft) { c, v -> c.copy(batteryLeft = v) },
                box("Session time", cfg.sessionTime) { c, v -> c.copy(sessionTime = v) },
                box("Clock", cfg.clock) { c, v -> c.copy(clock = v) },
                box("Heat & battery warnings", cfg.warnings) { c, v -> c.copy(warnings = v) }
            ).forEach { (label, pair) ->
                val (on, set) = pair
                Chip((if (on) "✓ " else "") + label, accent = on) { PerfSettings.update(context) { set(it, !on) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        SectionLabel("Corner")
        ChipFlow {
            PerfCorner.values().forEach { c ->
                Chip(c.label, accent = cfg.corner == c) { PerfSettings.update(context) { it.copy(corner = c) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        Spacer(Modifier.height(10.dp))
        SectionLabel("Permissions")
        Text(
            "Overlay: " + (if (canOverlay) "allowed" else "not allowed") + "   ·   FPS: " + (if (hasDump) "allowed" else "not allowed"),
            color = ShelfTheme.colors.textPrimary, fontSize = 13.sp
        )
        if (!canOverlay || !hasDump) {
            MutedNote("Allow them under Settings › Permissions (\"Display over other apps\" and \"Read frame rate\"). Without the FPS one, everything except the FPS number still works.")
        }
        Spacer(Modifier.height(10.dp))
        SectionLabel("What this device can report")
        val p = probe
        if (p == null) {
            MutedNote("Checking…")
        } else {
            @Composable fun line(name: String, ok: Boolean, extra: String = "") {
                Text(
                    "$name: " + if (ok) "available$extra" else "not readable on this device",
                    color = if (ok) ShelfTheme.colors.textPrimary else ShelfTheme.textMuted, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
            line("CPU frequency", p.cpuMaxMhz != null)
            line("CPU usage", p.cpuPercent != null)
            line("GPU load", p.gpuPercent != null)
            line("GPU frequency", p.gpuMhz != null)
            line("RAM", p.ramUsedMb != null)
            line("SoC temperature", p.socTempC != null)
            line("Battery temperature", p.batteryTempC != null)
            line("Battery power (approx.)", p.powerW != null, if (p.powerW == null) "" else "")
            Text(
                "FPS and frame time: " + if (p.fps != null || hasDump) "available while a game started from Voltcap runs" else "see \"Why n/a?\"",
                color = if (hasDump) ShelfTheme.colors.textPrimary else ShelfTheme.textMuted, fontSize = 12.sp
            )
            FpsWhyNa(refresh)
        }
        Spacer(Modifier.height(6.dp))
        ChipFlow { Chip("Check again", accent = false) { refresh++ } }
        MutedNote("The Performance hotkey and the FPS quick-settings tile only work while Voltcap is in front.")
        MutedNote("Battery: while Voltcap is in the background or the screen is off it runs no timers. The only background work is save sync (scheduled by Android, only when needed), the weekly recap, the optional performance overlay while you play, and Discord presence while you play if it is switched on.")
    }

    // Music (Spotify or any player) on top of the game, in its own corner.
    SettingsHeading("Music over games")
    ShelfCard {
        SwitchRow(
            Icons.Filled.MusicNote, "Music player over games",
            "While music plays, a small player (album art, song, and the current lyric line) sits in a corner on top of the game. Needs notification access and \"Display over other apps\".",
            cfg.musicDuringGames,
            onChange = { v -> PerfSettings.update(context) { it.copy(musicDuringGames = v) } }
        )
        if (cfg.musicDuringGames) {
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Chip((if (cfg.musicLyrics) "✓ " else "") + "Lyrics line", accent = cfg.musicLyrics) {
                    PerfSettings.update(context) { it.copy(musicLyrics = !cfg.musicLyrics) }
                }
                Chip((if (cfg.musicChipButtons) "✓ " else "") + "Song buttons on the chip", accent = cfg.musicChipButtons) {
                    PerfSettings.update(context) { it.copy(musicChipButtons = !cfg.musicChipButtons) }
                }
                Chip((if (cfg.musicInteractive) "✓ " else "") + "Tap to open controls", accent = cfg.musicInteractive) {
                    PerfSettings.update(context) { it.copy(musicInteractive = !cfg.musicInteractive) }
                }
            }
            Spacer(Modifier.height(6.dp))
            SectionLabel("Music corner")
            ChipFlow {
                PerfCorner.values().forEach { c ->
                    Chip(c.label, accent = cfg.musicCorner == c) { PerfSettings.update(context) { it.copy(musicCorner = c) } }
                }
            }
        }
        MutedNote("Shows whatever is playing — Spotify, YouTube Music or any other player — with the current lyric line when lyrics are on (Settings › Audio). With \"Tap to open controls\", a tap on the chip opens play / pause, previous / next and your Spotify playlists and albums; tap outside (or wait a few seconds) to close it. Only the chip itself takes touches; the controller always stays with the game.")
    }
    // RetroAchievements on top of the game, in its own corner.
    SettingsHeading("Achievements over games")
    ShelfCard {
        SwitchRow(
            Icons.Filled.EmojiEvents, "RetroAchievements overlay",
            // Update 202: says what it shows by default now (the rest is switched on below).
            "For games with achievements: a small box with the badge of the achievement you're on (the pinned one, or the next one " +
                "worth going for) and your progress bar. Name, description, totals, missables, the next one and the last unlock can be " +
                "switched on below. Tap it for the full lists. Updates after every unlock check; the controller stays with the game.",
            cfg.raDuringGames,
            onChange = { v -> PerfSettings.update(context) { it.copy(raDuringGames = v) } }
        )
        if (cfg.raDuringGames) {
            Spacer(Modifier.height(6.dp))
            // Update 202: the small overlay's parts, each its own switch (default: badge + progress bar only).
            SectionLabel("What the achievements overlay shows")
            RaOverlayPartChips(cfg) { f -> PerfSettings.update(context, f) }
            MutedNote("The badge is the 📌 pinned achievement, or when nothing is pinned the next one to get (a missable first, then the easiest one left).")
            Spacer(Modifier.height(6.dp))
            SectionLabel("Achievements overlay options")
            ChipFlow {
                Chip((if (cfg.raPoints) "✓ " else "") + "Points", accent = cfg.raPoints) {
                    PerfSettings.update(context) { it.copy(raPoints = !cfg.raPoints) }
                }
                Chip((if (cfg.raUnlockedTab) "✓ " else "") + "Unlocked tab", accent = cfg.raUnlockedTab) {
                    PerfSettings.update(context) { it.copy(raUnlockedTab = !cfg.raUnlockedTab) }
                }
                Chip((if (cfg.raThemed) "✓ " else "") + "Voltcap look", accent = cfg.raThemed) {
                    PerfSettings.update(context) { it.copy(raThemed = !cfg.raThemed) }
                }
            }
            MutedNote("Tap the overlay to open it: how far you are into the set on top, then Left, Missable and Unlocked lists with badges. Tap 📍 on an achievement (in the overlay while playing, or in a game's achievement list) to pin the one you're working on; it then stays on the small overlay. \"Voltcap look\" uses your accent colour and a glass panel; off, it's the plain dark box.")
            Spacer(Modifier.height(6.dp))
            SectionLabel("Achievements corner")
            ChipFlow {
                PerfCorner.values().forEach { c ->
                    Chip(c.label, accent = cfg.raCorner == c) { PerfSettings.update(context) { it.copy(raCorner = c) } }
                }
            }
            if (cfg.raCorner == cfg.corner && cfg.showDuringGames || cfg.raCorner == cfg.musicCorner && cfg.musicDuringGames) {
                MutedNote("Another overlay uses this corner too - pick a free one so they don't overlap.")
            }
        }
    }
}

/**
 * Update 202: one switch per part of the small achievements overlay — here (Settings › Overlay › Achievements over games)
 * and in the in-game overlay panel (OverlayControlActivity). [change] applies a PerfConfig change.
 */
@Composable
internal fun RaOverlayPartChips(
    cfg: com.shelf.launcher.data.perf.PerfConfig,
    change: ((com.shelf.launcher.data.perf.PerfConfig) -> com.shelf.launcher.data.perf.PerfConfig) -> Unit
) {
    ChipFlow {
        Chip((if (cfg.raBadges) "✓ " else "") + "Badge icon", accent = cfg.raBadges) { change { it.copy(raBadges = !it.raBadges) } }
        Chip((if (cfg.raProgressBar) "✓ " else "") + "Progress bar", accent = cfg.raProgressBar) { change { it.copy(raProgressBar = !it.raProgressBar) } }
        Chip((if (cfg.raName) "✓ " else "") + "Name", accent = cfg.raName) { change { it.copy(raName = !it.raName) } }
        Chip((if (cfg.raDescription) "✓ " else "") + "Description", accent = cfg.raDescription) { change { it.copy(raDescription = !it.raDescription) } }
        Chip((if (cfg.raTotals) "✓ " else "") + "Totals (count · % · points)", accent = cfg.raTotals) { change { it.copy(raTotals = !it.raTotals) } }
        Chip((if (cfg.raMissables) "✓ " else "") + "Missables line", accent = cfg.raMissables) { change { it.copy(raMissables = !it.raMissables) } }
        Chip((if (cfg.raNext) "✓ " else "") + "Next achievement line", accent = cfg.raNext) { change { it.copy(raNext = !it.raNext) } }
        Chip((if (cfg.raLastUnlock) "✓ " else "") + "Last unlock", accent = cfg.raLastUnlock) { change { it.copy(raLastUnlock = !it.raLastUnlock) } }
        val showPin = com.shelf.launcher.data.Features.raShowPinned.state.value
        Chip((if (showPin) "✓ " else "") + "📌 Pinned first", accent = showPin) {
            com.shelf.launcher.data.Features.raShowPinned.value = !showPin
        }
    }
}

/** Loads the stored settings once so the card shows them, not the defaults. */
@Composable
private fun LaunchedEffectInit(context: Context) {
    remember { PerfSettings.config(context) }
}

private fun copy(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ADB command", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}


/** "Why n/a?": a tap shows each FPS route, whether it works on this device, and the fix. */
@Composable
internal fun FpsWhyNa(refresh: Int = 0) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Spacer(Modifier.height(4.dp))
    ChipFlow { Chip(if (open) "Hide" else "Why n/a?", accent = false) { open = !open } }
    if (open) {
        val lines by produceState<List<com.shelf.launcher.data.perf.FpsDiagnosis.Line>?>(null, refresh) {
            value = withContext(Dispatchers.IO) {
                runCatching { com.shelf.launcher.data.perf.FpsDiagnosis.explain(context, PerfSampler.gamePackage) }.getOrNull()
            }
        }
        val l = lines
        if (l == null) MutedNote("Checking…")
        else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            l.forEach { line ->
                Text(
                    (if (line.ok) "✓ " else "✗ ") + line.source,
                    color = if (line.ok) ShelfTheme.colors.textPrimary else ShelfTheme.textMuted,
                    fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                MutedNote(line.detail)
            }
            if (l.none { it.ok && it.source != "A game started from Voltcap" }) {
                MutedNote("Nothing on this device can read another app's frame rate yet. Granting \"Read frame rate\" once from a PC (the command above) fixes it for good.")
                ChipFlow { Chip("Copy ADB command", accent = false) { copy(context, com.shelf.launcher.data.perf.SurfaceFlingerFpsSource.ADB_COMMAND) } }
            }
        }
    }
}
