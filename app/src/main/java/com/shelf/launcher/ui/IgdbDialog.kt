package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IgdbDialog(
    connected: Boolean,
    statusMessage: String?,
    onConnect: (clientId: String, clientSecret: String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("IgdbDialog")
    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }

    ShelfDialog(
        icon = Icons.Filled.Info,
        title = "IGDB",
        subtitle = "Summaries, critic scores and how-long-to-beat times.",
        onDismiss = onDismiss,
        footer = {
            Chip("Back", accent = false, onClick = onDismiss)
            if (connected) {
                Chip("Disconnect", accent = false, onClick = onDisconnect)
            } else {
                Chip(
                    "Connect",
                    enabled = clientId.isNotBlank() && clientSecret.isNotBlank(),
                    onClick = { onConnect(clientId, clientSecret) }
                )
            }
        }
    ) {
        if (connected) {
            ConnectedCard("Press A on any game to see its score, summary and how long it takes to beat.")
        } else {
            ShelfCard {
                SectionLabel("Setup")
                MutedNote(
                    "1. Sign in at dev.twitch.tv/console (IGDB is owned by Twitch).\n" +
                        "2. Register an application: any name, OAuth redirect http://localhost, " +
                        "category \"Application Integration\".\n" +
                        "3. Copy the Client ID, click \"New Secret\", and paste both below."
                )
            }
            Spacer(Modifier.height(14.dp))
            ShelfTextField(clientId, { clientId = it }, "Client ID")
            Spacer(Modifier.height(8.dp))
            ShelfTextField(clientSecret, { clientSecret = it }, "Client Secret", password = true)
        }
        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            MutedNote(it)
        }
    }
}
