package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.RaMatchEntry
import com.shelf.launcher.data.RaMatchSource
import com.shelf.launcher.ui.theme.ShelfTheme

/**
 * How one game is linked to RetroAchievements, and the three ways to change
 * it: re-hash the file, give it a hash by hand (for formats Shelf can't hash,
 * like CHD), or set the game ID directly.
 */
@Composable
fun RaIdDialog(
    game: Game,
    match: RaMatchEntry?,
    manualId: Int?,
    customHash: String?,
    isMatching: Boolean,
    onSaveId: (String) -> Unit,
    onSaveHash: (String) -> Unit,
    onRehash: () -> Unit,
    onDismiss: () -> Unit
) {
    com.shelf.launcher.Breadcrumbs.mark("RaIdDialog")
    var idText by remember { mutableStateOf(manualId?.toString() ?: "") }
    var hashText by remember { mutableStateOf(customHash ?: "") }

    ShelfDialog(
        icon = Icons.Filled.EmojiEvents,
        title = "Achievement matching",
        subtitle = game.name,
        onDismiss = onDismiss,
        maxContentHeightFraction = 0.7f
    ) {
        // --- Current link ---
        ShelfCard {
            val (headline, detail, ok) = when {
                manualId != null -> Triple("Manual ID $manualId", "Set by hand — overrides automatic matching.", true)
                match == null -> Triple(
                    if (isMatching) "Checking…" else "Not checked yet",
                    "Matching runs automatically in the background.", false
                )
                match.kind == RaMatchSource.HASH -> Triple(
                    "Matched by hash", "${match.raTitle ?: "Game"} (ID ${match.gameId}) — the same set the emulator will load.", true
                )
                match.kind == RaMatchSource.CUSTOM_HASH -> Triple(
                    "Matched by your hash", "${match.raTitle ?: "Game"} (ID ${match.gameId}).", true
                )
                match.kind == RaMatchSource.TITLE -> Triple(
                    "Matched by title",
                    "${match.raTitle ?: "Game"} (ID ${match.gameId}). Not verified by hash — if achievements " +
                        "don't match what you see in-game, set a hash or ID below.", true
                )
                else -> Triple("No match", "Nothing on RetroAchievements matched this file.", false)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(ok)
                Spacer(Modifier.width(10.dp))
                Text(headline, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            MutedNote(detail)
        }

        // --- Fingerprint ---
        Spacer(Modifier.height(12.dp))
        ShelfCard {
            SectionLabel("File hash")
            val hash = match?.hash
            if (hash != null) {
                Text(hash, color = ShelfTheme.colors.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
            match?.note?.let {
                Spacer(Modifier.height(4.dp))
                MutedNote(it)
            }
            if (hash == null && match?.note == null) MutedNote("Not computed yet.")
            Spacer(Modifier.height(10.dp))
            Chip(
                if (isMatching) "Working…" else "Re-hash now",
                accent = false,
                enabled = !isMatching && manualId == null,
                onClick = onRehash
            )
        }

        // --- Custom hash ---
        Spacer(Modifier.height(12.dp))
        ShelfCard {
            SectionLabel("Use a specific hash")
            MutedNote(
                "For files Voltcap can't hash itself (like .cso, .pbp or .7z). RetroArch shows the hash in its log " +
                    "when it loads the game, and each game's \"Supported Game Files\" page on " +
                    "retroachievements.org lists every accepted hash. Leave empty to go back to automatic."
            )
            Spacer(Modifier.height(10.dp))
            ShelfTextField(hashText, { hashText = it }, "32-character hash")
            Spacer(Modifier.height(10.dp))
            Chip("Save hash", enabled = !isMatching && manualId == null, onClick = { onSaveHash(hashText) })
        }

        // --- Manual ID ---
        Spacer(Modifier.height(12.dp))
        ShelfCard {
            SectionLabel("Set the game ID directly")
            MutedNote(
                "The number at the end of the game's page URL, e.g. the 14402 in " +
                    "retroachievements.org/game/14402. Leave empty to go back to automatic matching."
            )
            Spacer(Modifier.height(10.dp))
            ShelfTextField(idText, { idText = it }, "RetroAchievements game ID", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(10.dp))
            Chip("Save ID", onClick = { onSaveId(idText) })
        }
    }
}
