package com.shelf.launcher.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import com.shelf.launcher.device.audio.MixerNotification
import com.shelf.launcher.device.tune.GameNotification
import com.shelf.launcher.device.tune.Tuner

/*
 * Settings › Device › Notifications (and one row each on the Overlay and Performance pages): switch off the
 * notifications of the power tuner (TDP), the overlays and the volume mixer.
 *
 * The mixer's is Voltcap's own choice, so its switch just works. The tuner and the overlays run as foreground services,
 * and Android requires them to have a notification (it turns a channel Voltcap itself lowers back on), so only the
 * person can hide those: their switch opens that channel in Android's settings, and reads back what was picked there.
 * Hidden, the tuner and the overlays keep working; on Android 13+ they're still listed in the pull-down's
 * "active apps" list.
 */

/** Which notification a row is about. */
internal enum class VoltNote { POWER, OVERLAY, MIXER }

/** All three switches (Device › Notifications). */
@Composable
internal fun NotificationSettings() {
    Column {
        MutedNote(
            "Hide the notifications Voltcap shows while you play. Hidden, everything keeps working; " +
                "the power and overlay ones open Android's switch for them (Android requires them while those run, so only you can hide them)."
        )
        Spacer(Modifier.height(8.dp))
        // The first check asks the root service several times: off the main thread (update 91).
        val tunerSupported by androidx.compose.runtime.produceState(false) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { Tuner.supported }.getOrDefault(false) }
        }
        if (tunerSupported) NotificationSwitch(VoltNote.POWER)
        NotificationSwitch(VoltNote.OVERLAY)
        if (com.shelf.launcher.device.audio.AppMixer.available(LocalContext.current)) NotificationSwitch(VoltNote.MIXER)
    }
}

/** One notification's switch. */
@Composable
internal fun NotificationSwitch(which: VoltNote) {
    val context = LocalContext.current
    // Read again whenever Voltcap comes back from Android's settings.
    var rev by remember { mutableIntStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                rev++
                // The shared notification moves to a channel that's still on (or out of sight).
                if (which != VoltNote.MIXER) runCatching { GameNotification.refresh(context) }
            }
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    @Suppress("UNUSED_EXPRESSION") rev
    when (which) {
        VoltNote.MIXER -> SwitchRow(
            Icons.Filled.VolumeUp, "Volume mixer notification",
            "Per-app volume (− / +) in Android's pull-down. Off: no notification; the mixer (R1 › Mixer) still works.",
            Features.mixerNotification.state.value,
            { on ->
                Features.mixerNotification.value = on
                MixerNotification.refreshAsync(context)
            }
        )
        VoltNote.POWER, VoltNote.OVERLAY -> {
            val power = which == VoltNote.POWER
            val channel = if (power) GameNotification.CHANNEL_POWER else GameNotification.CHANNEL_OVERLAY
            remember(Unit) { GameNotification.ensureChannels(context); 0 }
            val on = GameNotification.shown(context, channel)
            SwitchRow(
                if (power) Icons.Filled.Speed else Icons.Filled.Layers,
                if (power) "Power tuner (TDP) notification" else "Overlay notification",
                (if (power) "Power tier / AutoTDP with live watts, °C, fan and a Power button while you play."
                else "Size and opacity buttons for the overlays while you play.") +
                    " Opens Android's switch for it" + (if (on) " — turn it off there to hide it." else " — it's hidden now; turn it on there to bring it back."),
                on,
                { openChannelSettings(context, channel) }
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** Android's page for one of Voltcap's notification channels (its app page before Android 8). */
private fun openChannelSettings(context: android.content.Context, channel: String) {
    val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
}
