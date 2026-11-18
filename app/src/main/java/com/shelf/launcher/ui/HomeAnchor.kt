package com.shelf.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 682e (owner 18 Nov, a system rule): where the home screen shows its primary action row (Play) and the category chip row at
 * the same time, the two share ONE visual anchor - the selected chip's left edge sits on the Play button's left edge, and
 * when the chips all fit the row starts there. Play reports its edge (in the home window), the chip row reads it.
 * Provided by the home screen only; a second screen has its own (or none). Checked on dumps by tools/qa/tools/anchorcheck.py.
 */
internal class HomeAnchorState {
    /** The home Play button's left edge in the home window (px); null while no Play button is shown on the home. */
    var playLeftPx by mutableStateOf<Float?>(null)
}

internal val LocalHomeAnchor = staticCompositionLocalOf<HomeAnchorState?> { null }

/** True inside the home screen's own game header (HomeShowcaseHeader) - its Play is the anchor; the popup's is not. */
internal val LocalHomeHeader = staticCompositionLocalOf { false }

/** The rule's arithmetic (PlayChipAnchor682eTest). All px. */
internal object PlayChipAnchor {
    /**
     * Where the chips start inside their row: on Play's edge ([playLeft] - [rowLeft]) when a Play button is shown and the
     * row reaches that far left; else the row's own start [fallback] (no Play shown, or Play left of where the row begins -
     * then both still start on the content column the row has).
     */
    fun start(playLeft: Float?, rowLeft: Float, fallback: Float): Float {
        if (playLeft == null) return fallback
        val d = playLeft - rowLeft
        return if (d >= 0f) d else fallback
    }

    /** True when the chips need scrolling (they do not all fit between [start] and the row's end). */
    fun overflows(start: Float, chipsWidth: Float, end: Float, viewW: Float): Boolean = start + chipsWidth + end > viewW + 0.5f

    /**
     * The scroll that puts the selected chip's left edge on the anchor: [cur] + (its left on screen [onScreenLeft] - [start]),
     * kept within 0..[max].
     */
    fun target(cur: Int, onScreenLeft: Float, start: Float, max: Int): Int =
        (cur + Math.round(onScreenLeft - start)).coerceIn(0, max.coerceAtLeast(0))

    /** Room after the last chip so that it too can come to the anchor (only when the chips overflow). */
    fun endRoom(viewW: Float, start: Float, lastChipW: Float, end: Float): Float = maxOf(end, viewW - start - lastChipW)
}
