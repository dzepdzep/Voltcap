package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Flag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.PlayStatus

/** Start › Collections: tick the collections [game] is in, or make a new one. */
@Composable
internal fun CollectionPickerDialog(
    game: Game,
    collections: Map<String, Set<String>>,
    onToggle: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    ShelfDialog(
        icon = Icons.Filled.CollectionsBookmark,
        title = "Collections",
        subtitle = gameDisplayName(game.name),
        onDismiss = onDismiss
    ) {
        if (collections.isEmpty()) {
            EmptyState("No collections yet — make the first one below (\"Backlog\", \"Couch co-op\", \"Comfort games\"…).")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                collections.keys.sortedBy { it.lowercase() }.forEach { name ->
                    val inIt = game.stableKey in collections[name].orEmpty()
                    MenuRow(
                        if (inIt) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        name,
                        "${collections[name].orEmpty().size} games"
                    ) { onToggle(name) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ShelfCard {
            SectionLabel("New collection")
            ShelfTextField(value = newName, onValueChange = { newName = it }, label = "Name", onSubmit = {
                if (newName.isNotBlank()) { onCreate(newName); newName = "" }
            })
            Spacer(Modifier.height(8.dp))
            Chip("Create and add this game", enabled = newName.isNotBlank()) {
                onCreate(newName)
                newName = ""
            }
        }
    }
}

/** Start › Status: where you are with a game. */
@Composable
internal fun PlayStatusDialog(
    game: Game,
    current: PlayStatus?,
    onPick: (PlayStatus?) -> Unit,
    onDismiss: () -> Unit
) {
    ShelfMenuDialog(
        title = "Status",
        subtitle = gameDisplayName(game.name),
        onDismiss = onDismiss,
        icon = Icons.Filled.Flag
    ) {
        PlayStatus.values().forEach { st ->
            MenuRow(
                if (st == current) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                "${st.glyph}  ${st.label}",
                when (st) {
                    PlayStatus.PLAYING -> "On the go right now"
                    PlayStatus.FINISHED -> "Done — the random pick skips it"
                    PlayStatus.DROPPED -> "Not for you — the random pick skips it"
                    PlayStatus.WISHLIST -> "Want to play it"
                    PlayStatus.BACKLOG -> "Lined up to play next — \"What should I play?\" favours it"
                }
            ) { onPick(st) }
        }
        if (current != null) MenuRow(Icons.Filled.CheckBoxOutlineBlank, "No status", destructive = true) { onPick(null) }
    }
}
