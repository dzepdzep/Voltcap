package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.PlaySuggest

/**
 * "What should I play?": three picks from the category you're in, each with why it was picked.
 * A picks one (selects it and opens its details); "Something else" rolls three new ones.
 */
@Composable
internal fun WhatToPlayDialog(
    suggest: (exclude: Set<String>) -> List<PlaySuggest.Pick>,
    onPick: (Game) -> Unit,
    onDismiss: () -> Unit
) {
    var shown by remember { mutableStateOf(emptySet<String>()) }
    var picks by remember { mutableStateOf(suggest(emptySet())) }
    ShelfDialog(
        icon = Icons.Filled.Casino,
        title = "What should I play?",
        subtitle = "From the category you're in",
        onDismiss = onDismiss,
        widthFraction = 0.6f,
        footer = {
            Chip("Something else", accent = false) {
                shown = shown + picks.map { it.game.stableKey }
                val next = suggest(shown)
                if (next.isEmpty()) { shown = emptySet(); picks = suggest(emptySet()) } else picks = next
            }
            Chip("Close", onClick = onDismiss)
        }
    ) {
        if (picks.isEmpty()) EmptyState("Nothing to suggest here — everything is finished, dropped or mastered.")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            picks.forEach { p ->
                MenuRow(Icons.Filled.PlayArrow, gameDisplayName(p.game.name), p.reason) { onPick(p.game) }
            }
        }
    }
}
