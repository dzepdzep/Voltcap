package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 726a (class C): the Circuit board window draws the datasheet's own sections - not the shared page bodies. */
class NativePagesChip726aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun circuitHasItsOwnSections() {
        assertEquals(WinScene.CHIP_SHEET, winSceneFor(HomeLayout.CIRCUIT))
        assertTrue(nativePages(WinScene.CHIP_SHEET))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.CHIP_SHEET, p))
        val sheet = src("WinScenesHomes2.kt").let { it.substring(it.indexOf("internal fun ChipSheet("), it.indexOf("internal fun LibraryPage(")) }
        assertFalse("no shared page body left in the sheet", sheet.contains("ctx.pageBody("))
        assertTrue(sheet.contains("ChipPage(ctx, pg, pageLive, pcb, trace, silk, Modifier.fillMaxSize())"))
        assertTrue(sheet.contains(".nativeBumpers(ctx)") && sheet.contains("slots = sceneKeySlots(ctx)"))
        // only Performance falls back to the shared page
        assertEquals(1, src("WinPagesChip.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun sectionNamesAndNumbers() {
        assertEquals("PINOUT", chipPageName(WinPage.MAIN))
        assertEquals("CHARACTERISTICS", chipPageName(WinPage.DETAILS))
        assertEquals("PIN STATES", chipPageName(WinPage.TROPHIES))
        assertEquals("JUMPERS", chipPageName(WinPage.OPTIONS))
        assertEquals(listOf(1, 2, 3, 4), NATIVE_TAB_ORDER.map(::chipSectionNo))
    }

    @Test fun pinsJumpersAndStates() {
        assertEquals("P01", chipPin(0)); assertEquals("P12", chipPin(11)); assertEquals("P120", chipPin(119))
        assertEquals("HIGH", chipPinState(true)); assertEquals("LOW", chipPinState(false))
        assertEquals("25 PT", chipPoints(25)); assertEquals("— PT", chipPoints(null))
        assertEquals("J1", chipJumper(0)); assertEquals("J10", chipJumper(9))
    }

    @Test fun noTruncationInTheSections() {
        val c = src("WinPagesChip.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
    }
}
