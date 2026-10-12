package com.shelf.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.RunningGames

/**
 * Settings › Library › Running games: how Voltcap knows a game is still open, and the one switch that
 * makes it exact (Android's usage access). AYN handhelds see and close games directly.
 */
@Composable
internal fun RunningGamesCard() {
    val context = LocalContext.current
    // Usage access is granted outside the app: look again whenever we come back.
    var refresh by remember { mutableStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
        }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val usage = remember(refresh) { RunningGames.hasUsageAccess(context) }
    val enhanced = remember(refresh) { RunningGames.enhanced }
    val open = RunningGames.keys.size

    Spacer(Modifier.height(12.dp))
    ShelfCard {
        SectionLabel("Running games")
        MutedNote(
            "Games still open in the background get a green ring and an \"Open\" tag on the grid and the second screen " +
                "(Mark open games below turns that off). " +
                "A brings one back exactly where you were, X closes it. Starting another game in the same emulator " +
                "closes the old one first; starting a game while a different one is open asks first. " +
                "Play time counts only the minutes a game is really in front."
        )
        Spacer(Modifier.height(8.dp))
        MutedNote(
            when {
                enhanced -> "Enhanced access is on (this handheld's system service): open games are checked directly and X really ends them."
                usage -> "Usage access is on: Voltcap sees when a game is closed and counts only active play time. " +
                    "X asks Android to end the game; if it can't, the app's page with Force stop opens."
                else -> "Without usage access a game counts as open for 20 minutes after you leave it, and play time counts the whole session. " +
                    "Turn on usage access for exact results."
            } + if (open > 0) "  Open now: $open." else ""
        )
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip(if (RunningGames.marked) "✓ Mark open games" else "Mark open games", accent = RunningGames.marked) {
                RunningGames.setMarked(context, !RunningGames.marked)
            }
            Chip(if (usage) "Usage access: on" else "Turn on usage access", accent = !usage) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            .setData(android.net.Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.recoverCatching {
                    // Some builds reject the package URI: open the list instead.
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}
