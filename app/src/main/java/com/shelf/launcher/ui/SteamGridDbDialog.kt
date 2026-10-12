package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.ArtConnectionState
import com.shelf.launcher.ui.theme.ShelfTheme

@Composable
fun SteamGridDbDialog(
    state: ArtConnectionState,
    statusMessage: String?,
    isKeyEncrypted: Boolean,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("SteamGridDbDialog")
    var key by remember { mutableStateOf("") }
    val connected = state == ArtConnectionState.ON

    ShelfDialog(
        icon = Icons.Filled.Image,
        title = "SteamGridDB",
        subtitle = "Covers, backgrounds, wide capsules and logos.",
        onDismiss = onDismiss,
        footer = {
            Chip("Back", accent = false, onClick = onDismiss)
            if (connected) {
                Chip("Disconnect", accent = false, onClick = onDisconnect)
            } else {
                Chip(
                    if (state == ArtConnectionState.CONNECTING) "Connecting…" else "Connect",
                    enabled = key.isNotBlank() && state != ArtConnectionState.CONNECTING,
                    // (update 91) A pasted key often carries a space or line break: it would break the auth header.
                    onClick = { onConnect(key.trim()) }
                )
            }
        }
    ) {
        if (connected) {
            ConnectedCard(
                "Art is being fetched from SteamGridDB. The key is " +
                    (if (isKeyEncrypted) "encrypted" else "saved (unencrypted fallback)") + " for next time."
            )
        } else {
            MutedNote("Get a free key at steamgriddb.com › Profile › Preferences › API, then paste it here.")
            Spacer(Modifier.height(14.dp))
            ShelfTextField(key, { key = it }, "API key", password = true)
        }
        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            MutedNote(it)
        }
    }
}

/** "Connected" status card shared by the account dialogs. */
@Composable
internal fun ConnectedCard(detail: String, title: String = "Connected") {
    com.shelf.launcher.Breadcrumbs.mark("ConnectedCard")
    ShelfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(true)
            Spacer(Modifier.width(10.dp))
            Text(title, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        MutedNote(detail)
    }
}
