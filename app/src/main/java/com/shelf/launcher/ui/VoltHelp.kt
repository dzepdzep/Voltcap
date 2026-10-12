package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.ShelfTheme

/** Pages with a short explanation on request (a small "?"), without the full guide. */
enum class VoltTopic(val title: String, val text: String) {
    GAME_LOOK("Game settings", "Artwork priority picks which picture fills the background. In a game's settings, Look for this game can give it its own theme, artwork order, transition, CRT effect, intensity, ambience and state (Playing, On hold, Completed…). Inherit follows your global choice; resetting one game leaves the others alone."),
    PIXEL_STYLE("32-bit style", "Font: Normal, or 32-bit everywhere (names, categories and the status bar too, a size smaller). Pixel corners gives square panels. Pixel artwork is separate and adds chunky pixels to pictures on Android 13 or later. Fine, Classic and Chunky choose the size. It's all under Settings › Theme › Interface style. The 32-bit startup intro shows me opening the capsule; any button or tap skips it."),
    GAME_CLIENTS("Game clients", "DroidDeck (0.3.1 or newer) has two ways in. Export its .droiddeck launch files to a folder (with automatic sync if you like), add that folder in Library › ROM folders and scan: the games appear under DroidDeck and Play opens that exact game. Or use DroidDeck's Add to home screen on a game: Voltcap asks \"Add to Voltcap?\" and it becomes a game too (its options › Remove shortcut takes it out). PXPlay and Asobi can be pinned in L1."),
    SAVE_VAULT(
        "Save Vault",
        "Every game with a real save shows up here: save files, PS1 / PS2 memory cards, PSP saves and saves synced with Google Drive. " +
            "The badge on each one says where it stands: ✓ synced, a clock = not synced yet, arrows = moving now, a warning = conflict or error. " +
            "\"Games not on this device\" shows saves whose game isn't installed here. Filters & look changes the style (memory card, floppy…) and the background."
    ),
    THEME_COLORS(
        "Theme colours",
        "Each theme keeps its own look. Colour source: Theme original keeps its colours, Current game takes them from the cover you're on, " +
            "Custom lets you pick accent and background and tune saturation and brightness. Blur and dim can cover the whole background or only " +
            "the strip behind the games. Reset This Theme puts just this theme back."
    ),
    RETROACHIEVEMENTS(
        "RetroAchievements",
        "Link your account under Accounts and your games' achievements appear on their cards. Open a game's list with Y. " +
            "📍 pins the one you're working on: it stays on the in-game overlay. Your profile lists every game you've played on " +
            "RetroAchievements, even ones that aren't on this device."
    ),
    CUSTOM_PACKS(
        "Your own themes & visualizers",
        "Add your own animated theme or music visualizer as one small text file. Voltcap checks it first (size, loops, how heavy it is), " +
            "test-builds it on this device and draws it smaller and slower than the built-in ones, so it can't overload the app. " +
            "If it ever makes the home screen slow it is switched off by itself. Import files adds them; \"How to make one\" shows " +
            "the whole recipe with examples you can copy."
    ),
    TDP(
        "Power & TDP",
        "TDP is how much power the chip may use. Lower = cooler, quieter and longer battery; higher = more speed. AutoTDP adjusts it " +
            "while you play to hold your frame-rate target. Profiles can be set for the whole device or for one game. The Voltcap Quick " +
            "Settings tile switches profiles without leaving the game."
    )
}

/** A small "?" that opens a short, page-specific explanation. Hidden when page help is off. */
@Composable
internal fun VoltHelpButton(topic: VoltTopic, modifier: Modifier = Modifier, moreLabel: String? = null, onMore: (() -> Unit)? = null) {
    if (!Features.voltHelp.state.value) return
    var open by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(ShelfTheme.colors.surfaceRaised)
            .border(1.5.dp, ShelfTheme.accent.copy(alpha = 0.6f), CircleShape)
            .shelfClickable(CircleShape) { open = true },
        contentAlignment = Alignment.Center
    ) { Text("?", color = ShelfTheme.accent, fontSize = 15.sp, fontWeight = FontWeight.Black) }
    if (open) {
        ShelfDialog(
            icon = Icons.Filled.Info, title = "Help · ${topic.title}", subtitle = "Quick help",
            onDismiss = { open = false }
        ) {
            Text(topic.text, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
            ChipFlow {
                if (moreLabel != null && onMore != null) Chip(moreLabel, accent = true) { open = false; onMore() }
                Chip("Got it") { open = false }
            }
        }
    }
}

/**
 * Help's one access point (home Options menu › Help): replay the setup guide, help for the complex pages,
 * and the help switch.
 */
object VoltHub {
    var open by mutableStateOf(false)
    /** Set to ask the home screen to start the guide (first-time setup) again. */
    var replay by mutableStateOf(false)
}

@Composable
internal fun VoltHubDialog() {
    if (!VoltHub.open) return
    var topic by remember { mutableStateOf<VoltTopic?>(null) }
    ShelfDialog(icon = Icons.Filled.Info, title = "Help", subtitle = "Help and the guide", onDismiss = { VoltHub.open = false }) {
        Column {
            val t = topic
            Text(
                t?.text ?: "Go through the setup guide again, or read about one of the trickier pages.",
                color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, lineHeight = 20.sp
            )
            Spacer(Modifier.height(10.dp))
            ChipFlow {
                Chip("Replay the setup guide") { VoltHub.open = false; VoltHub.replay = true }
                VoltTopic.values().forEach { v -> Chip(v.title, accent = topic == v) { topic = v } }
            }
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                val on = Features.voltHelp.state.value
                Chip(if (on) "✓ Show ? help on pages" else "Show ? help on pages", accent = on) { Features.voltHelp.value = !on }
                Chip("Close", accent = false) { VoltHub.open = false }
            }
        }
    }
}
