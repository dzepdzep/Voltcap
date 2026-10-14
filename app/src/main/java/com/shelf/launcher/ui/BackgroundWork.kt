package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 185: a tap on the progress text at the top ("Getting art 12/215 · Details 3/182") opens this: each job running
 * in the background, how far it is, what it's waiting for, and a Stop for each (they pick up again by themselves later,
 * or from Settings › Metadata).
 */

@Composable
internal fun BackgroundWorkDialog(
    state: LauncherUiState,
    onStopArt: () -> Unit,
    onStopDetails: () -> Unit,
    onStopSaving: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        ShelfCard {
            SectionLabel("Working in the background")
            val artOn = state.scrapeTotal > 0
            val jobs = listOfNotNull(
                if (artOn) WorkRow("Getting artwork", state.scrapeDone, state.scrapeTotal,
                    "Covers, banners, backgrounds and logos for the games that don't have them; each saved on the device as it comes.", onStopArt) else null,
                if (state.metadataScrapeTotal > 0) WorkRow("Getting details", state.metadataScrapeDone, state.metadataScrapeTotal,
                    if (artOn) "Waiting for the artwork first (both ask ScreenScraper, one request at a time)." else "Summaries, release dates, developers, genres and scores.",
                    onStopDetails) else null,
                if (state.artSaveTotal > 0) WorkRow("Saving artwork", state.artSaveDone, state.artSaveTotal,
                    "Pictures already found, kept on the device so the home screen never needs the network for them.", onStopSaving) else null,
                if (state.raMatchTotal > 0) WorkRow("Matching achievements", state.raMatchDone, state.raMatchTotal,
                    "Which of your games have RetroAchievements sets.", null) else null
            )
            if (jobs.isEmpty()) MutedNote("Nothing is running right now.")
            jobs.forEach { j ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${j.title}  ·  ${j.done} / ${j.total}", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(uiRounded(3.dp)).background(ShelfTheme.colors.textPrimary.copy(alpha = 0.12f))) {
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth((j.done.toFloat() / j.total.coerceAtLeast(1)).coerceIn(0f, 1f))
                                    .clip(uiRounded(3.dp)).background(ShelfTheme.accent)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(j.note, color = ShelfTheme.textMuted, fontSize = 12.sp)
                    }
                    if (j.stop != null) {
                        Spacer(Modifier.width(10.dp))
                        Chip("Stop", accent = false) { j.stop.invoke() }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            ChipFlow {
                Chip("Scraping settings", accent = false) { onDismiss(); onOpenSettings() }
                Chip("Close") { onDismiss() }
            }
        }
    }
}

private class WorkRow(val title: String, val done: Int, val total: Int, val note: String, val stop: (() -> Unit)?)
