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

    /**
     * Update 401 (D86, two offset copies when a case is picked again while it still flies home): true from the moment a piece
     * starts to fly out until, closing, it is back over its place — the card it came from stays hidden in that time, whatever the
     * flight amount (which restarts at 0 and left the still-falling card drawn beside the new flight).
     */
    val hold = mutableStateOf(false)

    /** The alpha of the card the piece comes out of: hidden while [hold], else it returns as the piece ([amount]) goes home. */
    fun cardAlpha(amount: Float, hold: Boolean): Float = if (hold) 0f else (1f - amount).coerceIn(0f, 1f)

    /** The game being inspected (its stableKey), or null. */
    var key by mutableStateOf<String?>(null)

    /**
     * Update 162: the game you're on (its stableKey) while the carousel draws it as a 3D case although the look is a
     * plain cover (the "3D cases" switch, and the Arc / Spine wall / Turned row views); null otherwise. A plain field
     * the carousel sets for its middle game, so opening that game inspects a box rather than keeping the flat window.
     */
    @Volatile var drawnAsCase: String? = null

    /**
     * Update 200: a stick click for the piece out in the game window (open the case a step / turn it once), set while a
     * piece is shown. The home screen's key handler calls it when the click didn't reach the window itself (no control in
     * the window had the focus yet — the first click went nowhere and it took two).
     */
    @Volatile var stickClick: (() -> Unit)? = null

    /**
     * Update 274 (Toni): the game's menu › Inspect… — this game's window shows its cartridge / disc (else its case) to
     * turn round whatever the look draws (a plain cover too); cleared when that window closes.
     */
    @Volatile var force: String? = null
    /** Update 274: bumped with every [force] request — a window closing late clears only its own. */
    @Volatile var forceSeq = 0

    /** Update 274, testing only (TestHooks `voltcap_test_inspect`): the forced piece on this library shell, held at [testPose]. */
    @Volatile var testShell: String? = null
    /** Update 274, testing only: yaw, pitch (degrees) to hold the piece at — no turn once round, no sway. */
    @Volatile var testPose: FloatArray? = null
}
