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
        assertTrue(src("ui/CategoryLooks.kt").contains("Box(Modifier.fillMaxWidth(0.88f).heightIn(min = cellH * 0.36f)"))
        val cs = src("ui/CategoryScreen.kt")
        assertTrue(cs.contains("textAlign = androidx.compose.ui.text.style.TextAlign.Center, reserve = false"))
        assertTrue(cs.contains("if (gridState.canScrollForward) {"))
        assertTrue(src("ui/LauncherScreen.kt").contains("!HomeEditState.active && !viewModel.categoryScreenOpenState.value) {"))
    }
}
