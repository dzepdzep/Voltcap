package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 415: mixed-size Cardboard box packing: original order untouched, grouping by real size, dividers, no overlaps. */
class BoxPack415Test {
    private val platforms = listOf("N64", "NES", "SNES", "GB", "GBA", "NDS", "Switch", "PS1", "PS2", "Wii")
    private fun games(n: Int) = List(n) { i -> Game("Game $i", platforms[i % platforms.size], 0, SourceType.ROM, "/x/g$i.rom") }
    private fun cam(rows: Int = 2) = boxCam(1920f, 1080f, 0.5f, 1f, rows)

    @Test fun originalOrderIsTheOldPackingExactly() {
        val g = games(90); val cam = cam()
        val old = packBoxes(g, cam, 0.6f, false)
        val full = packBoxesFull(g, cam, 0.6f, false, bySize = false, dividers = true)
        assertTrue(full.dividers.isEmpty())
        assertEquals((0 until 90).toList(), full.order.toList())
        for (i in old.indices) { assertEquals(old[i].x0, full.slots[i].x0, 0f); assertEquals(old[i].box, full.slots[i].box); assertEquals(old[i].row, full.slots[i].row); assertEquals(old[i].y0, full.slots[i].y0, 0f) }
    }

    @Test fun groupedOrderIsAPermutationKeepingSortOrderInsideEachGroup() {
        val g = games(90)
        val order = boxOrder(g, true)
        assertEquals((0 until 90).toList(), order.sorted())
        for (grp in order.groupBy { sizeGroup(g[it]) }.values) assertEquals(grp.sorted(), grp)
        // groups are contiguous
        val seq = order.map { sizeGroup(g[it]) }
        assertEquals(seq.distinct().size, seq.zipWithNext().count { it.first != it.second } + 1)
    }

    @Test fun groupedPackingNeverOverlapsOrLeavesTheBoxAndKeepsRealSizes() {
        val g = games(120)
        for (rows in 1..3) for (messy in listOf(false, true)) {
            val cam = cam(rows)
            val gg = if (rows == 3) games(120).filter { it.platform in listOf("GB", "GBA", "NDS") } else g
            val c = if (rows == 3) boxCam(1920f, 1080f, 0.5f, 1f, 3, BoxRows.laneFor(3, BoxRows.plan(gg, 420f, 0.6f, messy))) else cam
            val p = packBoxesFull(gg, c, 0.6f, messy, bySize = true, dividers = true)
            val plain = packBoxes(gg, c, 0.6f, messy)
            for (i in gg.indices) { assertEquals(plain[i].thick, p.slots[i].thick, 0f); assertEquals(plain[i].len, p.slots[i].len, 0f); assertEquals(plain[i].stand, p.slots[i].stand, 0f) }
            for ((_, row) in p.slots.indices.groupBy { p.slots[it].box to p.slots[it].row }) {
                val xs = row.map { p.slots[it] }.sortedBy { it.x0 }
                for (k in 1 until xs.size) assertTrue("overlap rows=$rows messy=$messy ${xs[k - 1].x0} ${xs[k - 1].x1} ${xs[k].x0}", xs[k].x0 >= xs[k - 1].x1 - 0.001f)
                for (s in xs) { assertTrue(s.x1 <= c.wMm / 2f - BOX_MARGIN + 0.01f + 3f); assertTrue(s.y0 >= BOX_MARGIN - 0.001f && s.y1 <= c.dMm - BOX_MARGIN + 0.001f) }
            }
            // a divider never touches a case
            for (d in p.dividers) for (s in p.slots) if (s.box == d.box && s.row == d.row) assertTrue("divider inside a case", d.x1 <= s.x0 + 0.001f || d.x0 >= s.x1 - 0.001f)
        }
    }

    @Test fun dividersOnlyBetweenGroupsAndOnlyWhenAsked() {
        val g = games(60); val cam = cam()
        assertTrue(packBoxesFull(g, cam, 0.6f, false, bySize = true, dividers = false).dividers.isEmpty())
        val p = packBoxesFull(g, cam, 0.6f, false, bySize = true, dividers = true)
        assertTrue(p.dividers.isNotEmpty())
        for (d in p.dividers) {
            val left = p.order.map { p.slots[it] }.filter { it.box == d.box && it.row == d.row && it.x1 <= d.x0 + 0.001f }
            val right = p.order.map { p.slots[it] }.filter { it.box == d.box && it.row == d.row && it.x0 >= d.x1 - 0.001f }
            assertTrue(left.isNotEmpty() && right.isNotEmpty())
        }
    }
}
