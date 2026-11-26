package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 732a (class C): File select's turned-over file draws its own screens on its inset panel - not the shared page bodies. */
class NativePagesFile732aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun fileSelectHasTheFilesOwnScreens() {
        assertEquals(WinScene.FILE_CARD, winSceneFor(HomeLayout.FILESELECT))
        assertTrue(nativePages(WinScene.FILE_CARD))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.FILE_CARD, p))
        val f = src("WinScenes.kt")
        val start = f.indexOf("private fun FileCardBack(")
        val scene = f.substring(start, f.indexOf("\n}\n", start))
        assertTrue(scene.contains("FilePage(ctx, pg, pageLive, base, tight, Modifier.fillMaxSize())"))
        assertTrue(scene.contains(".nativeBumpers(ctx)"))
        // the top row names the page after the old one has gone (rememberHeadingPage), never the shared page name / body
        assertTrue(scene.contains("FilePageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, ink)"))
        assertFalse(scene.contains("ctx.pageBody("))
        assertFalse(scene.contains("pageName(ctx.page)"))
        // only Performance falls back to the shared page
        assertEquals(1, src("WinPagesFile.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun pageNamesMatchTheButtons() {
        assertEquals(listOf("FILE", "DETAILS", "STARS", "OPTIONS"), NATIVE_TAB_ORDER.map(::filePageName))
        // the file's Details button is called Details (WinLook FILESELECT)
        val look = src("GameWindow.kt").let { it.substring(it.indexOf("HomeLayout.FILESELECT -> WinLook(")).substringBefore("\n        )") }
        assertTrue(look.contains("details = \"Details\""))
        assertEquals("DETAILS", filePageName(WinPage.DETAILS))
    }

    @Test fun starWords() {
        assertEquals("3 / 12 STARS", fileStarsLine(3, 12)); assertEquals("1 / 1 STAR", fileStarsLine(1, 1))
        assertEquals("0 / 0 STARS", fileStarsLine(0, 0)); assertEquals("12 / 12 STARS", fileStarsLine(15, 12))
        assertEquals("GOT IT 12 Mar 2024", fileEntryLine(true, "12 Mar 2024"))
        assertEquals("GOT IT", fileEntryLine(true, null)); assertEquals("GOT IT", fileEntryLine(true, " "))
        assertEquals("NOT YET", fileEntryLine(false, "12 Mar 2024"))
        assertEquals("+25", filePoints(25)); assertNull(filePoints(null))
    }

    @Test fun starRowKeepsToOneRow() {
        assertEquals(12 to 3, mcBlocks(3, 12, FILE_STARS_MAX))
        assertEquals(24 to 1, mcBlocks(1, 120, FILE_STARS_MAX)); assertEquals(24 to 24, mcBlocks(120, 120, FILE_STARS_MAX))
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesFile.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
