package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.AppScanner
import com.shelf.launcher.data.ConsoleNames
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.OtherScreen
import com.shelf.launcher.data.ResolvedLayout
import com.shelf.launcher.data.RuleScope
import com.shelf.launcher.data.ScreenLayout
import com.shelf.launcher.data.ScreenLayoutsStore
import com.shelf.launcher.data.ScreenPlacement
import com.shelf.launcher.data.ScreenRule
import com.shelf.launcher.data.ScreenSetup
import com.shelf.launcher.data.ScreenSide
import com.shelf.launcher.data.collectionLabel
import com.shelf.launcher.ui.theme.ShelfTheme

/** One place a layout can be saved from a game's Screens option. */
data class LayoutTarget(val scope: RuleScope?, val value: String, val label: String)

/**
 * A game's Screens option: pick a layout, then where it applies — just this
 * launch, this game, its console, a collection it's in, its emulator, or
 * everything. Shows what the game uses now and why.
 */
@Composable
fun ScreenLayoutPickerDialog(
    game: Game,
    setup: ScreenSetup,
    current: ResolvedLayout,
    layouts: List<ScreenLayout>,
    collections: List<Pair<String, String>>,
    emulatorPackage: String?,
    onPlayOnce: (String) -> Unit,
    onSave: (RuleScope, String, String) -> Unit,
    onClearGameRule: () -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("ScreenLayoutPickerDialog")
    var picked by remember { mutableStateOf(current.layout.id) }
    val console = ConsoleNames.display(game.platform)
    val targets = remember(game.stableKey, collections, emulatorPackage) {
        buildList {
            add(LayoutTarget(null, "", "Just this time"))
            add(LayoutTarget(RuleScope.GAME, game.stableKey, "This game"))
            if (game.platform != "App") add(LayoutTarget(RuleScope.CONSOLE, game.platform, "All $console"))
            collections.forEach { (key, name) -> add(LayoutTarget(RuleScope.COLLECTION, key, "Collection: $name")) }
            if (emulatorPackage != null) add(LayoutTarget(RuleScope.EMULATOR, emulatorPackage, "Everything in this emulator"))
            add(LayoutTarget(RuleScope.DEFAULT, "", "Everything (default)"))
        }
    }
    var target by remember { mutableIntStateOf(1) }

    ShelfDialog(
        icon = if (setup == ScreenSetup.TV) Icons.Filled.Tv else Icons.AutoMirrored.Filled.ScreenShare,
        title = "Screens",
        subtitle = "${gameDisplayName(game.name)} · ${setup.label}",
        onDismiss = onDismiss,
        footer = {
            if (current.scope == RuleScope.GAME) Chip("Use the general setting", accent = false, onClick = onClearGameRule)
            Chip("Cancel", accent = false, onClick = onDismiss)
            val t = targets.getOrNull(target)
            val scope = t?.scope
            if (t == null || scope == null) {
                Chip("Play") { onPlayOnce(picked) }
            } else {
                Chip("Save") { onSave(scope, t.value, picked) }
            }
        }
    ) {
        MutedNote(
            "Now: ${current.layout.name} — ${current.reason { ConsoleNames.display(it) }}."
        )
        Spacer(Modifier.height(10.dp))
        SectionLabel("Layout")
        layouts.forEach { layout ->
            RadioLine(layout.name, selected = layout.id == picked, blurb = layout.summary(setup)) { picked = layout.id }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("Use it for")
        MutedNote("The most specific one wins: this game, then a collection, then the console, then the emulator, then the default.")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            targets.forEachIndexed { i, t ->
                Chip(t.label, accent = i == target) { target = i }
            }
        }
    }
}

/**
 * Settings › Screens: which kind of screen is connected, the default layout
 * for each setup, every rule you've made, and your own layouts.
 */
