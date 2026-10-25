package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.LauncherViewModel
import com.shelf.launcher.data.AppDisplaySource
import com.shelf.launcher.data.AppDisplayTarget
import com.shelf.launcher.data.ConsoleNames
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameProfile
import com.shelf.launcher.data.LaunchExtra
import com.shelf.launcher.data.LaunchExtraType
import com.shelf.launcher.data.ProfileSource
import com.shelf.launcher.data.RuleScope
import com.shelf.launcher.data.SecondPage
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.data.guide.SpoilerLevel

/**
 * "Game settings": everything that can differ per game (or per app), each with where its
 * current value comes from — this game, its console, or the global default — and a way
 * back to inherited. Values that already have their own screens (Screens, Artwork,
 * Emulator) are shown here and link out rather than being duplicated.
 */
@Composable
fun GameProfileDialog(
    game: Game,
    viewModel: LauncherViewModel,
    /** Bumped when screen rules change, so the display summary refreshes. */
    displayVersion: Int,
    hasCustomArt: Boolean,
    onOpenScreens: () -> Unit,
    onOpenArtwork: () -> Unit,
    onOpenEmulator: () -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("GameProfileDialog")
    val version by viewModel.profileVersion.collectAsState()
    val isApp = game.sourceType == SourceType.APP
    val isRom = game.sourceType == SourceType.ROM
    val consoleName = ConsoleNames.display(game.platform)
    // Re-read whenever anything changed (the stores are tiny SharedPreferences reads).
    val resolved = remember(version, game.stableKey) { viewModel.effectiveProfile(game) }
    val own = remember(version, game.stableKey) { viewModel.gameProfile(game) }
    val cat = remember(version, game.stableKey) { viewModel.categoryProfile(game) }

    fun inherited(src: ProfileSource) = when (src) {
        ProfileSource.GAME -> "Set for this game"
        ProfileSource.CATEGORY -> "Inherited from $consoleName"
        ProfileSource.GLOBAL -> "Inherited from the default"
    }

    ShelfDialog(
        icon = Icons.Filled.Tune,
        title = "Game settings",
        subtitle = gameDisplayName(game.name),
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.72f,
        footer = {
            Chip("Reset all to inherited", accent = false) {
                viewModel.resetGameProfile(game)
                viewModel.clearArtOverrides(game)
                if (isApp) game.packageName?.let { viewModel.resetAppDisplay(it) }
                else if (viewModel.screenPlanFor(game)?.resolved?.scope == RuleScope.GAME) {
                    viewModel.setScreenRule(RuleScope.GAME, game.stableKey, null)
                }
                viewModel.setGuideSpoilerOnly(game, SpoilerLevel.NONE)
                com.shelf.launcher.data.GameLooks.reset(game.stableKey)
            }
            Chip("Done", onClick = onDismiss)
        }
    ) {
        // ---- Display ----
        Section(
            "Display",
            if (isApp) {
                val (target, src) = remember(displayVersion, version, game.stableKey) { viewModel.effectiveAppDisplay(game) }
                (if (src == AppDisplaySource.APP) "Set for this app" else "Inherited from the " +
                    (if (src == AppDisplaySource.CATEGORY) "Emulators category" else "default")) + ": " + target.label
            } else {
                val plan = remember(displayVersion, version, game.stableKey) { viewModel.screenPlanFor(game) }
                plan?.let { "Layout: " + screensRowSubtitle(it.resolved) } ?: "Needs a second screen"
            },
            onReset = if (isApp) {
                val pkg = game.packageName
                val (_, src) = remember(displayVersion, version, game.stableKey) { viewModel.effectiveAppDisplay(game) }
                if (pkg != null && src == AppDisplaySource.APP) ({ viewModel.resetAppDisplay(pkg) }) else null
            } else {
                val plan = remember(displayVersion, version, game.stableKey) { viewModel.screenPlanFor(game) }
                if (plan?.resolved?.scope == RuleScope.GAME) ({ viewModel.setScreenRule(RuleScope.GAME, game.stableKey, null) }) else null
            }
        ) {
            if (isApp) {
                val pkg = game.packageName
                val (target, src) = remember(displayVersion, version, game.stableKey) { viewModel.effectiveAppDisplay(game) }
                if (pkg != null) {
                    Text("Opens on", color = com.shelf.launcher.ui.theme.ShelfTheme.textMuted, fontSize = 12.sp)
                    ChipFlow {
                        AppDisplayTarget.values().forEach { t ->
                            Chip(t.label.replace("The screen I launched it from", "Where launched"),
                                accent = src == AppDisplaySource.APP && target == t) { viewModel.setAppDisplay(pkg, t) }
                        }
                    }
                }
            } else {
                Chip("Screens…", accent = false, onClick = onOpenScreens)
            }
        }

        // ---- Artwork ----
        Section(
            "Artwork",
            if (hasCustomArt) "Custom artwork: yes" else "Custom artwork: no (scraped art)",
            onReset = if (hasCustomArt) ({ viewModel.clearArtOverrides(game) }) else null
        ) { Chip("Artwork & details…", accent = false, onClick = onOpenArtwork) }

        // ---- Look for this game (theme, artwork priority, animation, CRT, intensity, ambience, state) ----
        GameLookSection(
            game,
            autoState = GameStates.byKey[game.stableKey]?.takeIf { com.shelf.launcher.data.GameLooks.of(game.stableKey).state == null }
        )

        // ---- Save sync ----
        if (isRom) {
            TriState(
                "Save sync", resolved.syncSaves.value, resolved.syncSaves.source, ::inherited,
                own.syncSaves, cat.syncSaves, consoleName,
                onGame = { v -> viewModel.updateGameProfile(game) { it.copy(syncSaves = v) } },
                onCategory = { v -> viewModel.updateCategoryProfile(game) { it.copy(syncSaves = v) } },
                note = "Off skips the background save watcher and this game's after-play sync. The regular full sync still covers every folder."
            )
        }

        // ---- Second screen page ----
        Section(
            "Second screen page",
            inherited(resolved.secondScreenPage.source) + ": " + resolved.secondScreenPage.value.label,
            onReset = if (own.secondScreenPage != null) ({ viewModel.updateGameProfile(game) { it.copy(secondScreenPage = null) } }) else null,
            onApplyAll = if (isRom) ConsoleApply(consoleName, cat.secondScreenPage != null,
                { viewModel.updateCategoryProfile(game) { it.copy(secondScreenPage = resolved.secondScreenPage.value) } },
                { viewModel.updateCategoryProfile(game) { it.copy(secondScreenPage = null) } }) else null
        ) {
            ChipFlow {
                Chip("Inherit", accent = own.secondScreenPage == null) { viewModel.updateGameProfile(game) { it.copy(secondScreenPage = null) } }
                SecondPage.values().forEach { p ->
                    Chip(p.label, accent = own.secondScreenPage == p) { viewModel.updateGameProfile(game) { it.copy(secondScreenPage = p) } }
                }
            }
        }

        // ---- Performance overlay ----
        TriState(
            "Performance overlay", resolved.perfOverlay.value, resolved.perfOverlay.source, ::inherited,
            own.perfOverlay, cat.perfOverlay, consoleName.takeIf { isRom }, 
            onGame = { v -> viewModel.updateGameProfile(game) { it.copy(perfOverlay = v) } },
            onCategory = { v -> viewModel.updateCategoryProfile(game) { it.copy(perfOverlay = v) } },
            note = "Used by the performance overlay."
        )

        // ---- Performance tuner ----
        GamePerformanceProfile(
            game.stableKey, gameDisplayName(game.name),
            platform = game.platform.takeIf { isRom }, consoleName = consoleName.takeIf { isRom },
            siblings = {
                viewModel.uiState.value.games
                    .filter { it.sourceType == SourceType.ROM && it.platform == game.platform && it.stableKey != game.stableKey }
                    .map { it.stableKey }.distinct()
            }
        )

        // ---- Launch parameters ----
        if (isRom) LaunchParams(own.launchExtras) { list -> viewModel.updateGameProfile(game) { it.copy(launchExtras = list) } }

        // ---- Walkthrough ----
        if (!isApp) {
            TriState(
                "Walkthrough auto-locate", resolved.guideAutoLocate.value, resolved.guideAutoLocate.source, ::inherited,
                own.guideAutoLocate, cat.guideAutoLocate, consoleName.takeIf { isRom },
                onGame = { v -> viewModel.updateGameProfile(game) { it.copy(guideAutoLocate = v) } },
                onCategory = { v -> viewModel.updateCategoryProfile(game) { it.copy(guideAutoLocate = v) } },
                note = "Off stops the guide from following your progress in the background."
            )
            var spoiler by remember(game.stableKey) { mutableStateOf<SpoilerLevel?>(null) }
            androidx.compose.runtime.LaunchedEffect(game.stableKey, version) { spoiler = viewModel.guideSpoilerOf(game) }
            Section(
                "Walkthrough spoilers",
                if (spoiler == null || spoiler == SpoilerLevel.NONE) "Default: ${SpoilerLevel.NONE.label}" else "Set for this game: ${spoiler!!.label}",
                onReset = if (spoiler != null && spoiler != SpoilerLevel.NONE) ({
                    spoiler = SpoilerLevel.NONE; viewModel.setGuideSpoilerOnly(game, SpoilerLevel.NONE)
                }) else null
            ) {
                ChipFlow {
                    SpoilerLevel.values().forEach { l ->
                        Chip(l.label, accent = spoiler == l) { spoiler = l; viewModel.setGuideSpoilerOnly(game, l) }
                    }
                }
            }
        }

        // ---- Emulator: the console's, or (update 326) this game's own ----
        if (isRom) GameEmulatorSection(game, viewModel, version, consoleName, onOpenEmulator)

        Spacer(Modifier.height(4.dp))
        MutedNote(
            "Not available: controller profiles (Android can't remap controls inside other apps — set them up in the emulator) " +
                "and orientation (Android can't force another app's screen rotation)."
        )
    }
}

