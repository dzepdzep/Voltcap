package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 416: a mixed N64 / NES / GB / DS / Switch / PS2 collection packs in both orders, 1-3 rows, Neat / Messy: real sizes, no overlap, nothing outside the box, D-pad order complete. */
class BoxMixed416Test {
    private val mix = listOf("N64", "NES", "GB", "NDS", "Switch", "PS2")
    private fun games(n: Int) = List(n) { i -> Game("Mixed $i", mix[(i * 7 + i / 5) % mix.size], 0, SourceType.ROM, "/fixture/m$i.rom") }

    @Test fun fixtureReallyMixesPhysicalSizes() {
        val g = games(60)
        val lens = g.map { caseMm(it, false).len }.toSet()
        val thick = g.map { caseMm(it, false).thick }.toSet()
        assertTrue("lens $lens", lens.size >= 3)
        assertTrue("thick $thick", thick.size >= 3)
        assertTrue(g.map { sizeGroup(it) }.toSet().size >= 3)
    }

    @Test fun bothOrdersAllRowsNeatAndMessyFitWithoutIntersection() {
        val g = games(150)
        for (bySize in listOf(false, true)) for (rows in 1..3) for (messy in listOf(false, true)) {
            val lane = BoxRows.laneFor(rows, BoxRows.plan(g, 420f, 0.6f, messy))
            val c = boxCam(1920f, 1080f, 0.5f, 1f, rows, lane)
            val p = packBoxesFull(g, c, 0.6f, messy, bySize, dividers = true)
            val tag = "bySize=$bySize rows=$rows messy=$messy"
            // D-pad order: every game exactly once
            assertEquals(tag, (0 until g.size).toList(), p.order.sorted())
            for ((_, idx) in p.slots.indices.groupBy { p.slots[it].box to p.slots[it].row }) {
                val xs = idx.map { p.slots[it] }.sortedBy { it.x0 }
                for (k in 1 until xs.size) assertTrue("$tag overlap", xs[k].x0 >= xs[k - 1].x1 - 0.001f)
            }
            for (i in g.indices) {
                val s = p.slots[i]; val m = caseMm(g[i], messy)
                assertEquals(tag, m.thick, s.thick, 0f); assertEquals(tag, minOf(m.len, c.lane), s.len, 0f); assertEquals(tag, m.stand, s.stand, 0f)
                assertTrue("$tag left wall", s.x0 >= -c.wMm / 2f + BOX_MARGIN - 0.001f)
                assertTrue("$tag right wall ${s.x1}", s.x1 <= c.wMm / 2f - BOX_MARGIN + 0.01f + 3f)
                assertTrue("$tag back wall", s.y0 >= BOX_MARGIN - 6f - 0.001f)
                assertTrue("$tag front wall ${s.y1}", s.y1 <= c.dMm - BOX_MARGIN + 0.001f + 6f)
            }
            // two cases of different rows never overlap in depth (rows are separate lanes)
            for ((_, idx) in p.slots.indices.groupBy { p.slots[it].box }) {
                for (r in 0 until rows - 1) {
                    val a = idx.map { p.slots[it] }.filter { it.row == r }; val b = idx.map { p.slots[it] }.filter { it.row == r + 1 }
                    if (a.isEmpty() || b.isEmpty()) continue
                    assertTrue("$tag rows touch ${a.maxOf { it.y1 }} ${b.minOf { it.y0 }}", a.maxOf { it.y1 } <= b.minOf { it.y0 } + 0.001f)
                }
            }
            // a divider sits inside one row between two cases and never inside one
            if (bySize) for (d in p.dividers) for (s in p.slots) if (s.box == d.box && s.row == d.row) assertTrue("$tag divider in case", d.x1 <= s.x0 + 0.001f || d.x0 >= s.x1 - 0.001f)
        }
    }
}
