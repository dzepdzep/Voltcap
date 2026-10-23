package com.shelf.launcher.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.shelf.launcher.data.HomeLayout

/*
 * Update 125: the endless row ("Endless" in R1) on the other layouts too. The games repeat on both sides, and the
 * place you're on is tracked on an endless "virtual" index: stepping from the last game to the first slides one game
 * on, like the Voltcap row (GameDock), instead of the row racing back over everything to the start.
 */

/** Layouts whose row goes round on its own (no "comes back in from the other side" over the whole layout). */
// Update 156: every layout now goes round on its own (the Grid's and TV room's every look, Tiles, Game shelf, Case stacks,
// Console stack, Library, Timeline, Channel grid, Live tiles, Arcade, Disc station joined): going past the end is one
// more step, never the whole layout coming back in from the other side (it looked like the layout reloading).
@Suppress("UNUSED_PARAMETER")
internal fun loopsOnItsOwn(layout: HomeLayout): Boolean = true

/** Copies of the games a looping lazy row holds; the row starts in the middle one. */
internal const val LOOP_COPIES = 1000

/** True when [c]'s row should go round (Endless on, at least two games). */
internal fun loopRowOn(c: StyleCtx): Boolean = c.state.appearance.loopRow && c.games.size >= 2 && endlessApplies(c.state.appearance.homeLayout)

/** Update 182: Hero tiles has no Endless (its rows going round were confusing): it stops at the ends there. */
internal fun endlessApplies(layout: com.shelf.launcher.data.HomeLayout): Boolean = layout != com.shelf.launcher.data.HomeLayout.HERO_TILES

/**
 * The virtual place of [focus] on an endless row of [n] games: it moves by the D-pad's step ([tick] / [step] from the
 * view model), so with the row going round it keeps counting up (or down) instead of jumping back. Without [loop] it is
 * just [focus]. A new list of the same size keeps the place; a different size starts again at [focus].
 */
@Composable
internal fun rememberLoopFocus(focus: Int, n: Int, loop: Boolean, tick: Int, step: Int, list: Any?): Int {
    // [last focus, virtual focus, last tick, size, looping]
    val t = remember { intArrayOf(focus, focus, tick, n, 0) }
    val seen = remember { arrayOfNulls<Any>(1) }
    if (!loop || n <= 0) {
        t[0] = focus; t[1] = focus; t[2] = tick; t[3] = n; t[4] = 0
        seen[0] = list
        return focus
    }
    if (t[4] == 0 || t[3] != n) {
        t[0] = focus; t[1] = focus; t[2] = tick; t[3] = n; t[4] = 1
    } else if (seen[0] != null && seen[0] !== list) {
        // Re-sorted, or art came in: carry on from the same place on the endless row.
        t[1] = t[1] - t[1].mod(n) + focus
        t[0] = focus
        t[2] = tick
    }
    seen[0] = list
    if (t[2] != tick) {
        // A D-pad step: slide exactly that way (with two games the index alone can't tell which way it went).
        t[2] = tick
        if (step != 0) {
            t[1] += step
            t[0] = (t[0] + step).mod(n)
        }
    }
    if (t[0] != focus) {
        // A jump (a tap, a letter): the shorter way round.
        var d = focus - t[0]
        if (d > n / 2) d -= n
        if (d < -n / 2) d += n
        t[1] += d
        t[0] = focus
    }
    return t[1]
}

/** [rememberLoopFocus] for a style home's row. */
@Composable
internal fun rememberLoopFocus(c: StyleCtx): Int =
    rememberLoopFocus(c.focus, c.games.size, loopRowOn(c), c.state.loopTick, c.state.loopStep, c.games)

/**
 * A lazy row of [c]'s games that goes round: how many items it holds, which game item [i] shows, which item is the
 * one you're on ([focusItem]), and a key for each item.
 */
internal class LoopRow(val loop: Boolean, val n: Int, virtual: Int) {
    private val base = if (loop) (LOOP_COPIES / 2) * n else 0
    val count: Int get() = if (loop) n * LOOP_COPIES else n
    val focusItem: Int = (base + virtual).let { if (loop) it.coerceIn(0, n * LOOP_COPIES - 1) else it }
    fun game(i: Int): Int = if (loop) i.mod(n) else i
    fun key(i: Int, keys: List<Any>): Any = if (loop) "${keys[i.mod(n)]}#${i / n}" else keys[i]
}

@Composable
internal fun rememberLoopRow(c: StyleCtx, fits: Int = LAZY_LOOP_MIN): LoopRow {
    val v = rememberLoopFocus(c)
    // (Update 174: a lazy row goes round only with more games than fit, else its copies show side by side.)
    // (Update 226, #105: [fits] = how many the row really shows, measured by its owner — eight was too few for slim art
    // on a wide screen.)
    val loop = loopRowOn(c) && c.games.size > maxOf(LAZY_LOOP_MIN, fits)
    return remember(loop, c.games.size, v, c.focus) { LoopRow(loop, c.games.size, if (loop) v else c.focus) }
}

/**
 * Update 226 (#105): a lazy row's owner measures how many games it shows ([report] from inside its BoxWithConstraints:
 * the width over one game and its gap, plus the two part-shown ones) and passes [value] to [rememberLoopRow] — read on the
 * next frame, changed only when the room or the game size does.
 */
internal class RowFits {
    private val n = androidx.compose.runtime.mutableIntStateOf(LAZY_LOOP_MIN)
    val value: Int get() = n.intValue
    fun report(width: androidx.compose.ui.unit.Dp, item: androidx.compose.ui.unit.Dp, gap: androidx.compose.ui.unit.Dp) {
        if (item <= androidx.compose.ui.unit.Dp(0f)) return
        val k = ((width + gap) / (item + gap)).toInt() + 2
        if (k != n.intValue) n.intValue = k
    }
}

/** Update 174: the fewest games a lazy row / list needs to go round (fewer fit on screen, so copies would show). */
internal const val LAZY_LOOP_MIN = 8

/**
 * Keeps the item you're on in view of a lazy row: one step glides, a long way (a new list, Endless switched on) jumps.
 * [before] is how many items stay visible before it.
 */
@Composable
internal fun FollowFocus(list: LazyListState, row: LoopRow, before: Int = 1) {
    LaunchedEffect(row.focusItem, row.count) {
        val target = (row.focusItem - before).coerceAtLeast(0)
        val visible = list.layoutInfo.visibleItemsInfo
        val inView = visible.any { it.index == row.focusItem } && visible.firstOrNull()?.index != row.focusItem &&
            visible.lastOrNull()?.index != row.focusItem
        if (inView && visible.size > 2) return@LaunchedEffect
        if (kotlin.math.abs(list.firstVisibleItemIndex - target) > 12) runCatching { list.scrollToItem(target) }
        else runCatching { list.animateScrollToItem(target) }
    }
}

/**
 * Update 174: going round a row of only [n] games, whether the copy [off] places from the one you're on is the one drawn —
 * each game once ([before] of them may stand before it), so a short category doesn't show the same games again and again
 * (two Android games stood three times, four games twice each side). With more games than fit nothing changes.
 */
internal fun drawnOnce(off: Int, n: Int, before: Int = (n - 1) / 2): Boolean {
    if (n <= 0) return true
    // (Update 324: Endless › Fill the row — the games again past both edges, every layout.)
    if (com.shelf.launcher.data.Features.endlessFill.value) return true
    val b = before.coerceIn(0, n - 1)
    return off >= -b && off <= n - 1 - b
}