/**
 * Update 326: the emulator this game starts in. The console's emulator by default (Change emulator… sets that for every
 * game of the console); or one of the installed emulators made for the console, for this game only — e.g. Eden Duo for
 * a Switch game with a second-screen companion while the others stay on Eden.
 */
@Composable
private fun GameEmulatorSection(
    game: Game,
    viewModel: LauncherViewModel,
    version: Int,
    consoleName: String,
    onOpenEmulator: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val own = remember(version, game.stableKey) { viewModel.gameProfile(game).emulatorPackage }
    val consolePkg = state.emulatorConfigs[game.platform]?.packageName
    val pm = context.packageManager
    // Installed emulators made for this console (RetroArch left out: it needs a core per console, set under Change emulator…).
    val choices = remember(state.installedApps, game.platform) {
        val suggested = com.shelf.launcher.data.EmulatorRegistry.suggestionsFor(game.platform)
            .filter { it.platforms != com.shelf.launcher.data.ALL_PLATFORMS }
            .map { it.packageName }.toSet()
        state.installedApps.filter { a ->
            val p = a.packageName
            p != null && p in suggested && !com.shelf.launcher.data.CoreRegistry.isRetroArch(p) &&
                com.shelf.launcher.data.EmulatorCatalog.isRealEmulator(pm, p)
        }.distinctBy { it.packageName }
    }
    fun label(pkg: String?): String = pkg?.let { p ->
        state.installedApps.firstOrNull { it.packageName == p }?.name?.takeIf { it.isNotBlank() }
            ?: com.shelf.launcher.data.EmulatorRegistry.displayNameFor(p) ?: p
    } ?: "not set up yet"
    val ownInstalled = own != null && choices.any { it.packageName == own }
    val status = when {
        own != null && ownInstalled -> "Set for this game: ${label(own)}"
        own != null -> "Set for this game: $own (not installed — using $consoleName's: ${label(consolePkg)})"
        else -> "$consoleName: ${label(consolePkg)} (shared by all $consoleName games)"
    }
    Section("Emulator", status, onReset = if (own != null) ({ viewModel.setGameEmulator(game, null) }) else null) {
        ChipFlow {
            Chip("Console's emulator", accent = own == null) { viewModel.setGameEmulator(game, null) }
            choices.mapNotNull { it.packageName }.forEach { p ->
                Chip(label(p), accent = own == p) { viewModel.setGameEmulator(game, p) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Chip("Change emulator for all $consoleName games…", accent = false, onClick = onOpenEmulator)
        if (choices.any { it.packageName in com.shelf.launcher.data.ScreenLayouts.DUAL_SCREEN_EMULATORS }) {
            Spacer(Modifier.height(6.dp))
            MutedNote("Eden Duo shows its own companion on the second screen; Voltcap leaves that screen to it unless Screens has a layout set for this game.")
        }
    }
}

private class ConsoleApply(val console: String, val isSet: Boolean, val apply: () -> Unit, val clear: () -> Unit)

@Composable
private fun Section(
    title: String,
    status: String,
    onReset: (() -> Unit)?,
    onApplyAll: ConsoleApply? = null,
    content: @Composable () -> Unit
) {
    ShelfCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(title)
        }
        MutedNote(status)
        Spacer(Modifier.height(8.dp))
        content()
        if (onReset != null || onApplyAll != null) {
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                if (onReset != null) Chip("Reset", accent = false, onClick = onReset)
                if (onApplyAll != null) {
                    Chip("Apply to all ${onApplyAll.console} games", accent = false, onClick = onApplyAll.apply)
                    if (onApplyAll.isSet) Chip("Clear ${onApplyAll.console} default", accent = false, onClick = onApplyAll.clear)
                }
            }
        }
    }
}

