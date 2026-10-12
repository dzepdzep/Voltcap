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
import androidx.compose.ui.unit.dp

@Composable
fun TgdbDialog(
    connected: Boolean,
    statusMessage: String?,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("TgdbDialog")
    var key by remember { mutableStateOf("") }

    ShelfDialog(
        icon = Icons.Filled.Image,
        title = "TheGamesDB",
        subtitle = "Alternative art source with strong retro coverage.",
        onDismiss = onDismiss,
        footer = {
            Chip("Back", accent = false, onClick = onDismiss)
            if (connected) {
                Chip("Disconnect", accent = false, onClick = onDisconnect)
            } else {
                Chip("Connect", enabled = key.isNotBlank(), onClick = { onConnect(key) })
            }
        }
    ) {
        if (connected) {
            ConnectedCard("Choose which source is tried first under Settings › Metadata › Artwork source.")
        } else {
            MutedNote(
                "Box art, fanart, banners and logos. Request a public API key at " +
                    "thegamesdb.net (Account › API Access) and paste it here."
            )
            Spacer(Modifier.height(14.dp))
            ShelfTextField(key, { key = it }, "API key", password = true)
        }
        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            MutedNote(it)
        }
    }
}
