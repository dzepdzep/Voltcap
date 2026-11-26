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
            boxes.associate { b -> b.kind to badgeRect(b, tile, dir).let { r -> plan.getValue(b.kind).let { s -> r.translate(s.dx, s.dy) to s.shown } } }
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
        assertEquals(BadgeSpot(0, 0), plan[BadgeKind.CONSOLE]); assertEquals(BadgeSpot(0, 0), plan[BadgeKind.PROGRESS])
    }

    @Test fun novaCubeCardStacksTheCountUnderTheConsole() {
        // RP Nova Cube home x1.0: a 98 dp card, the PS2 wordmark at 78 % of it and "6/10" in the top right - they overlapped
        val tile = IntSize(196, 266)
        val boxes = listOf(console(152), count(74))
        assertTrue(badgesTouch(badgeRect(boxes[0], tile, LayoutDirection.Ltr), badgeRect(boxes[1], tile, LayoutDirection.Ltr), gap))
        val r = rects(tile, boxes)
        assertEquals(BadgeSpot(0, 0), planBadges(tile, boxes, gap)[BadgeKind.CONSOLE]) // the console keeps its corner
        val c = r.getValue(BadgeKind.PROGRESS).first
        assertEquals(tile.width - 10 - 74, c.left) // still on the right
        assertEquals(6 + 26 + gap, c.top) // 6 dp under the console's pill
        assertClear(tile, boxes)
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
        assertEquals(tile.width - 6 - 152, c.left)
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
            assertTrue(f, s.contains("rememberBadgeLane()") && (s.contains(".badgeTile(lane)") || s.contains(".badgeTile(lane, LocalBadgeOverlayBottom.current)"))) // (680a: StyleArt passes the overlay band)
        }
        assertTrue(src("GameCard.kt").contains("laneBadge(lane, BadgeKind.PROGRESS, trophyAlign"))
        assertTrue(src("TileHome.kt").contains("laneBadge(lane, BadgeKind.PROGRESS, pillAt, chipPad)"))
    }
}

/** 676e: a coverless card's name plate is fixed CONTENT in the lane (RP Nova x1.3 Cube: the count was drawn over the name). */
class BadgeContent676eTest {
    private val console = BadgeBox(BadgeKind.CONSOLE, Alignment.TopStart, 6, 6, 152, 26)
    private val count = BadgeBox(BadgeKind.PROGRESS, Alignment.TopEnd, 10, 10, 74, 22)

    @Test fun theCountStacksClearOfAShortPlate() {
        val tile = IntSize(196, 266)
        val plate = BadgeBox(BadgeKind.CONTENT, Alignment.TopStart, 20, 120, 156, 40) // a 2-line name in the middle
        val plan = planBadges(tile, listOf(console, count, plate), 12)
        assertEquals(BadgeSpot(0, 0), plan[BadgeKind.CONSOLE])
        val c = badgeRect(count, tile, LayoutDirection.Ltr).translate(plan.getValue(BadgeKind.PROGRESS).dx, plan.getValue(BadgeKind.PROGRESS).dy)
        assertTrue(plan.getValue(BadgeKind.PROGRESS).shown)
        assertFalse(badgesTouch(c, badgeRect(plate, tile, LayoutDirection.Ltr), 12))
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
        assertTrue(s.contains(".laneContent(lane, 6.dp, shiftY ="))
        val l = listOf("src/main/java/com/shelf/launcher/ui/BadgeLane.kt", "app/src/main/java/com/shelf/launcher/ui/BadgeLane.kt").map { File(it) }.first { it.exists() }.readText()
        assertFalse("a badge with no room is not placed (no alpha-0 text left under another)", l.contains("placeWithLayer(0, 0) { alpha = 0f }"))
    }
}
