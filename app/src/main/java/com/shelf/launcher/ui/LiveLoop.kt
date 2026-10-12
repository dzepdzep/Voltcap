package com.shelf.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key

/*
 * Update 156: Endless (R1) on Live tiles. ▶ past the last page (or onto its empty tiles) goes round to the first page,
 * ◀ past the first page to the last one, and the next page slides in exactly like any next page: the pages are
 * counted on ([rememberLivePage]) instead of the page number jumping back, so last -> first slides forward, not back.
 * Live tiles moves round its tiles itself (styleHomeKey › liveMove, plain setFocus), so a wrap there tells this which
 * way it went ([wrapped]); a step from the view model (moveFocus) brings its own loopTick / loopStep.
 */
internal object LiveLoop {
    /** Endless is on and there are games to go round (set by StyleLiveTiles while it's drawn). */
    @Volatile var on = false
    @Volatile var seq = 0
        private set
    @Volatile var dir = 0
        private set
    @Volatile var from = -1
        private set
    @Volatile var to = -1
        private set

    /** The D-pad went round from game [from] to game [to], forward ([dir] 1) or back (-1). */
    fun wrapped(from: Int, to: Int, dir: Int) {
        this.from = from
        this.to = to
        this.dir = dir
        seq++
    }
}

/**
 * Where ◀ / ▶ ([key]) from game [focus] go round to on pages of [size] games, [count] games in all, when the plain
 * move ([move], liveMove) has nowhere to go: -1 when it doesn't go round (Endless off, fewer than 2 games, not at an end).
 */
internal fun liveWrap(focus: Int, key: Key, size: Int, count: Int, move: Int): Int {
    if (!LiveLoop.on || size <= 0 || count < 2) return -1
    val pages = (count + size - 1) / size
    // (Update 156, review: one page goes round too — ▶ past its last game to its first, ◀ from the left column to the
    // right; the page itself stays, nothing slides.)
    if (pages < 1) return -1
    val lastBase = (pages - 1) * size
    return when (key) {
        Key.DirectionRight -> if (focus >= lastBase && move >= count) {
            // Past the last page: the tile it would reach on the next page, on the first one; onto an empty tile of the
            // last page: the first page's first tile (on the six even tiles, the start of the same row).
            val slot = move - lastBase - size
            val at = if (slot in 0 until size) slot else if (size == 6 && move - lastBase in 3..5) 3 else 0
            at.coerceAtMost(count - 1)
        } else -1
        Key.DirectionLeft -> if (focus < size) {
            // The same move as from a page further on, then that page before is the last page.
            val shifted = liveMove(focus + pages * size, key, size)
            val m = if (shifted == null) 0 else shifted - pages * size
            if (m < 0) (lastBase + m + size).coerceIn(lastBase, count - 1) else -1
        } else -1
        else -> -1
    }
}

/**
 * The page of Live tiles you're on, counted on round the ends with Endless on: going round from the last page to the
 * first one counts up by one (and back from the first to the last counts down), so the page slides in like any next
 * page. Without Endless it moves with the page itself. Only a different number of pages starts again.
 */
@Composable
internal fun rememberLivePage(focus: Int, size: Int, n: Int, loop: Boolean, tick: Int, step: Int): Int {
    val s = size.coerceAtLeast(1)
    val page = focus.coerceAtLeast(0) / s
    val pages = (n + s - 1) / s
    // [last page, counted page, last loop tick, last wrap used, pages, last focus]
    val t = remember { intArrayOf(page, page, tick, LiveLoop.seq, pages, focus) }
    if (t[4] != pages) {
        t[0] = page; t[1] = page; t[4] = pages
    } else if (page != t[0]) {
        var d = page - t[0]
        if (loop && pages >= 2) {
            val w = when {
                LiveLoop.seq != t[3] && LiveLoop.from == t[5] && LiveLoop.to == focus -> { t[3] = LiveLoop.seq; LiveLoop.dir }
                tick != t[2] -> if (step > 0) 1 else if (step < 0) -1 else 0
                else -> 0
            }
            if (w > 0 && d < 0) d += pages else if (w < 0 && d > 0) d -= pages
        }
        t[1] += d
        t[0] = page
    }
    t[2] = tick
    t[5] = focus
    return t[1]
}
