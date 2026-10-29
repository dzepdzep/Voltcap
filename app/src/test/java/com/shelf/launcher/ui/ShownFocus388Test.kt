package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Update 388 (D77, recorded on the Thor after 383): the selection changes at once, the row glides, so the highlight stood in the
 * third slot and slid back. Frame by frame: the card that wears the highlight must stay within half a slot of the selection's
 * slot (`before` games on its left) the whole glide, however fast the presses come, in both directions and across the wrap.
 */
class ShownFocus388Test {
    private val pitch = 200

    /** One frame: the row is at fractional position [pos] (items from the start), the selection is [focus]. Returns the highlight's slot (items from the left edge, fractional). */
    private fun slot(focus: Int, pos: Float, before: Int, count: Int): Float {
        val first = kotlin.math.floor(pos).toInt()
        val off = ((pos - first) * pitch).roundToInt()
        return shownFocusItem(focus, before, first, off, pitch, count) - pos
    }

    /** Simulates the row gliding to each press' target (a new press restarts the glide from where the row is, like animateScrollToItem). */
    private fun walk(steps: String, framesPerPress: Int, loop: Boolean, n: Int = 30, before: Int = 1) {
        val row0 = LoopRow(loop, n, 3)
        var focus = row0.focusItem
        var pos = followTarget(focus, before).toFloat()
        for ((k, m) in steps.withIndex()) {
            focus += if (m == 'R') 1 else -1
            val target = followTarget(focus, before).toFloat()
            val from = pos
            for (f in 1..framesPerPress) {
                val t = f / framesPerPress.toFloat()
                pos = from + (target - from) * (1f - (1f - t) * (1f - t)) // ease-out
                val s = slot(focus, pos, before, row0.count)
                val want = (focus - target) // settled slot = focus - followTarget
                assertTrue("press $k ($m) frame $f: highlight slot $s, want $want", abs(s - want) <= 0.5001f)
            }
        }
    }

    @Test fun walkingRightOneSlotNeverLeavesTheSecondSlot() = walk("RRRRRRRR", 12, loop = true)
    @Test fun walkingLeftOneSlotNeverLeavesTheSecondSlot() = walk("LLLLLLLL", 12, loop = true)
    @Test fun reversalsMidGlide() = walk("RLRLLRRLRL", 5, loop = true)
    @Test fun rapidPressesBeforeTheGlideEnds() = walk("RRRRRRRRRRRR", 3, loop = true)
    @Test fun endlessOffAtTheStartKeepsTheFirstGamesAtTheEdge() = walk("RLRLLRR", 8, loop = false)

    @Test fun settledIsTheSelection() {
        assertEquals(40, shownFocusItem(40, 1, 39, 0, pitch, 100))
        assertEquals(0, shownFocusItem(0, 1, 0, 0, pitch, 100))
    }

    /** The old behaviour the recording showed: right after ▶ the row has not moved yet; the highlight must still be on the old card. */
    @Test fun firstFrameAfterARightPressKeepsTheOldCardInTheSlot() {
        val focus = 501
        val shown = shownFocusItem(focus, 1, 499, 0, pitch, 1000) // row still at 499 (focus was 500)
        assertEquals(500, shown)
    }

    @Test fun noMeasurementYetIsTheSelection() {
        assertEquals(7, shownFocusItem(7, 1, 0, 0, 0, 10))
    }

    /** A ▶ then a ◀ back to the last settled selection while the row still glides: the highlight must keep following the row (was one slot too far left on the Thor). */
    @Test fun backToTheSettledSelectionMidGlideStillFollowsTheRow() {
        assertTrue(highlightFollowsRow(settledFor = 500, focusItem = 500, gliding = 1))
        assertTrue(highlightFollowsRow(settledFor = 500, focusItem = 501, gliding = 0)) // the first frame after a press
        assertEquals(false, highlightFollowsRow(settledFor = 500, focusItem = 500, gliding = 0)) // settled / the user scrolls
    }
}
