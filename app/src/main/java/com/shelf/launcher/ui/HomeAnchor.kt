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

    /**
     * 682m (owner 18 Nov 14:54, review of the before / after sheet: "a consistent starting anchor aligned with Play, but scroll
     * naturally and preserve neighbouring categories whenever possible ... natural appearance over a mathematically perfect
     * 0 dp"): the strip STARTS on Play's edge, and the chip you're on is no longer pinned there. The row only moves when the
     * chip, the one before it or the one after it is out of view (or under the fading end): going back, the one before comes
     * to the Play edge (so the strip again starts there, with the chip you're on beside it); going on, the row scrolls just
     * far enough that the one after shows whole. The list's end stops at its own end - the last chip sits on the right with
     * its neighbours, never alone on the left. [lefts] / [widths]: the chips in the row's content (px, the row's start
     * padding included); [cur] the scroll; [viewW] the row's width; [start] the Play edge in the row; [fade] the fading end's
     * width (no fade once the row is at its end); [max] the scroll's end. Pure.
     */
    fun natural(k: Int, lefts: IntArray, widths: IntArray, cur: Int, viewW: Int, start: Int, fade: Int, max: Int): Int {
        val n = minOf(lefts.size, widths.size)
        if (k !in 0 until n || max <= 0) return 0
        fun l(i: Int) = lefts[i] - cur
        fun r(i: Int) = lefts[i] + widths[i] - cur
        val endIn = viewW - (if (cur >= max) 0 else fade)
        val prev = if (k > 0) k - 1 else k
        val next = if (k < n - 1) k + 1 else k
        val prevIn = k == 0 || l(prev) >= start - 1
        val selIn = l(k) >= start - 1 && r(k) <= endIn + 1
        val nextIn = k == n - 1 || r(next) <= endIn + 1
        if (prevIn && selIn && nextIn) return cur.coerceIn(0, max)
        val back = if (k == 0) 0 else lefts[prev] - start           // the one before on the Play edge
        val t = if (!prevIn || l(k) < start - 1) back
        else {
            // on: the one after whole (by the fading end unless that is the list's end), the chip you're on never past the edge
            val toNext = lefts[next] + widths[next] - viewW + (if (next == n - 1) 0 else fade)
            minOf(toNext, lefts[k] - start)
        }
        return t.coerceIn(0, max)
    }
}
