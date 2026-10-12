package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.SleepRadios

/** Settings › Device › Sleep mode: Wi-Fi / Bluetooth off a while after the screen goes off (see [SleepRadios]). */
@Composable
internal fun SleepModeCard() {
    val context = LocalContext.current
    var s by remember { mutableStateOf(SleepRadios.load(context)) }
    val available = remember { SleepRadios.available }
    fun set(next: SleepRadios.Settings) { s = next; SleepRadios.save(context, next) }

    SettingsHeading("Sleep mode")
    ShelfCard {
        MutedNote(
            "While the screen is off or locked, Voltcap already stops every animation, timer, lyric and " +
                "achievement check and its Drive polling. Sleep mode goes further: a while after the screen " +
                "goes off it switches Wi-Fi and Bluetooth off, and switches back on exactly what it turned off " +
                "when you wake the handheld - never something you had off yourself. Nothing changes while " +
                "charging, while music plays, while saves sync, or (for Bluetooth) while headphones are connected."
        )
        Spacer(Modifier.height(8.dp))
        if (!available) {
            MutedNote(
                "Switching radios needs system rights, which Voltcap has on AYN handhelds (enhanced access). " +
                    "On this device, an app like SleepManager (github.com/Baggio94/SleepManager) can do it with its helper app."
            )
            return@ShelfCard
        }
        SwitchRow(
            Icons.Filled.Bedtime, "Sleep mode",
            "Radios off after the screen has been off for a while.",
            s.enabled, onChange = { set(s.copy(enabled = it)) }
        )
        if (s.enabled) {
            Spacer(Modifier.height(6.dp))
            SwitchRow(Icons.Filled.Wifi, "Wi-Fi", "Switched off while asleep.", s.wifi, onChange = { set(s.copy(wifi = it)) })
            Spacer(Modifier.height(6.dp))
            SwitchRow(Icons.Filled.Bluetooth, "Bluetooth", "Switched off while asleep (not with headphones connected).", s.bluetooth, onChange = { set(s.copy(bluetooth = it)) })
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                Icons.Filled.BatteryChargingFull, "Not while charging", "Leave everything on while plugged in.",
                s.skipWhileCharging, onChange = { set(s.copy(skipWhileCharging = it)) }
            )
            Spacer(Modifier.height(8.dp))
            SectionLabel("After the screen is off for")
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                SleepRadios.GRACE_CHOICES.forEach { m ->
                    Chip(if (m == 1) "1 minute" else "$m minutes", accent = s.graceMinutes == m) { set(s.copy(graceMinutes = m)) }
                }
            }
            SleepRadios.lastAction(context)?.let {
                Spacer(Modifier.height(8.dp))
                MutedNote(it)
            }
        }
    }
}
