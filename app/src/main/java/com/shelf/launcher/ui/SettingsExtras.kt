package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.BiosCheck
import com.shelf.launcher.data.ConsoleNames
import com.shelf.launcher.data.Screenshots
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * Settings › Library › Collections & status: the three kinds of collection
 * (each can be switched off) and your own collections — rename or delete.
 */
@Composable
internal fun CollectionsSettings(
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    userCollections: Map<String, Set<String>>,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    SettingsHeading("Collections & status")
    SettingsGrid {
        item { m ->
            SwitchRow(
                icon = Icons.Filled.CollectionsBookmark,
                title = "Your own collections",
                subtitle = "Start › Collections… on any game puts it on a list of yours — \"Backlog\", \"Couch co-op\"…",
                checked = appearance.userCollections,
                onChange = { onAppearanceChange(appearance.copy(userCollections = it)) },
                modifier = m
            )
        }
        item { m ->
            SwitchRow(
                icon = Icons.Filled.CollectionsBookmark,
                title = "Franchises, automatically",
                subtitle = "Series with 3 or more games in your library — Final Fantasy, Pokémon, Zelda… — collect themselves and keep up as you add games.",
                checked = appearance.franchiseCollections,
                onChange = { onAppearanceChange(appearance.copy(franchiseCollections = it)) },
                modifier = m
            )
        }
        item { m ->
            SwitchRow(
                icon = Icons.Filled.CollectionsBookmark,
                title = "Genres",
                subtitle = "RPG, Platform, Racing… from the scraped game details (Metadata). Genres with 2 or more games become categories.",
                checked = appearance.genreCollections,
                onChange = { onAppearanceChange(appearance.copy(genreCollections = it)) },
                modifier = m
            )
        }
        item { m ->
            SwitchRow(
                icon = Icons.Filled.Flag,
                title = "Play status",
                subtitle = "Playing, Finished, Dropped, Wishlist, Backlog — set with Start › Status. Each becomes a category; the random pick skips finished and dropped games.",
                checked = appearance.playStatus,
                onChange = { onAppearanceChange(appearance.copy(playStatus = it)) },
                modifier = m
            )
        }
    }
    MutedNote("They all appear under \"Collections, franchises & status\" in the category order below — that entry is hidden until you switch it on there.")

    if (appearance.userCollections && userCollections.isNotEmpty()) {
        var editing by remember { mutableStateOf<String?>(null) }
        var renaming by remember { mutableStateOf<String?>(null) }
        Spacer(Modifier.height(10.dp))
        ShelfCard {
            SectionLabel("Your collections")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                userCollections.keys.sortedBy { it.lowercase() }.forEach { name ->
                    val n = userCollections[name].orEmpty().size
                    MenuRow(Icons.Filled.Edit, name, if (n == 1) "1 game" else "$n games") { editing = name }
                }
            }
        }
        editing?.let { name ->
            ShelfMenuDialog(title = name, subtitle = "Collection", onDismiss = { editing = null }, icon = Icons.Filled.CollectionsBookmark) {
                MenuRow(Icons.Filled.Edit, "Rename") { renaming = name; editing = null }
                MenuRow(Icons.Filled.Delete, "Delete collection", "The games stay in your library", destructive = true) {
                    onDelete(name); editing = null
                }
            }
        }
        renaming?.let { old ->
            var text by remember(old) { mutableStateOf(old) }
            val clash = text.isNotBlank() && !text.trim().equals(old, ignoreCase = true) &&
                userCollections.keys.any { it.equals(text.trim(), ignoreCase = true) }
            ShelfDialog(icon = Icons.Filled.Edit, title = "Rename collection", subtitle = old, onDismiss = { renaming = null }) {
                ShelfTextField(value = text, onValueChange = { text = it }, label = "Name", onSubmit = {
                    if (text.isNotBlank() && !clash) { onRename(old, text.trim()); renaming = null }
                })
                if (clash) {
                    Spacer(Modifier.height(6.dp))
                    MutedNote("There's already a collection called that.")
                }
                Spacer(Modifier.height(10.dp))
                ChipFlow {
                    Chip("Save", enabled = text.isNotBlank() && !clash) { onRename(old, text.trim()); renaming = null }
                    Chip("Cancel", accent = false) { renaming = null }
                }
            }
        }
    }
}

