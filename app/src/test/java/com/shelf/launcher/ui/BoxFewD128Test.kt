package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.6.66 (D128): a 2-game category stood in the box's back-left corner, the rest of the floor empty. */
class BoxFewD128Test {
    private val platforms = listOf("GBC", "GBA", "NDS", "PS2", "N64", "PSP", "3DS", "GameCube")
    private fun games(n: Int) = List(n) { i -> Game("Game $i", platforms[i % platforms.size], 0, SourceType.ROM, "/x/g$i.rom") }

    @Test fun aFewCasesStandInTheMiddleOfTheBox() {
        for (n in 1..3) for (messy in listOf(false, true)) {
            val cam = boxCam(960f, 640f, 0.5f, 1f, 2)
            val p = packBoxesFull(games(n), cam, 0.6f, messy, bySize = false, dividers = false)
            val left = p.slots.minOf { it.x0 } - (-cam.wMm / 2f)
            val right = cam.wMm / 2f - p.slots.maxOf { it.x1 }
            // Fails on f0f71bc / 145d014: the cases started at the left wall (left ≈ 3 mm, right ≈ 380 mm).
            assertTrue("n=$n messy=$messy left=$left right=$right", kotlin.math.abs(left - right) < 8f)
            val longest = p.slots.maxBy { it.len }
            val front = cam.dMm - longest.y1; val back = longest.y0
            // (Messy pushes each case up to 6 mm off its line, both ways: a looser tolerance there.)
            assertTrue("n=$n messy=$messy back=$back front=$front", kotlin.math.abs(front - back) < if (messy) 14f else 8f)
            assertTrue(p.slots.all { it.box == 0 && it.row == 0 })
        }
    }

    @Test fun fullBoxesAreLaidOutFromTheWallAsBefore() {
        val cam = boxCam(1920f, 1080f, 0.5f, 1f, 2)
        val p = packBoxesFull(games(40), cam, 0.6f, false, bySize = false, dividers = false)
        assertTrue(p.slots.any { it.row == 1 })
        val first = p.slots[p.order[0]]
        assertEquals(-cam.wMm / 2f + BOX_MARGIN, first.x0, 0.01f)
        assertEquals(BOX_MARGIN, first.y0, 0.01f)
    }

    @Test fun casesStillNeverOverlapAndStayInside() {
        val cam = boxCam(960f, 640f, 0.5f, 1f, 2)
        val p = packBoxesFull(games(3), cam, 0.6f, false, bySize = false, dividers = false)
        val xs = p.slots.sortedBy { it.x0 }
        for (k in 1 until xs.size) assertTrue(xs[k].x0 >= xs[k - 1].x1 - 0.001f)
        for (s in xs) assertTrue(s.x0 >= -cam.wMm / 2f && s.x1 <= cam.wMm / 2f && s.y0 >= 0f && s.y1 <= cam.dMm)
    }
}
