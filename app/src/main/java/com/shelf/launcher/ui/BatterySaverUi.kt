package com.shelf.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
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
        "Voltcap keeps its own battery use down. Low battery: below the level set here (not plugged in) the theme " +
            "animation holds still, covers stop moving, video clips wait and the card effects rest — moving through " +
            "your games stays as it is. Plugged in, everything comes back. Games themselves are never slowed down."
    )
    Spacer(Modifier.height(6.dp))
    val now = BatterySaver.reason() ?: ("Not resting now · battery ${BatterySaver.level} %" + if (BatterySaver.plugged) ", plugged in" else "")
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
        "On a two-screen handheld a game on one screen leaves Voltcap showing on the other. While the game (or another " +
            "app) has the controller, Voltcap rests as if it were behind it: no animation, moving covers, video clips, " +
            "slideshows or timers. A touch on Voltcap's screen wakes it.",
        Features.saverAway.state.value, { Features.saverAway.value = it }
    )
}

/** The R1 tile's popup: the saver and, under it, the battery watcher. */
@Composable
internal fun BatterySaverPopup(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        Column(Modifier.widthIn(max = 600.dp)) { ShelfCard {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
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
 * settings are.
 */
@Composable
internal fun BatterySaverIntroDialog(onSettings: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        Column(Modifier.widthIn(max = 560.dp)) { ShelfCard {
            SectionLabel("Voltcap saves your battery")
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                Text(
                    listOf(
                        "While you play, Voltcap rests: its artwork downloads, syncing and timers wait, and the pictures it " +
                            "had loaded are given back so the emulator has the memory.",
                        "On a two-screen handheld it rests on the other screen too while the game has the controller — a " +
                            "touch there wakes it.",
                        "Below ${Features.saverLowAt.value} % (not plugged in), or with Android's Battery Saver on, the theme " +
                            "animation holds still, covers stop moving and video clips wait.",
                        "Left alone for a while, the home screen calms down by itself; the first press brings it all back.",
                        "Games are never slowed down."
                    ).joinToString("\n") { "•  $it" },
                    color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, lineHeight = 19.sp
                )
                Spacer(Modifier.height(6.dp))
                MutedNote("Change it in Settings › Device › Battery saver, or with the Battery saver tile in R1 (hold it for the settings).")
            }
            Spacer(Modifier.height(10.dp))
            ChipFlow {
                Chip("Got it", accent = true) { onDismiss() }
                Chip("Settings", accent = false) { onSettings() }
            }
        } }
    }
}
