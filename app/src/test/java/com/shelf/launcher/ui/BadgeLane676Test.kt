package com.shelf.launcher.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 676a: the badge lane (BadgeLane.kt) - console and achievement badges never closer than 6 dp. Numbers in px at density 2. */
class BadgeLane676Test {
    private val gap = 12 // 6 dp
    private fun console(w: Int, at: Alignment = Alignment.TopStart) = BadgeBox(BadgeKind.CONSOLE, at, 6, 6, w, 26)
    private fun count(w: Int, at: Alignment = Alignment.TopEnd) = BadgeBox(BadgeKind.PROGRESS, at, 10, 10, w, 22)

    private fun rects(tile: IntSize, boxes: List<BadgeBox>, dir: LayoutDirection = LayoutDirection.Ltr) =
        planBadges(tile, boxes, gap, dir).let { plan ->
            boxes.associate { b -> b.kind to plan.getValue(b.kind).let { s -> badgeShownRect(b, tile, dir, s) to s.shown } }
        }

    private fun assertClear(tile: IntSize, boxes: List<BadgeBox>) {
        val r = rects(tile, boxes)
        val shown = r.values.filter { it.second }.map { it.first }
        for (i in shown.indices) for (j in i + 1 until shown.size) assertFalse("${shown[i]} / ${shown[j]}", badgesTouch(shown[i], shown[j], gap))
        for (s in shown) assertTrue("$s inside $tile", s.left >= 0 && s.top >= 0 && s.right <= tile.width && s.bottom <= tile.height)
    }

    @Test fun roomyTileKeepsBothCorners() {
        // a 200 dp tile: a 120 dp wordmark and the count side by side, nothing moves
        val plan = planBadges(IntSize(400, 560), listOf(console(240), count(80)), gap)
        assertEquals(1f, plan.getValue(BadgeKind.CONSOLE).scale); assertEquals(1f, plan.getValue(BadgeKind.PROGRESS).scale)
        assertEquals(0, plan.getValue(BadgeKind.PROGRESS).dx + plan.getValue(BadgeKind.PROGRESS).dy)
    }

    @Test fun novaCubeCardShrinksBothOnTheSameRow() {
        // RP Nova Cube home x1.0: a 98 dp card, the PS2 wordmark at 78 % of it and "6/10" in the top right - they overlapped.
        // 676w (owner): no second row - both shrink by the same factor, each in its own corner, 6 dp apart.
        val tile = IntSize(196, 266)
        val boxes = listOf(console(152), count(74))
        assertTrue(badgesTouch(badgeRect(boxes[0], tile, LayoutDirection.Ltr), badgeRect(boxes[1], tile, LayoutDirection.Ltr), gap))
        val plan = planBadges(tile, boxes, gap)
        val c = plan.getValue(BadgeKind.CONSOLE); val p = plan.getValue(BadgeKind.PROGRESS)
        assertEquals(c.scale, p.scale, 0f) // one factor for both
        assertTrue("${c.scale}", c.scale in BADGE_MIN_SCALE..0.8f)
        val r = rects(tile, boxes)
        val cr = r.getValue(BadgeKind.CONSOLE).first; val pr = r.getValue(BadgeKind.PROGRESS).first
        assertEquals(6, cr.left); assertEquals(6, cr.top) // the console's corner point stays
        assertEquals(tile.width - 10, pr.right); assertEquals(10, pr.top) // the count's corner point stays: same row
        assertTrue(pr.top < cr.bottom) // side by side, not under it
        assertTrue("gap ${pr.left - cr.right}", pr.left - cr.right in gap..gap + 4) // a small, consistent gap
        assertClear(tile, boxes)
    }

    @Test fun neverASecondRow() {
        for (tw in listOf(120, 150, 196, 260)) for (cw in listOf(60, 90, 150)) for (pw in listOf(40, 74, 96)) {
            val tile = IntSize(tw, tw * 4 / 3)
            if (cw + 12 > tw || pw + 20 > tw) continue
            val boxes = listOf(console(cw), count(pw))
            val plan = planBadges(tile, boxes, gap)
            for (b in boxes) assertEquals(0, plan.getValue(b.kind).dx + plan.getValue(b.kind).dy) // nothing moves off its corner
        }
    }

    @Test fun x13AndSmallTilesStayClearWherePossible() {
        // KONKR / RP Classic small covers, x1.0 and x1.3 (wider words), console in either top corner
        for (tw in listOf(88, 120, 150, 196, 260)) for (cw in listOf(40, 90, 150)) for (pw in listOf(40, 74, 96)) for (cc in listOf(Alignment.TopStart, Alignment.TopEnd)) {
            val tile = IntSize(tw, tw * 4 / 3)
            if (cw + 12 > tw || pw + 20 > tw) continue
            assertClear(tile, listOf(console(cw, cc), count(pw)))
        }
    }

