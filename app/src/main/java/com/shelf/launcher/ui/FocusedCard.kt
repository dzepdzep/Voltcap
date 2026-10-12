package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.shelf.launcher.data.Features
import kotlin.math.abs

/*
 * Update 122: where the game you're on is drawn. Every home layout marks the card it lifts for the game you're on
 * with [focusedCard]; the cover light (HomeExtras.kt) follows it and the game window grows out of it (GameWindow.kt).
 * This used to live with the mascot (VoltCompanion.kt, now gone) and works the same as before.
 */

/** Where the game you're on is (root px), reported by the focused card. */
internal object FocusedCardRect {
    var rect by mutableStateOf<Rect?>(null)
    var owner: Any? = null
}

/** On the focused game card: tells the cover light and the game window where it is. Changes nothing about the card. */
@Composable
internal fun Modifier.focusedCard(): Modifier {
    val token = remember { Any() }
    DisposableEffect(token) {
        onDispose { if (FocusedCardRect.owner === token) { FocusedCardRect.owner = null; FocusedCardRect.rect = null } }
    }
    val origin = this.gameOrigin(GameOrigin.FOCUSED, true)
    return if (!Features.coverLight.state.value) origin else origin.onGloballyPositioned { c ->
        val r = c.boundsInRoot()
        FocusedCardRect.owner = token
        val old = FocusedCardRect.rect
        // Only real moves: the card's own animation reports every frame.
        if (old == null || abs(old.left - r.left) > 1f || abs(old.top - r.top) > 1f || abs(old.width - r.width) > 1f) {
            FocusedCardRect.rect = r
        }
    }
}
