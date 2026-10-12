package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.EmulatorConfig
import com.shelf.launcher.data.EmulatorRegistry
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.LaunchMode
import com.shelf.launcher.ui.theme.ShelfTheme

/** Every console in the library and which emulator handles it. */
@Composable
fun EmulatorSettingsDialog(
    platforms: List<String>,
    configs: Map<String, EmulatorConfig>,
    installedApps: List<Game>,
    onEditPlatform: (String) -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("EmulatorSettingsDialog")
    ShelfDialog(
        icon = Icons.Filled.VideogameAsset,
        title = "Emulators",
        subtitle = "Pick which app launches each system.",
        onDismiss = onDismiss,
        footer = { Chip("Back", onClick = onDismiss) }
    ) {
        if (platforms.isEmpty()) {
            ShelfCard {
                EmptyState(
                    "No consoles yet — scan a ROM folder from Settings › Library and the systems " +
                        "found will show up here."
                )
            }
        } else {
            platforms.forEachIndexed { index, platform ->
                val config = configs[platform]
                val assigned = when {
                    config == null -> null
                    config.mode == LaunchMode.SYSTEM_CHOOSER -> "Android asks every time"
                    // Update 144: an installed app shows its own name; the known emulators' names are for ones not installed.
                    else -> installedApps.firstOrNull { it.packageName == config.packageName }?.name?.takeIf { it.isNotBlank() }
                        ?: EmulatorRegistry.displayNameFor(config.packageName)
                        ?: config.packageName
                }
                if (index > 0) Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ShelfTheme.colors.surfaceRaised)
                        .shelfClickable(RoundedCornerShape(16.dp)) { onEditPlatform(platform) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(assigned != null)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(platform, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            assigned ?: "Not set — select to choose",
                            color = if (assigned != null) ShelfTheme.textMuted else ShelfTheme.accent,
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = ShelfTheme.textMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
