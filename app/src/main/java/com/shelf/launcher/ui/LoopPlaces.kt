package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/*
 * Update 156: Endless on the Grid, the TV room's grid and shelf, and the Tile home. Going round from the last game to
 * the first is one more ordinary step there: the games carry on (the first ones again after the last), only the games
 * move, nothing reloads. The place you're on is kept on an endless "virtual" index, as on the rows (EndlessRow.kt);
 * this one also keeps a tapped game where it was tapped, and in a grid takes ▲ ▼ (a whole row) as they are.
 */

/** A tapped game's virtual place, kept until the selection gets there (a tap selects the copy you touched). */
internal class LoopTap {
    var virtual: Int = Int.MIN_VALUE
}

/**
 * The virtual place of [focus] among [n] games going round (just [focus] without [loop]). [tick] / [step] are the
 * view model's D-pad step (each step slides exactly that way); a tap in [tap] lands on the copy that was tapped;
 * [rowStep] > 0 (a grid's columns): a move of exactly that many is ▲ / ▼, taken as it is; any other jump goes the
 * shorter way round. A new list of the same size keeps the place; a different size starts again at [focus].
 */
@Composable
internal fun rememberLoopPlace(focus: Int, n: Int, loop: Boolean, tick: Int, step: Int, list: Any?, tap: LoopTap, rowStep: Int = 0): Int {
    // [last focus, virtual focus, last tick, size, looping]
    val t = remember { intArrayOf(focus, focus, tick, n, 0) }
    val seen = remember { arrayOfNulls<Any>(1) }
    if (!loop || n <= 0) {
        t[0] = focus; t[1] = focus; t[2] = tick; t[3] = n; t[4] = 0
        seen[0] = list
        tap.virtual = Int.MIN_VALUE
        return focus
    }
    if (t[4] == 0 || t[3] != n) {
        t[0] = focus; t[1] = focus; t[2] = tick; t[3] = n; t[4] = 1
    } else if (seen[0] != null && seen[0] !== list) {
        // Re-sorted, or art came in: carry on from the same place.
        t[1] = t[1] - t[1].mod(n) + focus
        t[0] = focus
        t[2] = tick
    }
    seen[0] = list
    if (t[2] != tick) {
        // A D-pad step: slide exactly that way.
        t[2] = tick
        if (step != 0) {
            t[1] += step
            t[0] = (t[0] + step).mod(n)
        }
    }
    if (t[0] != focus) {
        val tapped = tap.virtual
        tap.virtual = Int.MIN_VALUE
        if (tapped != Int.MIN_VALUE && tapped.mod(n) == focus) {
            t[1] = tapped
        } else {
            val raw = focus - t[0]
            var d = raw
            if (!(rowStep > 0 && kotlin.math.abs(raw) == rowStep)) {
                // A jump (a letter, a swipe): the shorter way round.
                if (d > n / 2) d -= n
                if (d < -n / 2) d += n
            }
            t[1] += d
        }
        t[0] = focus
    }
    return t[1]
}
