package com.shelf.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.AppsMode
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.LibraryAppsSettings
import com.shelf.launcher.data.kindOf
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Every installed app, and whether it's in the library and/or an emulator.
 * Games Shelf recognises and known emulators are marked "auto" — the switches
 * are for everything it doesn't pick up itself.
 */
@Composable
fun AppsLibraryDialog(
    installedApps: List<Game>,
    settings: LibraryAppsSettings,
    gameApps: Set<String>,
    knownEmulators: Set<String>,
    onModeChange: (AppsMode) -> Unit,
    onToggleInLibrary: (String) -> Unit,
    onToggleEmulator: (String) -> Unit,
    onSetAppKind: (String, com.shelf.launcher.data.AppKind) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("AppsLibraryDialog")
    var query by remember { mutableStateOf("") }

    ShelfDialog(
        icon = Icons.Filled.Apps,
        title = "Apps in your library",
        subtitle = settings.mode.blurb,
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.72f,
        scrollable = false
    ) {
        val apps = installedApps
            .filter { it.packageName != null && (query.isBlank() || it.name.contains(query, ignoreCase = true)) }
            .sortedBy { it.name.lowercase() }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
        item { ShelfCard {
            SectionLabel("Which apps show in the dock")
            ChipFlow {
                AppsMode.values().forEach { mode ->
                    Chip(mode.label, accent = settings.mode == mode) { onModeChange(mode) }
                }
            }
            Spacer(Modifier.height(6.dp))
            MutedNote("Emulators always have their own Emulators category, whatever you pick here.")
        } }
        item { Spacer(Modifier.height(8.dp)) }
        item { MutedNote(
            "Game or app: Voltcap guesses which apps are games (Android's game category, big game studios, " +
                "game engines inside the app, gamepad support). Press the Game / App chip to overrule it — " +
                "games go in the Android games category."
        ) }
        item { Spacer(Modifier.height(12.dp)) }
        item { ShelfTextField(query, { query = it }, "Search apps") }
        item { Spacer(Modifier.height(12.dp)) }
        if (apps.isEmpty()) {
            item { EmptyState("Nothing matches.") }
        }
        items(apps) { app ->
            Column {
            val pkg = app.packageName.orEmpty()
            val kind = settings.kindOf(pkg)
            val autoGame = when (kind) {
                com.shelf.launcher.data.AppKind.GAME -> true
                com.shelf.launcher.data.AppKind.APP -> false
                com.shelf.launcher.data.AppKind.AUTO -> pkg in gameApps
            }
            val autoEmulator = pkg in knownEmulators
            val inLibraryAuto = settings.mode == AppsMode.ALL ||
                (settings.mode == AppsMode.GAMES_AND_ADDED && autoGame)
            AppRow(
                app = app,
                tags = listOfNotNull(if (autoGame) "Game" else null, if (autoEmulator) "Emulator" else null),
                kindLabel = when (kind) {
                    com.shelf.launcher.data.AppKind.AUTO -> if (autoGame) "Game · auto" else "App · auto"
                    com.shelf.launcher.data.AppKind.GAME -> "Game ✓"
                    com.shelf.launcher.data.AppKind.APP -> "App ✓"
                },
                kindPinned = kind != com.shelf.launcher.data.AppKind.AUTO,
                // auto → game → app → auto
                onCycleKind = {
                    onSetAppKind(
                        pkg,
                        when (kind) {
                            com.shelf.launcher.data.AppKind.AUTO -> if (autoGame) com.shelf.launcher.data.AppKind.APP else com.shelf.launcher.data.AppKind.GAME
                            com.shelf.launcher.data.AppKind.GAME -> com.shelf.launcher.data.AppKind.APP
                            com.shelf.launcher.data.AppKind.APP -> com.shelf.launcher.data.AppKind.AUTO
                        }
                    )
                },
                inLibrary = pkg in settings.added || inLibraryAuto,
                inLibraryAuto = inLibraryAuto && pkg !in settings.added,
                isEmulator = pkg in settings.emulators || autoEmulator,
                emulatorAuto = autoEmulator,
                onToggleInLibrary = { onToggleInLibrary(pkg) },
                onToggleEmulator = { onToggleEmulator(pkg) }
            )
            Spacer(Modifier.height(8.dp))
            }
        }
        }
    }
}

@Composable
private fun AppRow(
    app: Game,
    tags: List<String>,
    inLibrary: Boolean,
    inLibraryAuto: Boolean,
    isEmulator: Boolean,
    emulatorAuto: Boolean,
    kindLabel: String,
    kindPinned: Boolean,
    onCycleKind: () -> Unit,
    onToggleInLibrary: () -> Unit,
    onToggleEmulator: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("AppRow")
    val icon = rememberAppIcon(app.packageName)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ShelfTheme.colors.surfaceRaised)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(icon, null, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
        } else {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(ShelfTheme.accent.copy(alpha = 0.25f)))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.name, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = com.shelf.launcher.ui.fitMin(14.sp), maxFontSize = 14.sp),
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (tags.isNotEmpty()) {
                Text(tags.joinToString(" · "), color = ShelfTheme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
        Chip(kindLabel, accent = kindPinned, onClick = onCycleKind)
        Spacer(Modifier.width(8.dp))
        // "auto" ones are on because Shelf recognised them; the chip then
        // pins them explicitly (so they stay even if the mode changes).
        Chip(
            when {
                inLibraryAuto -> "In library ✓ auto"
                inLibrary -> "In library ✓"
                else -> "Add to library"
            },
            accent = inLibrary && !inLibraryAuto,
            onClick = onToggleInLibrary
        )
        Spacer(Modifier.width(8.dp))
        Chip(
            when {
                emulatorAuto -> "Emulator ✓ auto"
                isEmulator -> "Emulator ✓"
                else -> "Emulator?"
            },
            accent = isEmulator && !emulatorAuto,
            enabled = !emulatorAuto,
            onClick = onToggleEmulator
        )
    }
}
