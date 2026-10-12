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
fun ScreenScraperDialog(
    connected: Boolean,
    username: String?,
    statusMessage: String?,
    onConnect: (devId: String, devPassword: String, username: String, password: String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("ScreenScraperDialog")
    var devId by remember { mutableStateOf("") }
    var devPassword by remember { mutableStateOf("") }
    var user by remember { mutableStateOf(username ?: "") }
    var password by remember { mutableStateOf("") }

    ShelfDialog(
        icon = Icons.Filled.Image,
        title = "ScreenScraper",
        subtitle = "Box art, screenshots, fan art and logos for ROMs.",
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.7f,
        footer = {
            Chip("Back", accent = false, onClick = onDismiss)
            if (connected) {
                Chip("Disconnect", accent = false, onClick = onDisconnect)
            } else {
                Chip(
                    "Connect",
                    enabled = devId.isNotBlank() && devPassword.isNotBlank(),
                    onClick = { onConnect(devId, devPassword, user, password) }
                )
            }
        }
    ) {
        if (connected) {
            ConnectedCard(
                title = if (username != null) "Connected as $username" else "Connected",
                detail = "Choose ScreenScraper under Settings › Metadata › Artwork source to try it first, " +
                    "or leave it as a fallback. Then run \"Re-scrape everything\" to refresh existing art. " +
                    "It only covers ROMs — apps keep SteamGridDB or their own icon."
            )
        } else {
            ShelfCard {
                SectionLabel("Developer key (once per app)")
                MutedNote(
                    "ScreenScraper gives each app its own developer ID and password. Request one for " +
                        "Voltcap on screenscraper.fr › Forum › \"Demande d'accès API\" with a short description " +
                        "of the app. Established frontends ship their own key, which is why you may never " +
                        "have been asked for one before."
                )
                Spacer(Modifier.height(10.dp))
                ShelfTextField(devId, { devId = it }, "Developer ID")
                Spacer(Modifier.height(8.dp))
                ShelfTextField(devPassword, { devPassword = it }, "Developer password", password = true)
            }
            Spacer(Modifier.height(12.dp))
            ShelfCard {
                SectionLabel("Your ScreenScraper account (optional)")
                MutedNote(
                    "Your normal, free screenscraper.fr login. Without it scraping still works, but with a " +
                        "much smaller daily limit and slower responses when the site is busy."
                )
                Spacer(Modifier.height(10.dp))
                ShelfTextField(user, { user = it }, "Username")
                Spacer(Modifier.height(8.dp))
                ShelfTextField(password, { password = it }, "Password", password = true)
            }
        }
        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            MutedNote(it)
        }
    }
}
