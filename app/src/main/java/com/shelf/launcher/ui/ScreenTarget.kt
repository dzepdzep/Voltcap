package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Game
import com.shelf.launcher.LauncherViewModel
import com.shelf.launcher.data.AppDisplaySource
import com.shelf.launcher.data.AppDisplayTarget
import com.shelf.launcher.data.RuleScope
import com.shelf.launcher.data.ScreenLayouts
import com.shelf.launcher.data.ScreenSetup
import com.shelf.launcher.data.ScreenSide

/*
 * 676j (v0.6.74, owner 16 Nov - "Open on screen" in every app context menu): one row and one picker for an app's / Android game's
 * screen, used by the All apps drawer's menu and the second-screen dock's menu (the game options page has had it since before).
 * No new setting: a plain app's choice is its AppDisplayStore entry (this app > its category > the default); an Android game -
 * which starts through the game path - gets a game-scope Screen layouts rule (Top / Main = "Game + info", Bottom / Second =
 * "Game on the other screen + info"), and "Screen layouts…" opens the full rules (collection / console / emulator).
 * Opening the row or choosing never starts the app.
 */

internal enum class ScreenPick { FIRST, SECOND, WHERE_LAUNCHED, DEFAULT }

/** What the two screens are called here: Thor "Top" / "Bottom"; a TV setup "Handheld" / "TV"; else "Main" / "Second". */
internal data class ScreenNames(val first: String, val second: String, val oneScreen: Boolean)

internal fun screenNamesFor(model: String?, setup: ScreenSetup?, hasSecond: Boolean): ScreenNames = when {
    setup == ScreenSetup.TV -> ScreenNames("Handheld", "TV", !hasSecond)
    model.orEmpty().contains("Thor", ignoreCase = true) -> ScreenNames("Top screen", "Bottom screen", !hasSecond)
    else -> ScreenNames("Main screen", "Second screen", !hasSecond)
}

/** The item's choice now: which pick is ticked (null: one the quick picks don't cover), and the row's line. */
internal data class ScreenTargetNow(val picked: ScreenPick?, val summary: String)

/** A plain app (AppDisplayStore): its own choice, or what it inherits. */
internal fun appScreenNow(target: AppDisplayTarget, source: AppDisplaySource, names: ScreenNames): ScreenTargetNow {
    val where = when (target) {
        AppDisplayTarget.MAIN -> names.first
        AppDisplayTarget.SECOND -> names.second
        AppDisplayTarget.WHERE_LAUNCHED -> "where you open it"
    }
    return if (source == AppDisplaySource.APP) ScreenTargetNow(
        when (target) { AppDisplayTarget.MAIN -> ScreenPick.FIRST; AppDisplayTarget.SECOND -> ScreenPick.SECOND; AppDisplayTarget.WHERE_LAUNCHED -> ScreenPick.WHERE_LAUNCHED },
        "Set for this app: $where"
    ) else ScreenTargetNow(ScreenPick.DEFAULT, "Follows " + (if (source == AppDisplaySource.CATEGORY) "the Emulators default" else "the default") + ": $where")
}

/** An Android game (Screen layouts): a rule of its own, or the one it inherits. */
internal fun gameScreenNow(gameOn: ScreenSide, bothScreens: Boolean, ownRule: Boolean, reason: String, layoutName: String, names: ScreenNames): ScreenTargetNow {
    val where = if (bothScreens) "both screens ($layoutName)" else if (gameOn == ScreenSide.MAIN) names.first else names.second
    return if (ownRule) ScreenTargetNow(if (bothScreens) null else if (gameOn == ScreenSide.MAIN) ScreenPick.FIRST else ScreenPick.SECOND, "Set for this game: $where")
    else ScreenTargetNow(ScreenPick.DEFAULT, "Follows $reason: $where")
}

/** The Screen layouts rule a quick pick sets for an Android game (null: the game's own rule removed). */
internal fun gameLayoutFor(pick: ScreenPick): String? = when (pick) {
    ScreenPick.FIRST -> ScreenLayouts.INFO
    ScreenPick.SECOND -> ScreenLayouts.FLIPPED
    ScreenPick.WHERE_LAUNCHED, ScreenPick.DEFAULT -> null
}