/** Inherit / On / Off, with the resolved value and its source. [console] null hides the console shortcuts. */
@Composable
private fun TriState(
    title: String,
    value: Boolean,
    source: ProfileSource,
    describe: (ProfileSource) -> String,
    own: Boolean?,
    category: Boolean?,
    console: String?,
    onGame: (Boolean?) -> Unit,
    onCategory: (Boolean?) -> Unit,
    note: String
) {
    Section(
        title,
        describe(source) + ": " + if (value) "On" else "Off",
        onReset = if (own != null) ({ onGame(null) }) else null,
        onApplyAll = console?.let { c -> ConsoleApply(c, category != null, { onCategory(value) }, { onCategory(null) }) }
    ) {
        ChipFlow {
            Chip("Inherit", accent = own == null) { onGame(null) }
            Chip("On", accent = own == true) { onGame(true) }
            Chip("Off", accent = own == false) { onGame(false) }
        }
        Spacer(Modifier.height(6.dp))
        MutedNote(note)
    }
}

@Composable
private fun LaunchParams(extras: List<LaunchExtra>, onChange: (List<LaunchExtra>) -> Unit) {
    // -1 = adding, >= 0 = editing that row, null = just listing.
    var editing by remember { mutableStateOf<Int?>(null) }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(LaunchExtraType.STRING) }
    Section(
        "Launch parameters",
        if (extras.isEmpty()) "None — the emulator starts with its usual settings" else "Set for this game: ${extras.size} extra" + if (extras.size == 1) "" else "s",
        onReset = if (extras.isNotEmpty()) ({ editing = null; onChange(emptyList()) }) else null
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            extras.forEachIndexed { i, e ->
                Text("${e.key} = ${e.value}  (${e.type.label})", color = com.shelf.launcher.ui.theme.ShelfTheme.colors.textPrimary,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium)
                ChipFlow {
                    Chip("Edit", accent = false) { key = e.key; value = e.value; type = e.type; editing = i }
                    Chip("Remove", accent = false) { editing = null; onChange(extras.filterIndexed { j, _ -> j != i }) }
                }
            }
            if (editing == null) {
                Chip("Add parameter", accent = false) { key = ""; value = ""; type = LaunchExtraType.STRING; editing = -1 }
            } else {
                ShelfTextField(key, { key = it }, "Key (e.g. bootmode)")
                ShelfTextField(value, { value = it }, "Value")
                ChipFlow {
                    LaunchExtraType.values().forEach { t -> Chip(t.label, accent = type == t) { type = t } }
                }
                MutedNote("Number must be a whole number; Yes/No takes true or false. Invalid ones are skipped when launching.")
                ChipFlow {
                    Chip("Save", enabled = key.isNotBlank()) {
                        val item = LaunchExtra(key.trim(), value.trim(), type)
                        val i = editing ?: -1
                        onChange(if (i in extras.indices) extras.toMutableList().also { it[i] = item } else extras + item)
                        editing = null
                    }
                    Chip("Cancel", accent = false) { editing = null }
                }
            }
        }
    }
}
