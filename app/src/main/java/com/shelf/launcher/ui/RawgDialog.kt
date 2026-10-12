package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** RAWG, used alongside the main details source for Metacritic scores and an
 * average playtime where IGDB has none (common for retro games). */
@Composable
fun RawgDialog(
    connected: Boolean,
    statusMessage: String?,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("RawgDialog")
    var key by remember { mutableStateOf("") }

    ShelfDialog(
        icon = Icons.Filled.Star,
        title = "RAWG",
        subtitle = "Metacritic scores and average playtime.",
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
            ConnectedCard(
                "Game details now show the Metacritic score in the top-right corner, and an average " +
                    "playtime when IGDB has none."
            )
        } else {
            ShelfCard {
                SectionLabel("Setup")
                MutedNote(
                    "1. Sign up for free at rawg.io.\n" +
                        "2. Open rawg.io/apidocs and press \"Get API key\".\n" +
                        "3. Paste the key below.\n\n" +
                        "Works together with IGDB or ScreenScraper: they stay the main source, RAWG adds " +
                        "the Metacritic score and fills in playtime."
                )
            }
            Spacer(Modifier.height(14.dp))
            ShelfTextField(key, { key = it }, "API key", password = true)
        }
        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            MutedNote(it)
        }
    }
}
