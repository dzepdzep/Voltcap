package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 527: the owner's first category-look corrections (Tiles, Drawers, Cinema, Channel Surf, Arctic Panes). */
class CategoryLooks527Test {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText()

    @Test fun channelSurfLeavesTheOldChannelAndRollsInTheNewOneSoftly() {
        assertEquals(1f, ChannelChange.pictureAlpha(0f), 0f)
        assertEquals(0f, ChannelChange.pictureAlpha(0.5f), 0f)
        assertEquals(1f, ChannelChange.pictureAlpha(0.9f), 0f)
        // no pop: one step of 1/60 of the change never moves the picture more than ~1/3 of its alpha
        var c = 0f; var worst = 0f
        while (c < 1f) { worst = maxOf(worst, kotlin.math.abs(ChannelChange.pictureAlpha(c + 1f / 60f) - ChannelChange.pictureAlpha(c))); c += 1f / 60f }
        assertTrue("worst step $worst", worst <= 0.34f)
        assertEquals(0f, ChannelChange.snowAlpha(0.1f), 0f)
        assertTrue(ChannelChange.snowAlpha(0.52f) > 0.9f)
        val looks = src("ui/CategoryLooks.kt")
        assertTrue(looks.contains("val t = if (c < ChannelChange.SNOW_FROM) tiles.getOrNull(chans[0]) ?: t0 else t0"))
    }

    @Test fun cinemaAndArcticCrossFadeAndCinemasArtStaysUnderTheHeader() {
        val l = src("ui/CategoryLooks462.kt")
        assertEquals(2, Regex("LookCrossfade\\(t0, animate\\)").findAll(l).count())
        assertTrue(l.contains("Modifier.align(Alignment.CenterEnd).padding(top = CINEMA_ART_TOP_DP.dp)"))
    }

    @Test fun drawersShowWholeNamesAndTilesKeepNameAndCountTogether() {
        val looks = src("ui/CategoryLooks.kt")
        assertTrue(looks.contains("Box(Modifier.fillMaxWidth(if (tight) 0.94f else 0.88f).heightIn(min = cellH * if (tight) 0.3f else 0.36f)"))
        assertTrue(looks.contains("lineHeight = DRAWER_LINE_EM.em"))
        val cs = src("ui/CategoryScreen.kt")
        assertTrue(cs.contains("textAlign = androidx.compose.ui.text.style.TextAlign.Center, reserve = false"))
        assertTrue(cs.contains("if (gridState.canScrollForward) {"))
        assertTrue(src("ui/LauncherScreen.kt").contains("!HomeEditState.active && !viewModel.categoryScreenOpenState.value) {"))
    }

    @Test fun aShortDrawerStillHoldsTwoLinesAndItsPull() {
        // KONKR 960 x 640 (density 1.5): a drawer cell ~65 dp, its front 10 dp less. Two 11 sp lines (em-high), the card's
        // 3 + 2 dp insets, the tight margins (2 x 3 dp) and the 7 dp pull must fit; the old 8 dp margins + 12 dp pull + 12.5 sp
        // lines did not (that is why the names slid there).
        val front = 65f - 10f
        val tightNeed = 2 * 11f * DRAWER_LINE_EM + 2 * (3f + 2f) + 2 * 3f + 7f
        val oldNeed = 2 * 12.5f + 2 * (3f + 2f) + 2 * 8f + 12f
        assertTrue("tight $tightNeed <= $front", tightNeed <= front)
        assertTrue("old $oldNeed > $front", oldNeed > front)
        assertTrue(65 < DRAWER_TIGHT_DP)
    }
}
