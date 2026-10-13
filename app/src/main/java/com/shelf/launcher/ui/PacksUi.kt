package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
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
            Text(packsProgressText(progress), color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            PacksBar(progress.fraction, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            ChipFlow { Chip("Stop") { Packs.cancel() } }
        } else {
            if (failed != null && failed > 0) {
                MutedNote("$failed couldn't be downloaded (no connection?). Try again below.")
                Spacer(Modifier.height(4.dp))
            } else if (Packs.upToDate.value) {
                // (Update 169: Download with everything here already used to do nothing you could see.)
                MutedNote("Everything is on this device already ✓")
                Spacer(Modifier.height(4.dp))
            } else if (failed == 0) {
                MutedNote("Last download finished ✓")
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
                packsProgressText(progress) + " · it carries on in the background",
                color = ShelfTheme.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            PacksBar(progress.fraction, Modifier.fillMaxWidth())
        } else if (choice == Packs.Choice.MINE && Packs.libraryConsoles.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            MutedNote("They download as soon as your games are found.")
        }
    }
}

/** Update 169: "Downloading 3D models… 12 of 82 · 34 %" (with the piece's MB when its size is known). */
internal fun packsProgressText(p: Packs.Progress): String {
    val pct = (p.fraction * 100f).toInt()
    val mb = if (p.size > 0L) " · ${"%.1f".format(p.bytes / 1_000_000f)} of ${"%.1f".format(p.size / 1_000_000f)} MB" else ""
    return "Downloading ${p.current}… ${p.done + 1} of ${p.total} · $pct %" + mb + (if (p.failed > 0) " (${p.failed} failed)" else "")
}

/** Update 169: a thin progress bar in the accent colour. */
@Composable
internal fun PacksBar(fraction: Float, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
    androidx.compose.foundation.layout.Box(modifier.height(5.dp).clip(shape).background(ShelfTheme.colors.textPrimary.copy(alpha = 0.14f))) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(ShelfTheme.accent)
        )
    }
}

/**
 * Update 169: the extra art packs' download, seen from anywhere — a small pill in the corner of the home screen with what
 * it's fetching and how far it is, while it runs (it only showed inside Settings › Game look › Extra art packs, so you
 * couldn't tell whether anything was downloading). It reads the progress itself, so the home screen never redraws for it.
 */
@Composable
internal fun PacksProgressPill(modifier: Modifier = Modifier) {
    val p = Packs.progress.value
    androidx.compose.animation.AnimatedVisibility(
        visible = p != null, modifier = modifier,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
        exit = androidx.compose.animation.fadeOut()
    ) {
        // (The last progress kept, not as state, so the pill fades out showing it rather than going blank.)
        val last = remember { arrayOfNulls<Packs.Progress>(1) }
        if (p != null) last[0] = p
        val q = last[0] ?: return@AnimatedVisibility
        androidx.compose.foundation.layout.Column(
            Modifier.width(230.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(androidx.compose.ui.graphics.Color(0xE0121418))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Filled.CloudDownload, null, tint = ShelfTheme.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${q.current} ${q.done + 1} / ${q.total} · ${(q.fraction * 100f).toInt()} %",
                    color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                )
            }
            Spacer(Modifier.height(5.dp))
            PacksBar(q.fraction, Modifier.fillMaxWidth())
        }
    }
}
