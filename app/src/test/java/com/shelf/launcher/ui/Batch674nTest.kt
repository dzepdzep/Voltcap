package com.shelf.launcher.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 674n: KONKR x1.0 k674m Side menu (Save sync cut 13 px at the drawer's bottom); Flip 2 x1.0 f674k Bar tab arrow off centre. */
class Batch674nTest {
    private fun src(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name"),
    ).first { it.exists() }.readText()

    @Test fun fitUsesTheGapsAndRowsKeyColumnDraws() {
        val s = src("WinScenesHomes2.kt")
        val body = s.substring(s.indexOf("internal fun SideDrawer"), s.indexOf("internal fun SpotPoster"))
        // Old code: every key (status line included) counted as a 30 dp row with 2 dp gaps.
        assertTrue(!body.contains("fitRowHeight(maxHeight, keys.size, 2.dp"))
        assertTrue(body.contains("fitRowHeight(rowRoom, nAct, SIDE_KEY_GAP, 40.dp, 30.dp)"))
        assertTrue(body.contains("coerceAtLeast(SIDE_ART_MIN)"))
    }

    @Test fun konkrWindowFitsWithoutScrolling() {
        // KONKR x1.0: window 360 dp; name + console line 84 dp; 6 keys + 1 status line.
        val keysNeed = 30.dp * 6 + 4.dp * 5
        val statusNeed = 24.dp + 4.dp
        // (674o: 2 dp stay free for pixel rounding - f674o's status line came out 51 px of its 54.)
        val header = (360.dp - (keysNeed + statusNeed + 2.dp) - 84.dp).coerceAtLeast(SIDE_ART_MIN)
        val room = 360.dp - header - 84.dp - statusNeed - 2.dp
        assertNotNull(fitRowHeight(room, 6, 4.dp, 40.dp, 30.dp))
    }

    @Test fun barTabArrowsAreDrawnCentred() {
        val s = src("StyleIconBar.kt")
        val tab = s.substring(s.indexOf("private fun BarCategoryTab"), s.indexOf("private fun BarTabs"))
        assertTrue(!tab.contains("Text(\"◀\"") && !tab.contains("Text(\"▶\""))
        assertTrue(tab.contains("BarStepArrow(left = true") && tab.contains("BarStepArrow(left = false"))
    }
}
