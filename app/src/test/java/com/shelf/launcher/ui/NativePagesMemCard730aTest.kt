package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 730a (class C): the Memory card layout's save browser draws its own screens on the glass panel - not the shared page bodies. */
class NativePagesMemCard730aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun memoryCardHasTheBrowsersOwnScreens() {
        assertEquals(WinScene.MEMCARD_INFO, winSceneFor(HomeLayout.MEMCARD))
        assertTrue(nativePages(WinScene.MEMCARD_INFO))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.MEMCARD_INFO, p))
        val f = src("StyleMemoryCard.kt")
        val start = f.indexOf("internal fun MemCardWindow(")
        val scene = f.substring(start, f.indexOf("\n}\n", start))
        assertTrue(scene.contains("MemCardPage(ctx, pg, pageLive, Modifier.fillMaxSize())"))
        assertTrue(scene.contains(".nativeBumpers(ctx)"))
        assertTrue(scene.contains("McPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading)"))
        // the panel sits between the title and the choices, measured - never a fixed share that can run under the choices
        assertTrue(scene.contains("padding(end = 8.dp, top = 4.dp, bottom = bottomRoom)"))
        assertFalse(scene.contains("ctx.pageBody("))
        // only Performance falls back to the shared page
        assertEquals(1, src("WinPagesMemCard.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun pageNames() {
        assertEquals(listOf("SAVE", "INFORMATION", "MEMORY BLOCKS", "COMMANDS"), NATIVE_TAB_ORDER.map(::mcPageName))
    }

    @Test fun entryWords() {
        assertEquals("Saved 12 Mar 2024", mcEntryLine(true, "12 Mar 2024"))
        assertEquals("Saved", mcEntryLine(true, null)); assertEquals("Saved", mcEntryLine(true, " "))
        assertEquals("Empty block", mcEntryLine(false, "12 Mar 2024"))
        assertEquals("3 / 12 blocks saved", mcBlocksLine(3, 12)); assertEquals("1 / 1 block saved", mcBlocksLine(1, 1))
        assertEquals("25 pts", mcPoints(25)); assertEquals("1 pt", mcPoints(1)); assertNull(mcPoints(null))
    }

    @Test fun blockStrip() {
        assertEquals(0 to 0, mcBlocks(0, 0))
        assertEquals(12 to 3, mcBlocks(3, 12)); assertEquals(12 to 12, mcBlocks(15, 12)); assertEquals(1 to 0, mcBlocks(-2, 1))
        assertEquals(40 to 40, mcBlocks(40, 40))
        // long lists: 40 blocks, any progress shows, all lit only when all earned
        assertEquals(40 to 0, mcBlocks(0, 120)); assertEquals(40 to 1, mcBlocks(1, 120)); assertEquals(40 to 20, mcBlocks(60, 120))
        assertEquals(40 to 39, mcBlocks(119, 120)); assertEquals(40 to 40, mcBlocks(120, 120))
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesMemCard.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
