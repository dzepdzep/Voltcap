package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shelf.launcher.data.BatteryWatch
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The battery watcher: how much battery Voltcap itself uses and what could be changed
 * (Settings › Device, the R1 tile and L1 shortcut). The measuring is [BatteryWatch].
 */
@Composable
internal fun BatteryWatchCard() {
    val context = LocalContext.current
    val on = Features.batteryWatch.state.value
    var rev by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { BatteryWatch.start(context) }
    val report by produceState<BatteryWatch.Report?>(null, rev) {
        value = withContext(Dispatchers.IO) { runCatching { BatteryWatch.report(context) }.getOrNull() }
    }
    ShelfCard {
        SectionLabel("Battery watcher")
        MutedNote(
            "Once a minute, while Voltcap runs, it notes how busy it keeps the processor and how fast the battery falls, " +
                "in front, behind a game and with the screen off. Nothing is sent anywhere. The numbers restart every week."
        )
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip(if (on) "✓ Watching" else "Start watching", accent = on) { Features.batteryWatch.value = !on }
            Chip("Refresh", accent = false) { rev++ }
            Chip("Start over", accent = false) { BatteryWatch.reset(context); rev++ }
        }
        Spacer(Modifier.height(10.dp))
        val r = report
        if (r == null) {
            MutedNote("Reading…")
            return@ShelfCard
        }
        val color = when (r.verdict) {
            BatteryWatch.Verdict.HEAVY -> Color(0xFFFF6B5E)
            BatteryWatch.Verdict.MODERATE -> Color(0xFFFFC83D)
            BatteryWatch.Verdict.LIGHT -> Color(0xFF4CD964)
            BatteryWatch.Verdict.NOT_ENOUGH -> ShelfTheme.textMuted
        }
        Text(r.verdict.label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (r.verdict == BatteryWatch.Verdict.NOT_ENOUGH) {
            MutedNote("It needs a little time: use the launcher for a few minutes, play something, and leave it with the screen off for a while.")
        }
        Spacer(Modifier.height(6.dp))
        r.slices.filter { it.hours >= 0.01 }.forEach { s ->
            MutedNote(
                "${s.where.label}: %.1f h measured · %.1f %% of a core".format(s.hours, s.cpuPercent) +
                    (s.avgMa?.let { " · %.0f mA".format(it) } ?: "")
            )
        }
        if (r.findings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("What it found")
            r.findings.forEach { MutedNote("•  $it") }
        }
        if (r.suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("What could be changed")
            r.suggestions.forEach { MutedNote("•  $it") }
        }
        Spacer(Modifier.height(6.dp))
        MutedNote("These are estimates from what an app can see. For exact numbers per app, see Android's own Battery screen.")
    }
}

/** The battery watcher in a window (R1 tile and L1 shortcut). */
@Composable
internal fun BatteryWatchPopup(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        ShelfCard {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(2.dp)) {
                BatteryWatchCard()
                Spacer(Modifier.height(8.dp))
                Chip("Done", onClick = onDismiss)
            }
        }
    }
}
