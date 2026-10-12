package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/*
 * Update 156: Endless on the layouts that handle their own keys (Library panel, Timeline, Channel grid). Going round
 * from the last game to the first is one more ordinary step there: only the games move, nothing reloads. Their key
 * handlers run outside Compose, so they leave word here (before the selection moves) of which way a step went round;
 * the layout counts on along an endless "virtual" place, as the rows do (EndlessRow.kt).
 */
internal object StyleLoop {
    /** Endless on (and at least two games) for the layout on screen: set as it draws, read by its key handler. */
    @Volatile var on: Boolean = false
    /** Bumped by a key handler that went round (Channel grid: a page / a column), [step] which way (+1 / -1). */
    @Volatile var tick: Int = 0
    @Volatile var step: Int = 0
    /**
     * Every step the Timeline's keys made, added up in places along its line (so no step is lost between frames); the
     * Library panel's keys add theirs in games (Update 156, review).
     */
    @Volatile var walked: Int = 0

    fun wrapped(d: Int) {
        step = d
        tick++
    }
}

/**
 * The virtual place of [place] (0 until [n]) going round: it moves by exactly what the key handler added to [walked]
 * since it last looked, so stepping on past the end keeps counting up (or down). A tap in [tap] lands on the copy that
 * was tapped; any other jump goes the shorter way round. Without [loop] it is just [place]. A new [list] (re-sorted)
 * keeps the copy you're in; a different [n] starts again at [place].
 */
@Composable
internal fun rememberWalkedPlace(place: Int, n: Int, loop: Boolean, walked: Int, list: Any?, tap: LoopTap): Int {
    // [last place, virtual place, last walked, size, looping]
    val t = remember { intArrayOf(place, place, walked, n, 0) }
    val seen = remember { arrayOfNulls<Any>(1) }
    if (!loop || n <= 0) {
        t[0] = place; t[1] = place; t[2] = walked; t[3] = n; t[4] = 0
        seen[0] = list
        tap.virtual = Int.MIN_VALUE
        return place
    }
    if (t[4] == 0 || t[3] != n) {
        t[0] = place; t[1] = place; t[2] = walked; t[3] = n; t[4] = 1
    } else if (seen[0] != null && seen[0] !== list) {
        t[1] = t[1] - t[1].mod(n) + place
        t[0] = place
        t[2] = walked
    }
    seen[0] = list
    // (Update 156, review: taken only once the place itself has moved — the key handler adds its step a moment before
    // the selection reaches here, and a redraw in between must not count that step twice.)
    if (t[2] != walked && t[0] != place) {
        val d = walked - t[2]
        t[2] = walked
        t[1] += d
        t[0] = (t[0] + d).mod(n)
    }
    if (t[0] != place) {
        val tapped = tap.virtual
        tap.virtual = Int.MIN_VALUE
        if (tapped != Int.MIN_VALUE && tapped.mod(n) == place) {
            t[1] = tapped
        } else {
            var d = place - t[0]
            if (d > n / 2) d -= n
            if (d < -n / 2) d += n
            t[1] += d
        }
        t[0] = place
    }
    return t[1]
}
