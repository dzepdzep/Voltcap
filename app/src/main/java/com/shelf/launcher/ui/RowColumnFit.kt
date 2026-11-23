package com.shelf.launcher.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import com.shelf.launcher.data.DockPosition

/**
 * 716a (owner Task 1, Row-position collisions): the Voltcap row's column (game header, game info / news, category bar, the
 * games) sits Top / Middle / Bottom by Row place. Middle centred it on the whole screen and Bottom stood it on the bottom
 * edge, so a column taller than the room the status bar leaves (game header + info + chips at x1.3 text, a tall music
 * player in the bar) started under the clock / battery / player. It now never starts above [topSafe]: when it fits it is
 * placed exactly as before (nothing moves on an ordinary screen), Middle is then kept clear of the legend's band
 * ([bottomSafe]) as well where both fit, and only a column taller than the whole free room spills at the bottom instead
 * of under the status bar (the top stays readable).
 */
object RowColumnFit {
    /**
     * The column's top (px) in a box [boxH] high for a column [colH] high at [place]. [topSafe] is the status bar's bottom
     * edge when the bar is along the top (0 when it is off or at the bottom), [bottomSafe] the legend's band.
     */
    fun top(place: DockPosition, boxH: Int, colH: Int, topSafe: Int, bottomSafe: Int): Int {
        val natural = when (place) {
            DockPosition.TOP -> 0
            DockPosition.MIDDLE -> (boxH - colH) / 2
            DockPosition.BOTTOM -> boxH - colH
        }
        if (place == DockPosition.TOP || topSafe <= 0 && bottomSafe <= 0) return natural
        // Middle: between the bar and the legend when the column fits there (only when the centred spot crossed one).
        if (place == DockPosition.MIDDLE && natural + colH > boxH - bottomSafe && colH <= boxH - topSafe - bottomSafe)
            return maxOf(topSafe, boxH - bottomSafe - colH)
        return maxOf(natural, topSafe)
    }

    /**
     * Moves the (already aligned) column down to [top]'s spot. Applied right after `align`, so the size and the alignment
     * the parent box gives stay as they were; the shift is 0 whenever the column fits.
     */
    fun Modifier.keepUnderBar(place: DockPosition, topSafe: Int, bottomSafe: Int): Modifier = layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        val boxH = if (constraints.hasBoundedHeight) constraints.maxHeight else p.height
        layout(p.width, p.height) {
            val natural = when (place) {
                DockPosition.TOP -> 0
                DockPosition.MIDDLE -> (boxH - p.height) / 2
                DockPosition.BOTTOM -> boxH - p.height
            }
            p.place(0, top(place, boxH, p.height, topSafe, bottomSafe) - natural)
        }
    }
}