    @Test fun aTileTooSmallForBothLeavesTheCountOutNeverOverIt() {
        val tile = IntSize(100, 60)
        val plan = planBadges(tile, listOf(console(88), count(74)), gap)
        assertTrue(plan.getValue(BadgeKind.CONSOLE).shown)
        assertFalse(plan.getValue(BadgeKind.PROGRESS).shown)
    }

    @Test fun rightToLeftMirrors() {
        val tile = IntSize(196, 266)
        val r = rects(tile, listOf(console(152), count(74)), LayoutDirection.Rtl)
        val c = r.getValue(BadgeKind.CONSOLE).first; val p = r.getValue(BadgeKind.PROGRESS).first
        assertEquals(tile.width - 6, c.right) // the console's corner point stays on the right in RTL
        assertFalse(badgesTouch(c, p, gap))
    }

    @Test fun everyTileThatDrawsBothJoinsTheLane() {
        val dir = listOf("src/main/java/com/shelf/launcher/ui", "app/src/main/java/com/shelf/launcher/ui").map(::File).first { it.exists() }
        fun src(n: String) = File(dir, n).readText()
        // the badges themselves
        assertTrue(src("ConsoleBadge.kt").split("laneBadge(lane, BadgeKind.CONSOLE").size == 3)
        assertTrue(src("StyleHomes.kt").split("laneBadge(inLane, BadgeKind.PROGRESS").size == 3)
        // the tiles: a lane, the tile's size and the badges handed it
        for (f in listOf("GameCard.kt", "TileHome.kt", "GridHome.kt", "StyleHomes.kt", "StyleCaseStacks.kt")) {
            val s = src(f)
            assertTrue(f, s.contains("rememberBadgeLane()") && s.contains(".badgeTile(lane)"))
        }
        assertTrue(src("GameCard.kt").contains("laneBadge(lane, BadgeKind.PROGRESS, trophyAlign"))
        assertTrue(src("TileHome.kt").contains("laneBadge(lane, BadgeKind.PROGRESS, pillAt, chipPad)"))
    }
}

/** 676e: a coverless card's name plate is fixed CONTENT in the lane (RP Nova x1.3 Cube: the count was drawn over the name). */
class BadgeContent676eTest {
    private val console = BadgeBox(BadgeKind.CONSOLE, Alignment.TopStart, 6, 6, 152, 26)
    private val count = BadgeBox(BadgeKind.PROGRESS, Alignment.TopEnd, 10, 10, 74, 22)

    @Test fun theCountStaysClearOfAShortPlate() {
        val tile = IntSize(196, 266)
        val plate = BadgeBox(BadgeKind.CONTENT, Alignment.TopStart, 20, 120, 156, 40) // a 2-line name in the middle
        val plan = planBadges(tile, listOf(console, count, plate), 12)
        assertTrue(plan.getValue(BadgeKind.PROGRESS).shown)
        val c = badgeShownRect(count, tile, LayoutDirection.Ltr, plan.getValue(BadgeKind.PROGRESS))
        assertFalse(badgesTouch(c, badgeRect(plate, tile, LayoutDirection.Ltr), 12))
        assertFalse(badgesTouch(c, badgeShownRect(console, tile, LayoutDirection.Ltr, plan.getValue(BadgeKind.CONSOLE)), 12))
    }

    @Test fun noRoomLeavesTheCountOutNeverOverTheName() {
        // the Nova x1.3 Cube tile: 162 x 224 px, the PS2 pill 126 x 29, the count 101 x 86, a 4-line plate 134 x 168
        val tile = IntSize(162, 224)
        val plan = planBadges(tile, listOf(BadgeBox(BadgeKind.CONSOLE, Alignment.TopStart, 8, 8, 126, 29),
            BadgeBox(BadgeKind.PROGRESS, Alignment.TopEnd, 4, 4, 101, 86), BadgeBox(BadgeKind.CONTENT, Alignment.TopStart, 14, 28, 134, 168)), 14)
        assertTrue(plan.getValue(BadgeKind.CONSOLE).shown)
        assertFalse(plan.getValue(BadgeKind.PROGRESS).shown)
        val s = listOf("src/main/java/com/shelf/launcher/ui/StyleHomes.kt", "app/src/main/java/com/shelf/launcher/ui/StyleHomes.kt").map { File(it) }.first { it.exists() }.readText()
        assertEquals(2, s.split("else -> StyleNamePlate(titles.display(g.name), lane = lane)").size - 1)
        assertTrue(s.contains("modifier = Modifier.laneContent(lane, 6.dp)"))
        val l = listOf("src/main/java/com/shelf/launcher/ui/BadgeLane.kt", "app/src/main/java/com/shelf/launcher/ui/BadgeLane.kt").map { File(it) }.first { it.exists() }.readText()
        assertFalse("a badge with no room is not placed (no alpha-0 text left under another)", l.contains("placeWithLayer(0, 0) { alpha = 0f }"))
    }
}
