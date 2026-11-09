package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 501 (staging only): coverless row names on 3 lines, the pulled-out shelf case's shadow on the board. */
class Batch501Test {
    private fun src(rel: String): String = listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    @Test fun coverlessNameGetsThreeLines() {
        assertEquals(3, NO_ART_NAME_LINES)
        val t = src("ui/GameCard.kt")
        val i = t.indexOf("maxLines = NO_ART_NAME_LINES")
        assertTrue(i > 0 && t.lastIndexOf("artUrl == null", i) > i - 1600)
    }

    @Test fun pulledCaseShadowFollowsThePull() {
        assertEquals(0f, pulledShadowAlpha(0f, Features.ShelfLook.WOOD), 0f)
        assertEquals(0.5f, pulledShadowAlpha(1f, Features.ShelfLook.WOOD), 1e-6f)
        assertEquals(0.28f, pulledShadowAlpha(1f, Features.ShelfLook.GLASS), 1e-6f)
        assertEquals(0.5f, pulledShadowAlpha(2f, Features.ShelfLook.WOOD), 1e-6f) // never darker than at rest
        assertTrue(src("ui/ShelfHome.kt").contains("pulledShadowAlpha(turn, Features.shelfLook.state.value)"))
    }

    @Test fun menuSheetFitsAShortScreen() {
        assertTrue(MENU_SHEET_MAX_H <= 0.95f)
        val t = src("ui/DialogChrome.kt")
        val f = t.substring(t.indexOf("fun ShelfMenuDialog("), t.indexOf("private fun DialogHeader("))
        assertTrue(f.contains(".heightIn(max = screen.height * MENU_SHEET_MAX_H)"))
        assertTrue(f.contains(".weight(1f, fill = false)")) // the list gives up the room and scrolls
    }

    @Test fun shelfSpinesGetACaseTopUnderTheShelfAbove() {
        assertEquals(8.5f, shelfCapHeight(androidx.compose.ui.unit.Dp(100f), androidx.compose.ui.unit.Dp(200f)).value, 0.01f)
        assertEquals(11f, shelfCapHeight(androidx.compose.ui.unit.Dp(168f), androidx.compose.ui.unit.Dp(200f)).value, 0.01f) // kept under 5.5 %
        val t = src("ui/ShelfHome.kt")
        assertTrue(t.contains("drawCaseTop(Features.spineDepth.state.value, caseTop.vanish - inset, caseTop.cardboard)")); assertTrue(cardboardBoxConsole("SNES") && !cardboardBoxConsole("PS2")); assertTrue(t.contains("LocalCaseTopVanish provides"))
    }

    @Test fun overlayListSwitchSitsBesideCloseNotOverTheTabs() {
        val t = src("ui/RaOverlayView.kt")
        val top = t.indexOf("addView(header, LinearLayout.LayoutParams(0")
        assertTrue(t.indexOf("addView(modeArrows)") in top..(top + 600))
        assertEquals(1, Regex("addView\\(modeArrows\\)").findAll(t).count())
    }

    @Test fun qaCardsUseTheVaultRenderer() {
        assertTrue(src("ui/QaCardSheet.kt").contains("SwayingModel(c, null, \"QA card\", \"PS1\""))
        assertEquals(listOf(0f, 40f, 90f), QaCards.angles("0, 40,x,90"))
    }
}
