package com.shelf.launcher.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.isActive
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
// (Update 329, found by the R1 tile walk: Twin showcase's grid stops at its ends too — StyleTwinShowcase keeps Endless off —
// so the R1 tile switched nothing there.)
internal fun endlessApplies(layout: com.shelf.launcher.data.HomeLayout): Boolean =
    layout != com.shelf.launcher.data.HomeLayout.HERO_TILES && layout != com.shelf.launcher.data.HomeLayout.TWIN_SHOWCASE

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
    private val whole = androidx.compose.runtime.mutableIntStateOf(LAZY_LOOP_MIN - 2)
    /** Update 390: how many whole cards fit between the row's paddings (what [rowBefore] places the selection by). */
    val visible: Int get() = whole.intValue
    fun report(width: androidx.compose.ui.unit.Dp, item: androidx.compose.ui.unit.Dp, gap: androidx.compose.ui.unit.Dp,
               pad: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(0f)) {
        if (item <= androidx.compose.ui.unit.Dp(0f)) return
        val w = ((width - pad * 2 + gap) / (item + gap)).toInt().coerceAtLeast(1)
        if (w != whole.intValue) whole.intValue = w
        val k = ((width + gap) / (item + gap)).toInt() + 2
        if (k != n.intValue) n.intValue = k
    }
}

/** Update 174: the fewest games a lazy row / list needs to go round (fewer fit on screen, so copies would show). */
internal const val LAZY_LOOP_MIN = 8

/**
 * Update 390: how many cards stand before the one you're on in a followed row ([FollowFocus]'s `before`) for the chosen
 * [com.shelf.launcher.data.Features.RowFocus], with [visible] whole cards fitting the row (the selection always stays on screen).
 */
internal fun rowBefore(pos: com.shelf.launcher.data.Features.RowFocus, visible: Int): Int = when (pos) {
    com.shelf.launcher.data.Features.RowFocus.DESIGN -> 1
    com.shelf.launcher.data.Features.RowFocus.LEFT -> 0
    com.shelf.launcher.data.Features.RowFocus.CENTER -> ((visible - 1) / 2).coerceAtLeast(0)
    com.shelf.launcher.data.Features.RowFocus.RIGHT -> (visible - 1).coerceAtLeast(0)
}

/**
 * Update 383 (D77): the item a followed row scrolls to so that the item you're on always stands [before] items from the left
 * edge. (Until 382 a card already in view was left where it was, so ▶ from the second slot left two games to its left and the
 * row only moved later, when the focus reached an edge: "two games left of the selected one", and a row that moved a press
 * after the selection.) One press = one step of the row, the selected card keeps its place.
 */
internal fun followTarget(focusItem: Int, before: Int): Int = (focusItem - before).coerceAtLeast(0)

/**
 * Update 389 (D77, third time): the highlight is a function of where the row IS, never of a second clock. 388 handed the frame
 * from one card to the next half-way through the glide (the frame rode the old card 90 px to the left, then jumped a whole slot
 * to the right: measured per frame on the Thor), 383 jumped it to the new card at once. Now the card that is gliding into the
 * selection's slot fades its highlight in while the card leaving it fades its out, both as the row moves (a continuous crossfade,
 * nothing jumps); settled it is exactly the selected card. [slotPos] is the (fractional) item standing in the selection's slot:
 * the selection minus how far the row still has to move ([followTarget] - first - offset / pitch); NaN = not following the row (settled, the user's own scroll, a row that can't move further).
 */
internal fun slotPosition(focusItem: Int, before: Int, first: Int, offsetPx: Int, pitchPx: Int): Float =
    if (pitchPx <= 0) Float.NaN else focusItem - (followTarget(focusItem, before) - (first + offsetPx.toFloat() / pitchPx))

/** 0..1: how much [item] wears the highlight — the selected item alone when [slotPos] is NaN, else its closeness to the slot. */
internal fun highlightStrength(item: Int, focusItem: Int, slotPos: Float): Float =
    if (slotPos.isNaN()) (if (item == focusItem) 1f else 0f) else (1f - kotlin.math.abs(item - slotPos)).coerceIn(0f, 1f)

/**
 * Whether the highlight follows the row's slot instead of sitting on the selection: from the first frame after a press ([settledFor]
 * is the selection the row last finished gliding to) and for as long as one of this row's glides runs ([gliding], which also covers
 * a ◀ right after a ▶), never while the user scrolls — and not when the row can't move any further towards the selection
 * ([stuck]: a row without Endless at its last game; there the selected card is simply where it is).
 */
internal fun highlightFollowsRow(settledFor: Int, focusItem: Int, gliding: Int, stuck: Boolean = false): Boolean =
    !stuck && (settledFor != focusItem || gliding > 0)

/** What [FollowFocus] hands back: the selected item and how strongly each item wears the highlight right now. */
@androidx.compose.runtime.Stable
internal class FocusFollow(val selected: Int, private val slot: androidx.compose.runtime.State<Float>) {
    /** Read while drawing or inside a derivedStateOf (it follows the scroll position frame by frame). */
    fun strength(item: Int): Float = highlightStrength(item, selected, slot.value)
}

/**
 * Keeps the item you're on at the same place of a lazy row: one step glides, a long way (a new list, Endless switched on) jumps.
 * [before] is how many items stand before it ([followTarget]). Returns the [FocusFollow] that says which items wear the highlight.
 */
@Composable
internal fun FollowFocus(list: LazyListState, row: LoopRow, before: Int = 1): FocusFollow {
    val settledFor = remember { androidx.compose.runtime.mutableIntStateOf(row.focusItem) }
    val gliding = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(row.focusItem, row.count) {
        val target = followTarget(row.focusItem, before)
        gliding.intValue++
        try {
            if (kotlin.math.abs(list.firstVisibleItemIndex - target) > 12) runCatching { list.scrollToItem(target) }
            else runCatching { list.animateScrollToItem(target) }
            if (coroutineContext.isActive) settledFor.intValue = row.focusItem // (a newer press cancels this effect: it stays unsettled)
        } finally { gliding.intValue-- }
    }
    val slot = remember(row.focusItem, row.count, before) {
        androidx.compose.runtime.derivedStateOf {
            val target = followTarget(row.focusItem, before)
            val stuck = list.firstVisibleItemIndex < target && !list.canScrollForward
            if (!highlightFollowsRow(settledFor.intValue, row.focusItem, gliding.intValue, stuck)) Float.NaN else {
                val info = list.layoutInfo
                val pitch = (info.visibleItemsInfo.firstOrNull()?.size ?: 0) + info.mainAxisItemSpacing
                slotPosition(row.focusItem, before, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, pitch)
            }
        }
    }
    return remember(row.focusItem, slot) { FocusFollow(row.focusItem, slot) }
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
