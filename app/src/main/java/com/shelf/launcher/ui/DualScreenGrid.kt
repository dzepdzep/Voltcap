package com.shelf.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Update 122: "Games below" / "Swap screens" and their games grid on the second screen are gone (the second screen
 * keeps its own pages, with a Games page of console folders: SecondGamesPage.kt). Only the game's art across a screen,
 * darker towards the bottom left, is left here; the style homes use it behind their panels (StyleHomes.kt).
 */

/** The game's art across the whole screen, darker towards the bottom left. */
@Composable
internal fun DualGridArt(gameKey: String?, url: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        // Held, not crossfaded (update 104): no black flash between two games' pictures.
        HeldImage(url, Modifier.fillMaxSize(), imageModifier = Modifier.pixelArtwork())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.08f), 1f to Color.Black.copy(alpha = 0.62f))
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.38f), 0.55f to Color.Transparent)
            )
        )
    }
}
