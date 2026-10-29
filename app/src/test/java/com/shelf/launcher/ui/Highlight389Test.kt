package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Update 389 (D77, third time). 388 handed the highlight from the old card to the new one half-way through the glide: on the
 * Thor (per-frame geometry trace) the frame rode the old card ~90 px left and then jumped a whole slot (~197 px) right. The
 * highlight is now a crossfade by the row's position: the strengths of the items always add up to 1 and their centre of gravity
 * stands still in the selection's slot, however fast the presses come, both directions, across the Endless wrap; settled it is
 * the selected card alone.
 */
class Highlight389Test {
    private val pitch = 200

    private class Frame(val strengths: Map<Int, Float>, val centreX: Float, val selected: Int)

    /** The strengths of the items standing near the row's position [pos] (items from the start) for selection [focus]. */
    private fun frame(focus: Int, pos: Float, before: Int, follow: Boolean = true): Frame {
        val first = floor(pos).toInt()
        val off = ((pos - first) * pitch).roundToInt()
        val slot = if (follow) slotPosition(focus, before, first, off, pitch) else Float.NaN
        val st = (first - 2..first + 12).associateWith { highlightStrength(it, focus, slot) }.filterValues { it > 0f }
        val cx = st.entries.sumOf { (it.value * ((it.key - (first + off.toFloat() / pitch)) * pitch)).toDouble() }.toFloat()
        return Frame(st, cx, focus)
    }

    /** Presses with the row gliding to each target (a new press restarts from where the row is); every frame is checked. */
    private fun walk(steps: String, framesPerPress: Int, loop: Boolean, n: Int = 30, before: Int = 1) {
        val row0 = LoopRow(loop, n, 3)
        var focus = row0.focusItem
        var pos = followTarget(focus, before).toFloat()
        for ((k, m) in steps.withIndex()) {
            focus += if (m == 'R') 1 else -1
            val target = followTarget(focus, before).toFloat()
            val from = pos
            for (f in 0..framesPerPress) {
                val t = f / framesPerPress.toFloat()
                pos = from + (target - from) * (1f - (1f - t) * (1f - t)) // ease-out
                val fr = frame(focus, pos, before)
                val sum = fr.strengths.values.sum()
                assertEquals("press $k frame $f: the strengths add up to 1", 1f, sum, 0.02f)
                assertTrue("press $k frame $f: at most two neighbours wear it ${fr.strengths}", fr.strengths.size <= 2 &&
                    (fr.strengths.size < 2 || abs(fr.strengths.keys.max() - fr.strengths.keys.min()) == 1))
                assertEquals("press $k frame $f: the highlight stands in slot $before, it never swings", before * pitch.toFloat(), fr.centreX, 2f)
            }
            val settled = frame(focus, pos, before, follow = false)
            assertEquals("press $k: settled, the selected card alone", mapOf(focus to 1f), settled.strengths)
        }
    }

    @Test fun `single presses right, one game left of the selection`() = walk("RRRRRR", 12, loop = true)
    @Test fun `single presses left`() = walk("LLLLLL", 12, loop = true)
    @Test fun `rapid presses restart the glide each frame or two`() = walk("RRRRRRRRRRRR", 2, loop = true)
    @Test fun `reversals while gliding`() = walk("RLRRLLRLRRRLLL", 3, loop = true)
    @Test fun `rapid reversals`() = walk("RLRLRLRLRL", 1, loop = true)
    @Test fun `two games left of the selection (before = 2)`() = walk("RRRLLLRR", 6, loop = true, before = 2)
    @Test fun `without Endless the row does the same away from the edge`() = walk("RRRLL", 8, loop = false)

    @Test fun `a row that has not moved yet keeps the highlight on the old card`() {
        // Right after ▶ (frame 0 of the glide): the row is still where it was, so the old card is still lit, the new one is not.
        val fr = frame(focus = 11, pos = 9f, before = 1) // old selection 10 stood at slot 1 -> row at 9; new selection 11 -> target 10
        assertEquals(mapOf(10 to 1f), fr.strengths)
    }

    @Test fun `the first game of a row without Endless stays lit (the row cannot move back)`() {
        val fr = frame(focus = 0, pos = 0f, before = 1)
        assertEquals(mapOf(0 to 1f), fr.strengths)
        assertEquals(0f, slotPosition(0, 1, 0, 0, pitch) - 0f, 0.0001f)
    }

    @Test fun `a row that cannot move further towards the selection does not follow`() {
        assertTrue(highlightFollowsRow(settledFor = 5, focusItem = 6, gliding = 1, stuck = false))
        assertTrue(!highlightFollowsRow(settledFor = 5, focusItem = 6, gliding = 1, stuck = true))
        assertTrue(!highlightFollowsRow(settledFor = 6, focusItem = 6, gliding = 0))
    }

    @Test fun `no pitch yet falls back to the selected card`() {
        assertTrue(slotPosition(4, 1, 3, 0, 0).isNaN())
        assertEquals(1f, highlightStrength(4, 4, Float.NaN), 0f)
        assertEquals(0f, highlightStrength(3, 4, Float.NaN), 0f)
    }
}
