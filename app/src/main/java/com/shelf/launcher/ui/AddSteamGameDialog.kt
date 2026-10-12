package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.GameNativeConfig
import com.shelf.launcher.ui.theme.ShelfTheme

@Composable
fun AddSteamGameDialog(
    existingSteamGames: List<Game>,
    gameNativeConfig: GameNativeConfig,
    onAdd: (name: String, steamAppId: Int) -> Unit,
    onRemove: (steamAppId: Int) -> Unit,
    onImport: (List<com.shelf.launcher.data.SteamGameEntry>) -> Int,
    onSaveGameNativeConfig: (GameNativeConfig) -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("AddSteamGameDialog")
    var name by remember { mutableStateOf("") }
    var appIdText by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    var packageName by remember { mutableStateOf(gameNativeConfig.packageName) }
    var activityName by remember { mutableStateOf(gameNativeConfig.activityClassName) }
    var action by remember { mutableStateOf(gameNativeConfig.action) }
    var extraKey by remember { mutableStateOf(gameNativeConfig.appIdExtraKey) }

    ShelfDialog(
        icon = Icons.Filled.SportsEsports,
        title = "Steam games",
        subtitle = "Launched through GameNative.",
        onDismiss = onDismiss
    ) {
        ShelfCard {
            SectionLabel("Add a game")
            MutedNote(
                "Use the game's name and its Steam AppID — the number in the store URL, e.g. the " +
                    "570 in store.steampowered.com/app/570. The AppID is also used to fetch exact " +
                    "art from SteamGridDB."
            )
            Spacer(Modifier.height(12.dp))
            ShelfTextField(name, { name = it }, "Game name")
            Spacer(Modifier.height(8.dp))
            ShelfTextField(appIdText, { appIdText = it }, "Steam AppID", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(12.dp))
            val id = appIdText.trim().toIntOrNull()
            Chip("Add game", enabled = name.isNotBlank() && id != null, onClick = {
                if (id != null && name.isNotBlank()) {
                    onAdd(name.trim(), id)
                    name = ""
                    appIdText = ""
                }
            })
        }

        Spacer(Modifier.height(12.dp))
        ActionCard(
            Icons.Filled.Folder,
            "Import from a folder…",
            "Find Steam shortcuts and installed games in a folder",
            onClick = { showImport = true }
        )

        if (existingSteamGames.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ShelfCard {
                SectionLabel("Added (${existingSteamGames.size})")
                existingSteamGames.forEach { game ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            game.name,
                            color = ShelfTheme.colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Chip("Remove", accent = false, onClick = { game.steamAppId?.let(onRemove) })
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ActionCard(
            Icons.Filled.Settings,
            if (showAdvanced) "Hide GameNative settings" else "GameNative settings",
            "Only needed for forks with a different package name",
            onClick = { showAdvanced = !showAdvanced }
        )
        if (showAdvanced) {
            Spacer(Modifier.height(12.dp))
            ShelfCard {
                MutedNote(
                    "Defaults match GameNative's documented launch intent. Only change these if " +
                        "you're on a fork (e.g. GameNative CE) with a different package name."
                )
                Spacer(Modifier.height(10.dp))
                ShelfTextField(packageName, { packageName = it }, "Package name")
                Spacer(Modifier.height(8.dp))
                ShelfTextField(activityName, { activityName = it }, "Activity class")
                Spacer(Modifier.height(8.dp))
                ShelfTextField(action, { action = it }, "Intent action")
                Spacer(Modifier.height(8.dp))
                ShelfTextField(extraKey, { extraKey = it }, "AppID extra key")
                Spacer(Modifier.height(12.dp))
                Chip("Save GameNative settings", onClick = {
                    onSaveGameNativeConfig(
                        GameNativeConfig(
                            packageName = packageName.ifBlank { GameNativeConfig.DEFAULT_PACKAGE },
                            activityClassName = activityName.ifBlank { GameNativeConfig.DEFAULT_ACTIVITY },
                            action = action.ifBlank { GameNativeConfig.DEFAULT_ACTION },
                            appIdExtraKey = extraKey.ifBlank { GameNativeConfig.DEFAULT_APP_ID_EXTRA_KEY }
                        )
                    )
                })
            }
        }
    }

    if (showImport) {
        SteamImportDialog(
            existingAppIds = existingSteamGames.mapNotNull { it.steamAppId }.toSet(),
            onImport = onImport,
            onDismiss = { showImport = false }
        )
    }
}