/** Settings › Library › Your screenshots: on/off and the folders searched. */
@Composable
internal fun ScreenshotSettings(
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    SettingsHeading("Your screenshots")
    SettingsGrid {
        item { m ->
            SwitchRow(
                icon = Icons.Filled.PhotoLibrary,
                title = "Show your screenshots",
                subtitle = "The details window lists the screenshots you took of that game — matched by file name. A opens one full screen.",
                checked = appearance.showScreenshots,
                onChange = { onAppearanceChange(appearance.copy(showScreenshots = it)) },
                modifier = m
            )
        }
        if (appearance.showScreenshots) {
            item { m -> ActionCard(Icons.Filled.Folder, "Add a folder", "Another place an emulator saves screenshots", { picking = true }, m) }
        }
    }
    if (appearance.showScreenshots) {
        Spacer(Modifier.height(8.dp))
        ShelfCard {
            SectionLabel("Searched folders")
            MutedNote("Always: " + Screenshots.DEFAULT_FOLDERS.joinToString(", "))
            if (appearance.screenshotFolders.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    appearance.screenshotFolders.sorted().forEach { path ->
                        MenuRow(Icons.Filled.Delete, path.removePrefix("/storage/emulated/0/"), "Remove this folder", destructive = true) {
                            onAppearanceChange(appearance.copy(screenshotFolders = appearance.screenshotFolders - path))
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        FolderBrowserDialog(
            startPath = "/storage/emulated/0",
            title = "Pick a screenshot folder",
            onSelect = { path ->
                onAppearanceChange(appearance.copy(screenshotFolders = appearance.screenshotFolders + path))
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

/**
 * Settings › Emulators › BIOS check: for each console in your library that
 * needs a BIOS, whether a file with the right name is where emulators look.
 */
@Composable
internal fun BiosCheckCard(platforms: List<String>, romRoot: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var round by remember { mutableStateOf(0) }
    var folders by remember { mutableStateOf(BiosCheck.userFolders(context)) }
    var picking by remember { mutableStateOf(false) }
    val results by produceState<Map<String, List<BiosCheck.Result>>?>(null, platforms, romRoot, round, folders) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { BiosCheck.check(platforms, romRoot, folders) }.getOrDefault(emptyMap())
        }
    }
    SettingsHeading("BIOS check")
    ShelfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("BIOS files for your consoles")
                MutedNote("Checks RetroArch/system, the emulators' own BIOS folders, your ROM folder and any folder you add here (with its sub-folders) for the right file names — not whether a dump is good.")
            }
            Spacer(Modifier.width(10.dp))
            Chip("Check again", accent = false) { round++ }
        }
        Spacer(Modifier.height(8.dp))
        ChipFlow { Chip("Add a BIOS folder…", accent = false) { picking = true } }
        if (folders.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                folders.sorted().forEach { path ->
                    MenuRow(Icons.Filled.Delete, path.removePrefix("/storage/emulated/0/"), "Your folder · A removes it", destructive = true) {
                        folders = folders - path
                        BiosCheck.setUserFolders(context, folders)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val r = results
        when {
            r == null -> MutedNote("Looking…")
            r.isEmpty() -> MutedNote("Nothing in your library needs a BIOS. ✓")
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                r.entries.sortedBy { ConsoleNames.label(it.key) }.forEach { (platform, list) ->
                    Column {
                        Text(ConsoleNames.label(platform), color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        list.forEach { res -> BiosLine(res) }
                    }
                }
            }
        }
    }
    if (picking) {
        FolderBrowserDialog(
            startPath = "/storage/emulated/0",
            title = "Where are your BIOS files?",
            onSelect = { path ->
                folders = folders + path
                BiosCheck.setUserFolders(context, folders)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}

@Composable
private fun BiosLine(res: BiosCheck.Result) {
    val ok = res.foundAt != null
    val (mark, color) = when {
        ok -> "✓" to com.shelf.launcher.ui.theme.Ds.success
        res.need.required -> "✕" to com.shelf.launcher.ui.theme.Ds.danger
        else -> "–" to ShelfTheme.textMuted
    }
    Row(Modifier, verticalAlignment = Alignment.Top) {
        Text(mark, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(res.need.what, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp)
            Text(
                when {
                    ok -> res.foundAt!!.removePrefix("/storage/emulated/0/")
                    res.need.names.first().startsWith("*.") -> "Put your PS2 BIOS (.bin) in the PS2 emulator's bios folder"
                    else -> (if (res.need.required) "Missing — needs one of: " else "Optional — ") + res.need.names.joinToString(", ")
                },
                color = ShelfTheme.textMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}
