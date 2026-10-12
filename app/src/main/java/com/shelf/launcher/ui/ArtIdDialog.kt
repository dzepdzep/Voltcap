package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Game

@Composable
fun ArtIdDialog(
    game: Game,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("ArtIdDialog")
    var idText by remember { mutableStateOf(game.sgdbGameId?.toString() ?: "") }

    ShelfDialog(
        icon = Icons.Filled.Image,
        title = "Link SteamGridDB",
        subtitle = game.name,
        onDismiss = onDismiss,
        footer = {
            Chip("Cancel", accent = false, onClick = onDismiss)
            Chip("Save", onClick = { onSave(idText) })
        }
    ) {
        MutedNote(
            "Search for \"${game.name}\" on steamgriddb.com, open its page, and copy the number at " +
                "the end of the URL — e.g. the 666 in steamgriddb.com/game/666. Leave blank to fall " +
                "back to automatic name search."
        )
        Spacer(Modifier.height(14.dp))
        ShelfTextField(idText, { idText = it }, "SteamGridDB game ID", keyboardType = KeyboardType.Number)
    }
}
