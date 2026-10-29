package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 400: Cardboard box row count (Auto / 1 / 2 / 3): case-fit decisions, forced fallbacks, packing and traversal helpers. */
class BoxRows400Test {
    private fun games(n: Int, vararg platforms: String) =
        List(n) { i -> Game("Game $i", platforms[i % platforms.size], 0, SourceType.ROM, "/x/g$i.rom") }

    private val W = 1920f; private val H = 1080f; private val D = 2.5f
    private fun plan(g: List<Game>) = BoxRows.plan(g, 420f, 0.6f, false)
    private fun decide(p: Features.BoxRowsPref, g: List<Game>, w: Float = W, h: Float = H) = BoxRows.decide(p, plan(g), w, h, 0.5f, 1f, D)

    @Test fun twoRowBoxKeepsTheOldGeometry() {
        val cam = boxCam(W, H, 0.5f, 1f)
        assertEquals(2, cam.rows)
        assertEquals(414f, cam.dMm, 0.001f)
        assertEquals(414f, BoxRows.depthFor(2, 120f), 0.001f)
    }

    @Test fun compactCasesFitThreeRowsDvdCasesDoNot() {
        for (p in listOf("NDS", "3DS", "GBA")) {
            val l = plan(games(10, p)).lane
            assertTrue("$p lane $l", BoxRows.fitsDepth(3, l))
        }
        for (p in listOf("PS2", "NES", "SNES", "Switch", "PSP")) {
            val l = plan(games(10, p)).lane
            assertFalse("$p lane $l", BoxRows.fitsDepth(3, l))
        }
    }

    @Test fun autoPicksThreeOnlyForCompactCategoriesThatFillMoreThanTwoRows() {
        assertEquals(3, decide(Features.BoxRowsPref.AUTO, games(200, "NDS")).rows)
        assertEquals(3, decide(Features.BoxRowsPref.AUTO, games(200, "3DS", "NDS", "GBA")).rows)
        // few games: the familiar two-row box; big cases: two rows
        assertEquals(2, decide(Features.BoxRowsPref.AUTO, games(5, "NDS")).rows)
        assertEquals(2, decide(Features.BoxRowsPref.AUTO, games(200, "PS2")).rows)
        // a mixed category with one long case in it cannot be three rows
        assertEquals(2, decide(Features.BoxRowsPref.AUTO, games(200, "NDS", "NDS", "PS2")).rows)
        // Auto never reports a limit
        assertFalse(decide(Features.BoxRowsPref.AUTO, games(200, "PS2")).fellBack)
    }

    @Test fun forcedCountsAreHonouredOrFallBackWithAReason() {
        val ds = games(40, "NDS"); val ps2 = games(40, "PS2")
        assertEquals(1, decide(Features.BoxRowsPref.ONE, ds).rows)
        assertEquals(2, decide(Features.BoxRowsPref.TWO, ps2).rows)
        val ok = decide(Features.BoxRowsPref.THREE, ds)
        assertEquals(3, ok.rows); assertFalse(ok.fellBack); assertNull(BoxRows.limitText(ok))
        val bad = decide(Features.BoxRowsPref.THREE, ps2)
        assertEquals(2, bad.rows); assertEquals(3, bad.requested)
        assertEquals(BoxRows.Limit.TOO_DEEP, bad.limit); assertNotNull(BoxRows.limitText(bad))
        // 3 rows are not offered even for a few games of compact cases on a screen too small to read them
        val tiny = decide(Features.BoxRowsPref.THREE, ds, 480f, 270f)
        assertEquals(2, tiny.rows); assertEquals(BoxRows.Limit.TOO_SMALL, tiny.limit)
        // and Auto does not choose it there
        assertEquals(2, decide(Features.BoxRowsPref.AUTO, games(200, "NDS"), 480f, 270f).rows)
    }

    @Test fun threeRowPackingHasThreeDistinctLanesInsideTheBox() {
        val g = games(150, "NDS", "3DS", "GBA")
        val p = plan(g)
        val cam = boxCam(W, H, 0.5f, 1f, 3, BoxRows.laneFor(3, p))
        assertTrue(cam.dMm <= BoxRows.MAX_DEPTH_MM)
        for (messy in listOf(false, true)) {
            val slots = packBoxes(g, cam, 0.6f, messy)
            assertTrue(slots.map { it.row }.toSet() == setOf(0, 1, 2))
            for (s in slots) {
                assertTrue(s.y0 >= BOX_MARGIN - 0.001f && s.y1 <= cam.dMm - BOX_MARGIN + 0.001f)
                assertTrue(s.len <= cam.lane + 0.001f)
            }
            // rows never meet: every case of row r ends before every case of row r+1 begins
            for (box in slots.groupBy { it.box }.values) for (r in 0..1) {
                val a = box.filter { it.row == r }; val b = box.filter { it.row == r + 1 }
                if (a.isNotEmpty() && b.isNotEmpty()) assertTrue("rows $r/${r + 1}", a.maxOf { it.y1 } < b.minOf { it.y0 })
            }
            // a box fills row 0, 1, 2 before the next box starts
            val order = slots.map { it.box * 3 + it.row }
            assertEquals(order.sorted(), order)
        }
    }

    @Test fun threeRowsHoldMoreThanTwoPerBox() {
        val g = games(300, "NDS")
        val p = plan(g)
        val two = packBoxes(g, boxCam(W, H, 0.5f, 1f, 2, BoxRows.laneFor(2, p)), 0.6f, false).last().box
        val three = packBoxes(g, boxCam(W, H, 0.5f, 1f, 3, BoxRows.laneFor(3, p)), 0.6f, false).last().box
        assertTrue("2 rows: $two boxes, 3 rows: $three boxes", three < two)
    }

    @Test fun oneToThreeGamesAndEmptyCategories() {
        for (n in 0..3) for (pref in Features.BoxRowsPref.values()) {
            val g = games(n, "NDS")
            val d = decide(pref, g)
            val cam = boxCam(W, H, 0.5f, 1f, d.rows, BoxRows.laneFor(d.rows, plan(g)))
            val slots = packBoxes(g, cam, 0.6f, false)
            assertEquals(n, slots.size)
            assertTrue(slots.all { it.box == 0 && it.row == 0 })
        }
    }

    @Test fun oneRowBoxIsCentredAndShallow() {
        val g = games(10, "NDS"); val p = plan(g)
        val cam = boxCam(W, H, 0f, 1f, 1, BoxRows.laneFor(1, p))
        assertTrue(cam.dMm < 200f)
        val top = cam.sy(-cam.t, cam.wallH); val bottom = cam.sy(cam.dMm + cam.t, cam.wallH)
        assertEquals("centred", 0.995f * H - bottom, top - 0.035f * H, 2f)
    }

    @Test fun rowTraversal() {
        // 3 rows: down from the back row goes to the middle, then the front; ends of the box have no further row.
        assertEquals(1, BoxRows.rowStep(0, 1, 3)); assertEquals(2, BoxRows.rowStep(1, 1, 3)); assertNull(BoxRows.rowStep(2, 1, 3))
        assertNull(BoxRows.rowStep(0, -1, 3)); assertEquals(1, BoxRows.rowStep(2, -1, 3))
        // 2 rows are as before
        assertEquals(1, BoxRows.rowStep(0, 1, 2)); assertNull(BoxRows.rowStep(1, 1, 2))
        // nearest-row search order when a row is empty
        assertEquals(2, boxRowByDistance(1, 1)); assertEquals(0, boxRowByDistance(1, 2))
    }
}
