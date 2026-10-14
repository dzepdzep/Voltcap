package com.shelf.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shelf.launcher.data.BatterySaver
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 187: the battery saver's home-screen side (BatterySaver.kt): the battery listener, the settings (Settings ›
 * Device › Battery saver, also in the R1 tile's popup) and the one-time note about it.
 */

/** Keeps [BatterySaver] told about the battery while the home screen is on screen (placed once, AmbientEffects). */
@Composable
internal fun BatterySaverDriver() {
    val context = LocalContext.current
    val resumed = rememberIsResumed()
    DisposableEffect(resumed) {
        if (!resumed) return@DisposableEffect onDispose { }
        BatterySaver.ensure(context)
        fun read(i: Intent) {
            val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val plugged = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (level >= 0) BatterySaver.onBattery(level * 100 / scale, plugged)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { i?.let { runCatching { read(it) } } }
        }
        // (The sticky battery intent comes back at once: the level is known without waiting for a change.)
        runCatching { context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { read(it) } }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    // Moving pictures aren't Compose: told on every change (a setting too).
    val resting = BatterySaver.resting
    LaunchedEffect(resting) { BatterySaver.sync() }
}

/** The settings (Settings › Device › Battery saver; the R1 Battery saver tile's popup). */
@Composable
internal fun BatterySaverSettings() {
    MutedNote(
        "Low battery: below the level set here (not plugged in) the theme animation holds still, covers stop moving, " +
            "video clips wait and the card effects rest. Moving through your games stays as it is, and plugged in " +
            "everything comes back. Games are never slowed down."
    )
    Spacer(Modifier.height(6.dp))
    val now = BatterySaver.reason() ?: ("Not saving now · battery ${BatterySaver.level} %" + if (BatterySaver.plugged) ", plugged in" else "")
    Text(now, color = if (BatterySaver.resting) ShelfTheme.accent else ShelfTheme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    StyleChoice("Save battery", BatterySaver.Mode.values(), Features.saverMode.state.value, { it.label }) { Features.saverMode.value = it }
    if (Features.saverMode.state.value == BatterySaver.Mode.AUTO) {
        StyleChoice("Below", arrayOf(10, 15, 20, 25, 30), Features.saverLowAt.state.value, { "$it %" }) { Features.saverLowAt.value = it }
        SwitchRow(
            Icons.Filled.BatterySaver, "Also with Android's Battery Saver",
            "While Android's own Battery Saver is on, Voltcap saves too, whatever the level.",
            Features.saverFollowSystem.state.value, { Features.saverFollowSystem.value = it }
        )
    }
    SwitchRow(
        Icons.Filled.SportsEsports, "Rest while you play",
        "On a two-screen handheld a game on one screen leaves Voltcap showing on the other. While a game you started " +
            "from Voltcap has the controller, Voltcap rests as if it were behind it: no animation, moving covers, video " +
            "clips, slideshows or timers. A touch on Voltcap's screen wakes it.",
        Features.saverAway.state.value, { Features.saverAway.value = it }
    )
    // Update 188: the second screen's dimming, here too (it's in Settings › Screens as well).
    if (SecondScreenShown.value) {
        Spacer(Modifier.height(8.dp))
        SecondDimSettings()
    }
}

/**
 * Update 188: when the second screen dims (SecondDim.kt): after how long unused, how dark, and the backlight.
 * (Settings › Screens › Dim the second screen, and Settings › Device › Battery saver.)
 */
@Composable
internal fun SecondDimSettings() {
    val after = Features.secondDimAfter.state.value
    StyleChoice(
        "Dim the second screen after", arrayOf(0, 30, 60, 120, 300), after,
        { when (it) { 0 -> "Never"; 30 -> "30 s"; 60 -> "1 min"; 120 -> "2 min"; else -> "5 min" } }
    ) { Features.secondDimAfter.value = it; SecondDim.settingsChanged() }
    if (after > 0) {
        StyleChoice(
            "How dark", arrayOf(60, 85, 100), Features.secondDimLevel.state.value,
            { when (it) { 60 -> "Dim"; 85 -> "Dark"; else -> "Black" } }
        ) { Features.secondDimLevel.value = it; SecondDim.settingsChanged() }
        SwitchRow(
            Icons.Filled.BrightnessLow, "Backlight down too",
            "While it's dimmed, the second screen's own brightness goes down as well (the main screen is untouched).",
            Features.secondDimBacklight.state.value, { Features.secondDimBacklight.value = it; SecondDim.settingsChanged() }
        )
    }
    MutedNote(
        "Nobody touching the second screen and nothing pressed in Voltcap for that long — also while a game runs on " +
            "the other screen — and it fades dark. Touch it to light it up again (that touch doesn't press anything)."
    )
}

/** The R1 tile's popup: the saver and, under it, the battery watcher. Fits the screen; Done always shows. */
@Composable
internal fun BatterySaverPopup(onDismiss: () -> Unit) {
    val maxH = screenSizeDp().height * 0.92f
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(0.9f).heightIn(max = maxH)) { ShelfCard {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                SectionLabel("Battery saver")
                BatterySaverSettings()
                Spacer(Modifier.height(12.dp))
                BatteryWatchCard()
            }
            Spacer(Modifier.height(8.dp))
            Chip("Done", onClick = onDismiss)
        } }
    }
}

/**
 * The one-time note (first start, or the first start after this update): what the battery saver does and where its
 * settings are. Wide and short so it fits a handheld's screen; the buttons always show.
 */
@Composable
internal fun BatterySaverIntroDialog(onSettings: () -> Unit, onDismiss: () -> Unit) {
    val maxH = screenSizeDp().height * 0.92f
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        ImmersiveDialogWindow()
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(0.88f).heightIn(max = maxH)) { ShelfCard {
            SectionLabel("Voltcap saves your battery")
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(
                    listOf(
                        "While you play, Voltcap rests: artwork downloads wait and the pictures it had loaded are given " +
                            "back to the emulator. Your saves still sync.",
                        "Two screens: Voltcap rests on the other screen too, and it dims there when nobody uses it. A " +
                            "touch lights it up again.",
                        "Below ${Features.saverLowAt.value} % (not plugged in), or with Android's Battery Saver on, the theme " +
                            "animation holds still, covers stop moving and video clips wait.",
                        "Games are never slowed down."
                    ).joinToString("\n") { "•  $it" },
                    color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(4.dp))
                MutedNote("Settings › Device › Battery saver, or hold the Battery saver tile in R1.")
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                Chip("Got it", accent = true) { onDismiss() }
                Chip("Settings", accent = false) { onSettings() }
            }
        } }
    }
}
