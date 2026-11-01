package com.shelf.launcher.ui

import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.data.ShortcutSpot

/**
 * v0.6.66 (D131): where the round shortcuts' corner ☰ goes, and how much room its open row has.
 *
 * Before: every layout flipped the corner by the game-row setting (row at the top → every top corner to the bottom; row at the
 * bottom → every bottom corner to the top). The whole-screen layouts (the style homes — Disc station, Arctic… — and Grid / TV room)
 * don't draw that row at all, so on a device whose row setting was "Top" (the GT78) all four corners landed on the bottom edge,
 * over Disc station's disc panel and its "DISC LOADED" label; on one set to "Bottom" (the Odin) they all went into the status bar.
 *
 * Now: a layout with the game row at an edge still keeps the shortcuts off that edge (unchanged). A whole-screen layout keeps the
 * side you picked and always uses the top band — the status bar's own ☰ room when the bar is on, the top corner when it's off:
 * their bottom edge is the layout's own rows, action buttons and legend, so a bottom corner there always covered something.
 */
internal object ShortcutCorner {
    data class Place(val top: Boolean, val start: Boolean)

    /** [fullScreen]: a layout that draws its whole screen itself (style homes, Grid, TV room) — no game row at an edge. */
    fun resolve(spot: ShortcutSpot, dock: DockPosition, fullScreen: Boolean): Place {
        val start = spot == ShortcutSpot.TOP_LEFT || spot == ShortcutSpot.BOTTOM_LEFT
        var top = spot == ShortcutSpot.TOP_LEFT || spot == ShortcutSpot.TOP_RIGHT
        if (fullScreen) {
            top = true
        } else {
            if (top && dock == DockPosition.TOP) top = false
            if (!top && dock == DockPosition.BOTTOM) top = true
        }
        return Place(top, start)
    }

    /**
     * The width (px) the open row may take in the status bar's band, from the ☰ room ([slotLeft]..[slotRight]) to the nearest
     * piece of the bar on the side it folds to ([busy]: the clock / icons cluster, the music player — left..right), less [gap];
     * to the screen edge less [margin] when nothing is there. A piece that holds the room itself (the room before the clock is
     * inside the cluster) doesn't count. Never less than the room itself.
     */
    fun bandRoom(
        slotLeft: Float, slotRight: Float, foldRight: Boolean,
        busy: List<ClosedFloatingPointRange<Float>>, screenW: Float, margin: Float, gap: Float
    ): Float {
        val slotW = (slotRight - slotLeft).coerceAtLeast(0f)
        val others = busy.filter { !(it.start <= slotLeft + 0.5f && it.endInclusive >= slotRight - 0.5f) }
        val room = if (foldRight) {
            val wall = others.filter { it.start >= slotRight - 0.5f }.minOfOrNull { it.start }
            (wall?.minus(gap) ?: (screenW - margin)) - slotLeft
        } else {
            val wall = others.filter { it.endInclusive <= slotLeft + 0.5f }.maxOfOrNull { it.endInclusive }
            slotRight - (wall?.plus(gap) ?: margin)
        }
        return room.coerceAtLeast(slotW)
    }

    /**
     * How far (px) a one-line row scrolled by [current] must scroll so button [index] (in the order drawn, [count] in all; laid
     * out from the ☰'s side — [fromEnd] for a ☰ at the right) is fully in a [viewport] wide window; null when it already is.
     */
    fun scrollFor(index: Int, count: Int, fromEnd: Boolean, stride: Float, size: Float, viewport: Float, current: Float): Float? {
        if (viewport <= 0f || index !in 0 until count) return null
        val a = (if (fromEnd) count - 1 - index else index) * stride
        val b = a + size
        return when {
            a < current -> a
            b > current + viewport -> b - viewport
            else -> null
        }
    }
}