/** The AppDisplayStore choice a quick pick sets for a plain app (null: the app's own choice removed). */
internal fun appTargetFor(pick: ScreenPick): AppDisplayTarget? = when (pick) {
    ScreenPick.FIRST -> AppDisplayTarget.MAIN
    ScreenPick.SECOND -> AppDisplayTarget.SECOND
    ScreenPick.WHERE_LAUNCHED -> AppDisplayTarget.WHERE_LAUNCHED
    ScreenPick.DEFAULT -> null
}

private fun LauncherViewModel.screenNames(): ScreenNames {
    val now = currentScreen()
    return screenNamesFor(android.os.Build.MODEL, now?.second, now != null)
}

private fun LauncherViewModel.screenNow(app: Game, names: ScreenNames): ScreenTargetNow {
    if (uiState.value.isAndroidGame(app)) {
        val plan = screenPlanFor(app)
            ?: return ScreenTargetNow(ScreenPick.DEFAULT, "One screen now: it opens here")
        val r = plan.resolved
        return gameScreenNow(r.layout.gameOn, r.layout.emulatorUsesBoth, r.scope == RuleScope.GAME,
            r.reason { com.shelf.launcher.data.ConsoleNames.short(it) }, r.layout.name, names)
    }
    val (target, source) = effectiveAppDisplay(app)
    return appScreenNow(target, source, names)
}

private fun LauncherViewModel.setScreenPick(app: Game, pick: ScreenPick) {
    if (uiState.value.isAndroidGame(app)) setScreenRule(RuleScope.GAME, app.stableKey, gameLayoutFor(pick))
    else app.packageName?.let { setAppDisplay(it, appTargetFor(pick)) }
}

/**
 * "Open on screen" in an app's menu: the current choice (its own, or what it follows) and, on A / tap, the picker. The picker
 * saves at once and never starts the app. [onScreenLayouts]: the full Screen layouts for an Android game (null: not offered).
 */
@Composable
internal fun ScreenTargetRow(app: Game, viewModel: LauncherViewModel, onScreenLayouts: ((Game) -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    val names = remember(version) { viewModel.screenNames() }
    val now = remember(version, app.stableKey) { viewModel.screenNow(app, names) }
    MenuRow(Icons.Filled.Layers, "Open on screen",
        now.summary + if (names.oneScreen) " · one screen connected now" else "") { open = true }
    if (!open) return
    val game = viewModel.uiState.value.isAndroidGame(app)
    ShelfDialog(icon = Icons.Filled.Layers, title = "Open on screen", subtitle = app.name, onDismiss = { open = false }, widthFraction = 0.6f) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            fun pick(p: ScreenPick) { viewModel.setScreenPick(app, p); version++; open = false }
            RadioLine(names.first, now.picked == ScreenPick.FIRST, if (game) "Game + Voltcap info on the other screen" else null) { pick(ScreenPick.FIRST) }
            RadioLine(names.second, now.picked == ScreenPick.SECOND, if (game) "Game there + Voltcap info on the other screen" else null) { pick(ScreenPick.SECOND) }
            if (!game) RadioLine("Where I open it", now.picked == ScreenPick.WHERE_LAUNCHED, "The screen you tap it on") { pick(ScreenPick.WHERE_LAUNCHED) }
            RadioLine("Follow default", now.picked == ScreenPick.DEFAULT, if (game) "Collection / console / emulator rules, then your default" else "This app's category, then your default") { pick(ScreenPick.DEFAULT) }
            if (names.oneScreen) MutedNote("Only one screen is connected now: the choice is kept for when a second screen is there.")
            if (game && onScreenLayouts != null) MenuRow(Icons.Filled.Dashboard, "Screen layouts…", "What each screen shows, and rules for its collection, console or emulator") {
                open = false; onScreenLayouts(app)
            }
        }
    }
}
