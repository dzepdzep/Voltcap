package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v0.6.67 (update 451): More in the layout windows that showed every action at once — nothing removed, Play never hidden. */
class PopupMore451Test {
    @Test fun moreOnlyWhereTheWindowWasAChipWall() {
        for (s in listOf(WinScene.VOLT_PANEL, WinScene.INDEX_CARD, WinScene.TWIN_HINGE, WinScene.POCKET_LCD, WinScene.TILE_CARD, WinScene.HERO_BANNER))
            assertTrue("$s gets More", s in MORE_SCENES)
        // Their own compact designs / menu lists stay as they are.
        for (s in listOf(WinScene.WII_PREVIEW, WinScene.CINEMA_PREMIERE, WinScene.ARCTIC_DRAWER, WinScene.BUBBLE_CARD, WinScene.CARD_BACK,
            WinScene.MEMCARD_INFO, WinScene.AIRY_PANEL, WinScene.CROSS_OPTIONS, WinScene.ARCADE_MENU, WinScene.TERM_RUN))
            assertFalse("$s keeps its own window", s in MORE_SCENES)
    }

    private val src = listOf("src/main/java/com/shelf/launcher/ui/GameWindow.kt", "app/src/main/java/com/shelf/launcher/ui/GameWindow.kt")
        .map(::File).first { it.exists() }.readText()

    @Test fun everyActionStaysReachableAndPlayIsNeverHidden() {
        val b = src.substring(src.indexOf("val compactMore = scene in MORE_SCENES"), src.indexOf("internal val MORE_SCENES"))
        // Play is added unconditionally; Details and Back stay; Inspect stays in view; the rest are all in More.
        assertTrue(b.contains("add(WinAction(look.play.lowercase().replaceFirstChar { it.uppercase() }, I.PlayArrow, onPlay, focus = playFocus, primary = true))"))
        assertTrue(b.contains("moreOpen -> allTools + WinAction(\"Less\""))
        assertTrue(b.contains("else -> allTools.filter { it.label == \"Inspect\" } +"))
        assertTrue(b.contains("val chips = if (showAll) allChips else allChips.filter { it.urgent }"))
        assertTrue(b.contains("if (perf && showAll) add(WinAction(PERF_LABEL"))
        // B closes More before the window; focus goes to the first revealed action and back to More.
        assertTrue(b.contains("BackHandler(enabled = compactMore && moreOpen) { moreOpen = false; moreRefocus(moreF) }"))
        assertTrue(b.contains("{ moreOpen = true; moreRefocus(optionsF) }"))
        // A new game in the window starts collapsed.
        assertTrue(b.contains("var moreOpen by remember(g.stableKey) { mutableStateOf(false) }"))
    }
}