@Composable
internal fun ScreensSettingsContent(
    appearance: com.shelf.launcher.data.AppearanceSettings,
    onAppearanceChange: (com.shelf.launcher.data.AppearanceSettings) -> Unit,
    games: List<Game>,
    /** Bumped by the view model when rules change elsewhere. */
    version: Int,
    onChanged: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("ScreensSettingsContent")
    val context = LocalContext.current
    val store = remember { ScreenLayoutsStore(context) }
    var localVersion by remember { mutableIntStateOf(0) }
    fun changed() { localVersion++; onChanged() }

    val screen = remember(localVersion) { ScreenPlacement.secondScreen(context) }
    val connectedSetup = remember(screen, localVersion) { screen?.let { store.setupFor(it) } }
    var editingSetup by remember { mutableStateOf(connectedSetup ?: ScreenSetup.DUAL) }
    val layouts = remember(localVersion, version) { store.layouts() }
    val rules = remember(localVersion, version, editingSetup) { store.rules(editingSetup) }
    val gameNames = remember(games) { games.associate { it.stableKey to gameDisplayName(it.name) } }
    var editing by remember { mutableStateOf<ScreenLayout?>(null) }

    SwitchRow(
        icon = Icons.AutoMirrored.Filled.ScreenShare,
        title = "Use the second screen",
        subtitle = "Voltcap's view on the second screen, and the layouts below when a game starts.",
        checked = appearance.useSecondScreen,
        onChange = { onAppearanceChange(appearance.copy(useSecondScreen = it)) }
    )

    SecondHomeSettings(appearance, onAppearanceChange)
    AppsOpenOnCard()

    SettingsHeading("Connected now")
    ShelfCard {
        if (screen == null) {
            MutedNote("No second screen right now. Rules still save; they're used once a screen is there.")
        } else {
            SectionLabel("${screen.name} · ${screen.width}×${screen.height}")
            MutedNote("Which kind of screen is this? Each kind keeps its own layouts.")
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                ScreenSetup.values().forEach { s ->
                    Chip(s.label, accent = s == connectedSetup) {
                        store.setSetupFor(screen, s)
                        editingSetup = s
                        changed()
                    }
                }
            }
        }
    }

    SettingsHeading("Rules")
    ShelfCard {
        SectionLabel("Showing rules for")
        ChipFlow {
            ScreenSetup.values().forEach { s ->
                Chip(s.label, accent = s == editingSetup) { editingSetup = s }
            }
        }
        Spacer(Modifier.height(12.dp))
        val defaultRule = rules.firstOrNull { it.scope == RuleScope.DEFAULT }
        SectionLabel("Default layout")
        MutedNote(
            if (defaultRule == null) {
                "Not set: Voltcap uses " + (if (editingSetup == ScreenSetup.TV) "game on the TV" else "game + info") +
                    ", and lets DS/3DS emulators use both screens."
            } else "Used for every game without a more specific rule."
        )
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            layouts.forEach { layout ->
                Chip(layout.name, accent = defaultRule?.layoutId == layout.id) {
                    store.setRule(editingSetup, RuleScope.DEFAULT, "", layout.id)
                    changed()
                }
            }
            if (defaultRule != null) {
                Chip("Voltcap's default", accent = false) {
                    store.setRule(editingSetup, RuleScope.DEFAULT, "", null)
                    changed()
                }
            }
        }
        val specific = rules.filter { it.scope != RuleScope.DEFAULT }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Games, consoles, collections and emulators")
        if (specific.isEmpty()) {
            EmptyState("None yet. Press Start on a game › Screens to set one for that game, its console, a collection or its emulator.")
        } else {
            specific.forEach { rule ->
                RuleLine(
                    rule = rule,
                    target = ruleTargetName(rule, gameNames),
                    layoutName = layouts.firstOrNull { it.id == rule.layoutId }?.name ?: "(deleted layout)",
                    onRemove = {
                        store.setRule(editingSetup, rule.scope, rule.value, null)
                        changed()
                    }
                )
            }
        }
    }

    SettingsHeading("Layouts")
    ShelfCard {
        layouts.forEach { layout ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(layout.name, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(layout.summary(editingSetup), color = ShelfTheme.textMuted, fontSize = 11.sp)
                }
                if (!layout.builtIn) {
                    Spacer(Modifier.width(8.dp))
                    Chip("Edit", accent = false) { editing = layout }
                    Spacer(Modifier.width(6.dp))
                    Chip("Delete", accent = false) {
                        store.deleteLayout(layout.id)
                        changed()
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Chip("New layout") {
            editing = ScreenLayout(store.newLayoutId(), "My layout", ScreenSide.MAIN, OtherScreen.INFO)
        }
    }
    Spacer(Modifier.height(8.dp))
    MutedNote(
        "The controller always goes to the game: Voltcap fills the other screen first and starts the game last. " +
            "Coming back to Voltcap clears the other screen again (apps you put there stay open)."
    )

    editing?.let { layout ->
        LayoutEditorDialog(
            initial = layout,
            setup = editingSetup,
            onSave = {
                store.saveLayout(it)
                editing = null
                changed()
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun RuleLine(rule: ScreenRule, target: String, layoutName: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ShelfTheme.colors.surfaceRaised)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(target, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("${rule.scope.label} → $layoutName", color = ShelfTheme.textMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        Chip("Remove", accent = false, onClick = onRemove)
    }
}

private fun ruleTargetName(rule: ScreenRule, gameNames: Map<String, String>): String = when (rule.scope) {
    RuleScope.GAME -> gameNames[rule.value] ?: rule.value.substringAfterLast('/')
    RuleScope.COLLECTION -> collectionLabel(rule.value)
    RuleScope.CONSOLE -> ConsoleNames.display(rule.value)
    RuleScope.EMULATOR -> rule.value
    RuleScope.DEFAULT -> "Everything"
}

/** Make or change one of your own layouts. */
@Composable
private fun LayoutEditorDialog(
    initial: ScreenLayout,
    setup: ScreenSetup,
    onSave: (ScreenLayout) -> Unit,
    onDismiss: () -> Unit
) {
    var layout by remember { mutableStateOf(initial) }
    val apps by com.shelf.launcher.data.InstalledAppsHub.apps.collectAsState()
    var showAllApps by remember { mutableStateOf(false) }

    ShelfDialog(
        icon = Icons.Filled.Add,
        title = if (initial.name == "My layout") "New layout" else "Edit layout",
        subtitle = layout.summary(setup),
        onDismiss = onDismiss,
        footer = {
            Chip("Cancel", accent = false, onClick = onDismiss)
            Chip("Save", enabled = layout.name.isNotBlank()) { onSave(layout) }
        }
    ) {
        ShelfTextField(value = layout.name, onValueChange = { layout = layout.copy(name = it) }, label = "Name")
        Spacer(Modifier.height(12.dp))
        SwitchRow(
            icon = Icons.AutoMirrored.Filled.ScreenShare,
            title = "The emulator uses both screens",
            subtitle = "For DS/3DS emulators that draw both screens themselves — Voltcap stays out of the way.",
            checked = layout.emulatorUsesBoth,
            onChange = { layout = layout.copy(emulatorUsesBoth = it) }
        )
        if (!layout.emulatorUsesBoth) {
            Spacer(Modifier.height(12.dp))
            SectionLabel("The game goes on")
            ChipFlow {
                ScreenSide.values().forEach { side ->
                    Chip(setup.sideName(side).replaceFirstChar { it.uppercase() }, accent = layout.gameOn == side) {
                        // (update 91) "Keep Voltcap" isn't offered with the game on the second screen: never save it that way.
                        layout = layout.copy(
                            gameOn = side,
                            other = if (side == ScreenSide.SECOND && layout.other == OtherScreen.KEEP) OtherScreen.INFO else layout.other
                        )
                    }
                }
            }
            val otherSide = if (layout.gameOn == ScreenSide.MAIN) ScreenSide.SECOND else ScreenSide.MAIN
            Spacer(Modifier.height(12.dp))
            SectionLabel("The ${setup.sideName(otherSide)} shows")
            OtherScreen.values()
                // Voltcap can't stay on the main screen under a game on the
                // other one (it wouldn't notice the game ending).
                .filter { !(it == OtherScreen.KEEP && layout.gameOn == ScreenSide.SECOND) }
                .forEach { option ->
                    RadioLine(option.label, selected = layout.other == option, blurb = option.blurb) {
                        layout = layout.copy(other = option)
                    }
                }
            if (layout.other == OtherScreen.APP) {
                Spacer(Modifier.height(10.dp))
                SectionLabel("Which app")
                val shown = if (showAllApps) apps else apps.take(24)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    shown.forEach { app ->
                        RadioLine(app.name, selected = layout.appPackage == app.packageName) {
                            layout = layout.copy(appPackage = app.packageName, appLabel = app.name)
                        }
                    }
                }
                if (!showAllApps && apps.size > shown.size) {
                    Spacer(Modifier.height(6.dp))
                    Chip("Show all ${apps.size} apps", accent = false) { showAllApps = true }
                }
                MutedNote("Some apps refuse to open on a second screen; Voltcap tells you when that happens.")
            }
        }
    }
}

/** "Screens" line for a game's options: the layout it will start with. */
fun screensRowSubtitle(resolved: ResolvedLayout): String =
    "${resolved.layout.name} — ${resolved.reason { ConsoleNames.display(it) }}"


/** Settings › Screens › While you're in Voltcap: the second screen's page and dock. */
@Composable
private fun SecondHomeSettings(
    appearance: com.shelf.launcher.data.AppearanceSettings,
    onAppearanceChange: (com.shelf.launcher.data.AppearanceSettings) -> Unit
) {
    val apps by com.shelf.launcher.data.InstalledAppsHub.apps.collectAsState()
    val names = remember(apps) { apps.associate { (it.packageName ?: "") to it.name } }
    var pickingSlot by remember { mutableStateOf<Int?>(null) }
    // Dense list of docked apps; the slot after the last one is "add".
    val docked = appearance.secondDockApps.filter { it.isNotBlank() }.distinct()
    val slots = docked + ""

    SettingsHeading("While you're in Voltcap")
    ShelfCard {
        SectionLabel("The second screen shows")
        MutedNote("For the game you're on. You can also switch with the tabs on the second screen itself.")
        Spacer(Modifier.height(8.dp))
        ChipFlow {
            com.shelf.launcher.data.SecondPage.values().filter { it.available() || appearance.secondPage == it }.forEach { p ->
                Chip(p.label, accent = appearance.secondPage == p) { onAppearanceChange(appearance.copy(secondPage = p)) }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    SwitchRow(
        icon = Icons.Filled.ViewCarousel,
        title = "Arrows between pages (only the current page at the top)",
        subtitle = "The second screen shows just this page's name, with a subtle arrow on each side to the others, instead of every tab. Swiping still flips pages. On by default.",
        checked = appearance.secondPageTitleOnly,
        onChange = { onAppearanceChange(appearance.copy(secondPageTitleOnly = it)) }
    )
    Spacer(Modifier.height(10.dp))
    SwitchRow(
        icon = Icons.Filled.ViewCarousel,
        title = "Game window on the second screen",
        subtitle = "A on a game opens its details window as a Game page on the second screen (B or the arrows leave it). Off: it opens on the main screen.",
        checked = appearance.secondDetails,
        onChange = { onAppearanceChange(appearance.copy(secondDetails = it)) }
    )
    Spacer(Modifier.height(10.dp))
    SwitchRow(
        icon = Icons.Filled.ViewCarousel,
        title = "Music effects on the second screen",
        subtitle = "A music theme's scene and the corner visualizers play behind the second screen's pages; the main screen keeps a still frame, so the games never cover them.",
        checked = appearance.musicFxSecond,
        onChange = { onAppearanceChange(appearance.copy(musicFxSecond = it)) }
    )
    Spacer(Modifier.height(10.dp))
    SwitchRow(
        icon = Icons.Filled.Add,
        title = "App dock on the second screen",
        subtitle = "Up to eight apps of yours, all apps and a settings button along the bottom of the second screen.",
        checked = appearance.secondDock,
        onChange = { onAppearanceChange(appearance.copy(secondDock = it)) }
    )
    if (appearance.secondDock) {
        Spacer(Modifier.height(10.dp))
        ShelfCard {
            SectionLabel("Dock apps")
            MutedNote("Tap an app to change or remove it. On the second screen itself, use the + in the dock to add one, and long-press an icon to move or remove it.")
            Spacer(Modifier.height(8.dp))
            ChipFlow {
                slots.forEachIndexed { i, pkg ->
                    if (pkg.isNotBlank() || docked.size < 8) {
                        Chip(
                            if (pkg.isBlank()) "+ Add an app" else "${i + 1}: " + (names[pkg] ?: pkg.substringAfterLast('.')),
                            accent = pkg.isNotBlank()
                        ) { pickingSlot = i }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // (Update 319: the dock's one-colour icon pack.)
            SwitchRow(
                icon = Icons.Filled.Add,
                title = "Icons in the theme's colour",
                subtitle = "On (default): every app and shortcut in the dock as one symbol in the theme's accent, nothing behind it — follows the theme, the game's or the album's colours. Off: the apps' own colour icons.",
                checked = com.shelf.launcher.data.Features.secondDockMono.state.value,
                onChange = { com.shelf.launcher.data.Features.secondDockMono.value = it }
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel("Dock apps open on")
            ChipFlow {
                Chip("The second screen", accent = appearance.secondDockOnSecond) {
                    onAppearanceChange(appearance.copy(secondDockOnSecond = true))
                }
                Chip("The main screen", accent = !appearance.secondDockOnSecond) {
                    onAppearanceChange(appearance.copy(secondDockOnSecond = false))
                }
            }
        }
    }

    pickingSlot?.let { slot ->
        var showAll by remember { mutableStateOf(false) }
        ShelfDialog(
            icon = Icons.Filled.Add,
            title = "Dock slot ${slot + 1}",
            subtitle = slots.getOrElse(slot) { "" }.let { if (it.isBlank()) "Empty" else names[it] ?: it },
            onDismiss = { pickingSlot = null }
        ) {
            fun set(pkg: String) {
                // "" removes the app in this slot; a blank slot (the + one) appends.
                val updated = docked.toMutableList()
                if (pkg.isBlank()) { if (slot < updated.size) updated.removeAt(slot) }
                else if (slot < updated.size) updated[slot] = pkg
                else updated.add(pkg)
                onAppearanceChange(appearance.copy(secondDockApps = updated.distinct().take(8)))
                pickingSlot = null
            }
            if (slots.getOrElse(slot) { "" }.isNotBlank()) RadioLine("Remove from dock", selected = false) { set("") }
            val shown = if (showAll) apps else apps.take(30)
            shown.forEach { app ->
                RadioLine(app.name, selected = slots.getOrElse(slot) { "" } == app.packageName) { set(app.packageName.orEmpty()) }
            }
            if (!showAll && apps.size > shown.size) {
                Spacer(Modifier.height(6.dp))
                Chip("Show all ${apps.size} apps", accent = false) { showAll = true }
            }
        }
    }
}

/**
 * Settings › Screens › Apps open on: the default screen for plain apps (games
 * follow the layouts above). A single app can override this; emulators can
 * have their own default.
 */
@Composable
private fun AppsOpenOnCard() {
    val context = LocalContext.current
    val store = remember { com.shelf.launcher.data.AppDisplayStore(context) }
    var version by remember { mutableIntStateOf(0) }
    val global = remember(version) { store.globalTarget() }
    val emulators = remember(version) { store.categoryTarget(com.shelf.launcher.data.AppDisplayCategories.EMULATOR) }
    SettingsHeading("Apps open on")
    ShelfCard {
        SectionLabel("Apps")
        ChipFlow {
            com.shelf.launcher.data.AppDisplayTarget.values().forEach { t ->
                Chip(t.label, accent = global == t) { store.setGlobalTarget(t); version++ }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("Emulators")
        ChipFlow {
            Chip("Same as apps", accent = emulators == null) {
                store.setCategoryTarget(com.shelf.launcher.data.AppDisplayCategories.EMULATOR, null); version++
            }
            com.shelf.launcher.data.AppDisplayTarget.values().forEach { t ->
                Chip(t.label, accent = emulators == t) {
                    store.setCategoryTarget(com.shelf.launcher.data.AppDisplayCategories.EMULATOR, t); version++
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MutedNote(
            "If an app is already open on the other screen, Android may just bring that one forward. " +
                "Voltcap opens a second copy on the screen you asked for, except for emulators."
        )
    }
}
