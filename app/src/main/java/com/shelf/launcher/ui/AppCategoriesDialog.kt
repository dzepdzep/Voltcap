package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.AppCategories
import com.shelf.launcher.data.AppKind
import com.shelf.launcher.data.ConsoleNames
import com.shelf.launcher.data.Game

/**
 * 725a (owner Task 4): START > Categories on an installed app or Android game - where it is listed, several at once
 * (Android games or Other apps, Recomps, consoles such as Nintendo 64, your collections). Every press applies at once and the
 * home shows it straight away; your choice beats the automatic one. Only the listing changes: it starts, keeps its saves,
 * art, play time and achievements as before, and appears once in All games. D-pad / touch: plain MenuRows, Play-free menu.
 */
@Composable
internal fun AppCategoriesDialog(
    game: Game,
    /** What it counts as now (after your choice) and what you chose (AUTO = nothing chosen). */
    androidGame: Boolean,
    kind: AppKind,
    recomp: Boolean,
    consoles: Set<String>,
    libraryPlatforms: List<String>,
    collectionsOn: Boolean,
    collectionCount: Int,
    onKind: (AppKind) -> Unit,
    onRecomp: (Boolean) -> Unit,
    onConsole: (String, Boolean) -> Unit,
    onCollections: () -> Unit,
    onDismiss: () -> Unit
) {
    var more by rememberSaveable(game.stableKey) { mutableStateOf(false) }
    ShelfMenuDialog(
        title = "Categories",
        subtitle = gameDisplayName(game.name),
        onDismiss = onDismiss,
        icon = Icons.Filled.Category
    ) {
        MutedNote("Tick every category it belongs in. It starts, saves and keeps its art and play time as before, and is in All games once.")
        SectionLabel("Android games or Other apps")
        val autoSays = if (kind == AppKind.AUTO) (if (androidGame) "Android games" else "Other apps") else null
        KindRow("Automatic", kind == AppKind.AUTO, "Voltcap decides" + (autoSays?.let { " - now: $it" } ?: ""), first = true) { onKind(AppKind.AUTO) }
        KindRow("Android games", kind == AppKind.GAME, "Set by you: a game, whatever Android says") { onKind(AppKind.GAME) }
        KindRow("Other apps", kind == AppKind.APP, "Set by you: an app - with Spotify, YouTube and the rest") { onKind(AppKind.APP) }
        Spacer(Modifier.height(4.dp))
        CheckRow("Recomps", recomp, "A recompiled game or native port that runs on Android") { onRecomp(!recomp) }
        SectionLabel("Consoles")
        val choices = AppCategories.consoleChoices(libraryPlatforms, consoles, all = more)
        if (choices.isEmpty() && !more) MutedNote("No consoles in your library yet - open More consoles for the full list.")
        choices.forEach { p ->
            val on = p in consoles
            CheckRow(ConsoleNames.display(p), on, if (on) "Listed with its games too" else null) { onConsole(p, !on) }
        }
        if (!more) MenuRow(Icons.Filled.ExpandMore, "More consoles…", "Every console Voltcap knows") { more = true }
        if (collectionsOn) {
            Spacer(Modifier.height(4.dp))
            MenuRow(
                Icons.Filled.CollectionsBookmark,
                "Collections…",
                when (collectionCount) { 0 -> "Add it to one of your own lists"; 1 -> "In 1 collection"; else -> "In $collectionCount collections" },
                onClick = onCollections
            )
        }
    }
}

@Composable
private fun KindRow(label: String, selected: Boolean, blurb: String, first: Boolean = false, onClick: () -> Unit) =
    MenuRow(if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked, label, blurb, focusFirst = first, onClick = onClick)

@Composable
private fun CheckRow(label: String, on: Boolean, blurb: String?, onClick: () -> Unit) =
    MenuRow(if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, label, blurb, onClick = onClick)
