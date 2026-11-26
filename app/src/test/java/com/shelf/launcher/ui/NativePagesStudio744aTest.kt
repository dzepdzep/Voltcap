package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 744a (class C): Layout Studio has its own pages (Properties / Milestones / Actions under the editor's tab strip) - not the shared ones. */
class NativePagesStudio744aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun panel(): String { val f = src("WinSceneStudio.kt"); val a = f.indexOf("internal fun StudioPanel("); return f.substring(a, f.indexOf("\n}\n", a)) }

    @Test fun theStudioHasItsOwnPages() {
        assertEquals(WinScene.STUDIO_PANEL, winSceneFor(HomeLayout.CUSTOM))
        assertTrue(nativePages(WinScene.STUDIO_PANEL))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.STUDIO_PANEL, p))
        val c = panel()
        assertTrue(c.contains("StudioPageArea(ctx, rememberStudioInk(a), Modifier.fillMaxWidth().weight(1f), short)"))
        assertFalse("no shared page body on the panel", c.contains("ctx.pageBody("))
        assertTrue(c.contains(".nativeBumpers(ctx)"))
        // Play first with the first page's slots on every page, so it stays put
        assertTrue(c.contains("KeyRow(sceneKeys(ctx), 8.dp, slots = sceneKeySlots(ctx))"))
        // and the panel grows upward only on its pages (More closed measured), so Play stays where the first page had it
        assertTrue(c.contains("pageH != null -> Modifier.height(pageH).offset(y = -(pageH - mainH.value!!) / 2)"))
        assertTrue(c.contains("onSizeChanged { if (moreShut) mainH.value"))
        val pages = src("WinPagesStudio.kt")
        assertTrue(pages.contains("StudioTabs(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, k, short)"))
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
        assertTrue(pages.contains("move = ::studioMove"))
    }

    @Test fun pageNamesAreTheEditorsWords() {
        assertEquals(listOf("Canvas", "Properties", "Milestones", "Actions"), NATIVE_TAB_ORDER.map(::studioPageName))
        assertEquals("Performance", studioPageName(WinPage.PERF))
        assertEquals(WinPage.values().size, WinPage.values().map(::studioPageName).toSet().size)
    }

    @Test fun milestoneWordsAndRuler() {
        assertEquals("3 / 12", studioAwardCount(3, 12)); assertEquals("12 / 12", studioAwardCount(15, 12))
        assertEquals("0 / 0", studioAwardCount(2, 0)); assertEquals("0 / 0", studioAwardCount(-1, -3))
        assertEquals("Earned 2026-11-01", studioAwardLine(true, " 2026-11-01 "))
        assertEquals("Earned", studioAwardLine(true, null)); assertEquals("Earned", studioAwardLine(true, " "))
        assertEquals("Not yet", studioAwardLine(false, "2026-11-01"))
        val ticks = studioRulerTicks()
        assertEquals(9, ticks.size); assertEquals(0.1f, ticks.first(), 1e-4f); assertEquals(0.9f, ticks.last(), 1e-4f)
        assertTrue(ticks.zipWithNext().all { (x, y) -> y > x })
    }

    @Test fun noTruncationNoIntrinsics() {
        val c = src("WinPagesStudio.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
