package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.FrontendImport
import com.shelf.launcher.data.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings › Library: favourites, collections and last played from ES-DE, Pegasus or Daijisho. */
@Composable
internal fun FrontendImportCard(games: List<Game>, romRoots: List<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<FrontendImport.Found>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    SettingsHeading("Import from another frontend")
    ShelfCard {
        MutedNote(
            "Brings over favourites, custom collections and last played from ES-DE, Pegasus and Daijisho playlists. " +
                "Games are matched by file name; nothing in Voltcap is removed."
        )
        Spacer(Modifier.height(8.dp))
        val f = found
        when {
            busy -> MutedNote("Looking…")
            f == null -> {}
            f.isEmpty() -> MutedNote("Nothing found. ES-DE keeps its data in /ES-DE, Pegasus next to the ROMs (metadata.pegasus.txt).")
            else -> f.forEach { s ->
                MutedNote(
                    "${s.source}: ${s.favorites.size} favourites · ${s.collections.size} collections" +
                        (if (s.lastPlayed.isNotEmpty()) " · ${s.lastPlayed.size} last played" else "")
                )
            }
        }
        result?.let { MutedNote(it) }
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            Chip("Look for data", accent = f == null) {
                busy = true; result = null
                scope.launch {
                    found = withContext(Dispatchers.IO) { runCatching { FrontendImport.scan(romRoots) }.getOrDefault(emptyList()) }
                    busy = false
                }
            }
            if (!f.isNullOrEmpty()) Chip("Import") {
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { FrontendImport.apply(context, f, games) }.getOrNull() }
                    busy = false
                    result = r?.let {
                        "Added ${it.favorites} favourites, ${it.inCollections} games into collections (${it.collections} new)" +
                            (if (it.recent > 0) ", ${it.recent} last-played dates" else "") + "."
                    } ?: "Import failed."
                }
            }
        }
    }
}

/** Settings › Library: one copy per game across regions, and which region wins. */
@Composable
internal fun RegionDuplicatesCard(
    appearance: com.shelf.launcher.data.AppearanceSettings,
    onChange: (com.shelf.launcher.data.AppearanceSettings) -> Unit
) {
    SettingsHeading("Regions & duplicates")
    SwitchRow(
        androidx.compose.material.icons.Icons.Filled.FilterList,
        "Show each game once",
        "When a game is in your library in several regions or revisions, only your preferred copy shows. Copies you've played or starred always stay.",
        appearance.hideDuplicates,
        onChange = { onChange(appearance.copy(hideDuplicates = it)) }
    )
    if (appearance.hideDuplicates) {
        Spacer(Modifier.height(8.dp))
        ShelfCard {
            val order = appearance.regionOrder.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            SectionLabel("Preferred region: " + order.joinToString(" › "))
            MutedNote("Tap a region to move it to the front.")
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                com.shelf.launcher.data.RegionDedupe.DEFAULT_ORDER.forEach { r ->
                    Chip(r, accent = order.firstOrNull() == r) {
                        onChange(appearance.copy(regionOrder = (listOf(r) + order.filter { it != r }).joinToString(",")))
                    }
                }
            }
        }
    }
}

/** Settings › Library: quick resume on / off. */
@Composable
internal fun QuickResumeRow(
    appearance: com.shelf.launcher.data.AppearanceSettings,
    onChange: (com.shelf.launcher.data.AppearanceSettings) -> Unit
) {
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        androidx.compose.material.icons.Icons.Filled.FilterList,
        "Quick resume",
        "Starting the game you just left, while its emulator is still open, brings you back exactly where you were instead of booting it again.",
        appearance.quickResume,
        onChange = { onChange(appearance.copy(quickResume = it)) }
    )
}
