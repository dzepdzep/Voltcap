package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 733a (class C): Case flow's opened case draws its own pages in the picture's glass well - not the shared page bodies. */
class NativePagesCase733aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun caseFlowHasTheCasesOwnPages() {
        assertEquals(WinScene.CASE_MEDIA, winSceneFor(HomeLayout.CASEFLOW))
        assertTrue(nativePages(WinScene.CASE_MEDIA))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.CASE_MEDIA, p))
        val f = src("WinScenes.kt")
        val start = f.indexOf("private fun CaseFlowMedia(")
        val scene = f.substring(start, f.indexOf("\n}\n", start))
        assertTrue(scene.contains("if (ctx.page != WinPage.MAIN) CaseFlowPageArea(ctx, accent, Modifier.weight(1f, fill = false))"))
        assertTrue(scene.contains(".nativeBumpers(ctx)"))
        // never the shared page body; on a page the pills keep Play in the first of two slots
        assertFalse(scene.contains("ctx.pageBody("))
        assertTrue(scene.contains("slots = if (ctx.page == WinPage.MAIN) 0 else PAGE_SLOTS"))
        val pages = src("WinPagesCase.kt")
        // the heading names the page after the old one has gone (rememberHeadingPage)
        assertTrue(pages.contains("CasePageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, accent)"))
        // only Performance falls back to the shared page
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
    }

    @Test fun pageNamesMatchTheButtons() {
        assertEquals(listOf("Case", "Details", "Extras", "Setup"), NATIVE_TAB_ORDER.map(::casePageName))
        // the case's Details button is called Details (WinLook CASEFLOW)
        val look = src("GameWindow.kt").let { it.substring(it.indexOf("HomeLayout.CASEFLOW -> WinLook(")).substringBefore("\n        )") }
        assertTrue(look.contains("details = \"Details\""))
    }

    @Test fun extrasWords() {
        assertEquals("3 of 12 unlocked", caseExtrasLine(3, 12)); assertEquals("0 of 0 unlocked", caseExtrasLine(0, 0))
        assertEquals("12 of 12 unlocked", caseExtrasLine(15, 12))
        assertEquals("Unlocked 12 Mar 2024", caseEntryLine(true, "12 Mar 2024"))
        assertEquals("Unlocked", caseEntryLine(true, null)); assertEquals("Unlocked", caseEntryLine(true, " "))
        assertEquals("Locked", caseEntryLine(false, "12 Mar 2024"))
        assertEquals(0f, caseExtrasShare(0, 0), 0f); assertEquals(0.25f, caseExtrasShare(3, 12), 1e-6f)
        assertEquals(1f, caseExtrasShare(20, 12), 0f)
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesCase.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
