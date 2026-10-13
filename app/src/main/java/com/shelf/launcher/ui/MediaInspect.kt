package com.shelf.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Update 162: opening a game to inspect it — its physical piece (box, cartridge, card, disc…) lifts out of the
 * carousel, comes forward and turns so you can see it from every side, the game's actions beside it. The game window
 * drives this; the carousel reads it so the other games step back and the piece's own place stays empty while it's out.
 */
internal object MediaInspect {
    /** 0 = the carousel as usual, 1 = the piece fully out in front (animated by the game window, read while drawing). */
    val amount = mutableFloatStateOf(0f)

    /** The game being inspected (its stableKey), or null. */
    var key by mutableStateOf<String?>(null)

    /**
     * Update 162: the game you're on (its stableKey) while the carousel draws it as a 3D case although the look is a
     * plain cover (the "3D cases" switch, and the Arc / Spine wall / Turned row views); null otherwise. A plain field
     * the carousel sets for its middle game, so opening that game inspects a box rather than keeping the flat window.
     */
    @Volatile var drawnAsCase: String? = null
}
