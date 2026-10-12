package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Packs
import com.shelf.launcher.ui.theme.ShelfTheme

/*
 * Update 131: the extra art packs (data/Packs.kt) — the first-start question and Settings › Game look › Extra art packs.
 */

/**
 * Asked once, after the first-time setup: download the packs for every console, only for the consoles in the library,
 * or not now. The answer can be changed any time in Settings › Game look › Extra art packs.
 */
@Composable
internal fun PacksFirstAsk(onDone: () -> Unit) {
    val context = LocalContext.current
    fun pick(ch: Packs.Choice) {
        Packs.setChoice(context, ch)
        Packs.asked.value = true
        if (ch != Packs.Choice.NONE) Packs.download(context, ch, Packs.libraryConsoles)
        onDone()
    }
    ShelfMenuDialog(
        title = "Download extra art?",
        subtitle = "Your cartridges, console spines, disc-console shells and 3D models, from Voltcap's page on GitHub. " +
            "They aren't in the app, so it stays small. Change it any time in Settings › Game look › Extra art packs.",
        icon = Icons.Filled.CloudDownload,
        onDismiss = { pick(Packs.Choice.NONE) }
    ) {
        MenuRow(Icons.Filled.CloudDownload, "Download all", "Every console (about 130 MB, full-quality cartridges)") { pick(Packs.Choice.ALL) }
        MenuRow(Icons.Filled.SportsEsports, "Only the consoles I have", "Just what your library uses; new consoles follow later") { pick(Packs.Choice.MINE) }
        MenuRow(Icons.Filled.Block, "Not now", "Voltcap's own drawn cartridges, discs and spines") { pick(Packs.Choice.NONE) }
    }
}

/** Settings › Game look › Extra art packs: what's on the device, the download, and removing them. */
@Composable
internal fun PacksCard() {
    val context = LocalContext.current
    val progress = Packs.progress.value
    val failed = Packs.lastFailed.value
    Packs.version.intValue
    var sizeMb by remember { mutableIntStateOf(-1) }
    LaunchedEffect(Packs.version.intValue, progress == null) {
        sizeMb = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Packs.sizeMb(context) }
    }
    ShelfCard {
        Text("Extra art packs", color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        MutedNote(
            "Your cartridge pack, console spines, disc-console shells and 3D models, downloaded from Voltcap's page on GitHub " +
                "(dzepdzep/Voltcap). Without them the looks use Voltcap's own drawn cartridges, discs and spines."
        )
        Spacer(Modifier.height(8.dp))
        Packs.Pack.values().forEach { p ->
            val (have, all) = Packs.count(p)
            Text(
                "${p.label}: " + when (have) { 0 -> "not downloaded"; all -> "all $all consoles"; else -> "$have of $all consoles" },
                color = ShelfTheme.colors.textPrimary, fontSize = 12.sp
            )
            Text(p.about, color = ShelfTheme.textMuted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
        }
        if (sizeMb > 0) Text("On this device: about $sizeMb MB", color = ShelfTheme.textMuted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        if (progress != null) {
            Text(
                "Downloading ${progress.current}… ${progress.done} of ${progress.total}" + (if (progress.failed > 0) " (${progress.failed} failed)" else ""),
                color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            ChipFlow { Chip("Stop") { Packs.cancel() } }
        } else {
            if (failed != null && failed > 0) {
                MutedNote("$failed couldn't be downloaded (no connection?). Try again below.")
                Spacer(Modifier.height(4.dp))
            }
            ChipFlow {
                Chip("Download all") { Packs.setChoice(context, Packs.Choice.ALL); Packs.download(context, Packs.Choice.ALL, Packs.libraryConsoles) }
                Chip("Only my consoles") { Packs.setChoice(context, Packs.Choice.MINE); Packs.download(context, Packs.Choice.MINE, Packs.libraryConsoles) }
                Chip("Remove all", accent = false) { Packs.setChoice(context, Packs.Choice.NONE); Packs.removeAll(context) }
            }
        }
    }
}

/**
 * Update 141: the "Extra art" step of the first-time setup — the same three answers as [PacksFirstAsk], with the
 * download's progress right there. Answering here means the question doesn't come up again after the setup.
 */
@Composable
internal fun PacksSetupStep() {
    val context = LocalContext.current
    Packs.version.intValue
    // Update 144: the answer kept in Compose state, so the ✓ moves at once (the saved setting alone didn't redraw this).
    var choice by remember { mutableStateOf(Packs.choice(context)) }
    val progress = Packs.progress.value
    fun pick(ch: Packs.Choice) {
        choice = ch
        Packs.setChoice(context, ch)
        Packs.asked.value = true
        if (ch != Packs.Choice.NONE) Packs.download(context, ch, Packs.libraryConsoles)
    }
    ShelfCard {
        Text("Download extra art?", color = ShelfTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        MutedNote(
            "Your cartridge pack, console spines, disc-console shells and 3D models, from Voltcap's page on GitHub. " +
                "Without them the looks use Voltcap's own drawn cartridges, discs and spines. " +
                "Not now? Download them any time in Settings › Game look › Extra art packs."
        )
        Spacer(Modifier.height(10.dp))
        ChipFlow {
            Chip((if (choice == Packs.Choice.ALL) "✓ " else "") + "Download all (about 130 MB)", accent = choice == Packs.Choice.ALL) { pick(Packs.Choice.ALL) }
            Chip((if (choice == Packs.Choice.MINE) "✓ " else "") + "Only my consoles", accent = choice == Packs.Choice.MINE) { pick(Packs.Choice.MINE) }
            Chip((if (choice == Packs.Choice.NONE) "✓ " else "") + "Not now", accent = choice == Packs.Choice.NONE) { pick(Packs.Choice.NONE) }
        }
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Downloading ${progress.current}… ${progress.done} of ${progress.total}" + (if (progress.failed > 0) " (${progress.failed} failed)" else "") +
                    " · it carries on in the background",
                color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        } else if (choice == Packs.Choice.MINE && Packs.libraryConsoles.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            MutedNote("They download as soon as your games are found.")
        }
    }
}
