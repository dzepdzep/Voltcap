package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 734b (class C): Orbit deck's console draws its own screens (DATA / TRACKS / COMMANDS) - not the shared page bodies. */
class NativePagesOrbit734bTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()

    private fun body(f: String, head: String): String { val s = f.indexOf(head); assertTrue(head, s >= 0); return f.substring(s, f.indexOf("\n}\n", s)) }

    @Test fun orbitHasTheConsolesOwnScreens() {
        assertEquals(WinScene.ORBIT_TERMINAL, winSceneFor(HomeLayout.ORBIT))
        assertTrue(nativePages(WinScene.ORBIT_TERMINAL))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.ORBIT_TERMINAL, p))
        val f = src("WinScenes.kt")
        val console = body(f, "private fun OdConsole(")
        assertTrue(console.contains("OrbitPageArea(ctx, glow, "))
        assertFalse("no shared page body left in the console", console.contains("ctx.pageBody("))
        // L1 / R1 on the window's root, wide (arc keys) and narrow (key row) alike
        assertTrue(body(f, "private fun OrbitTerminal(").contains("Box(Modifier.size(w, h).nativeBumpers(ctx))"))
        assertTrue(body(f, "private fun OrbitTerminalNarrow(").contains("Column(Modifier.size(w, h).nativeBumpers(ctx)"))
        val pages = src("WinPagesOrbit.kt")
        // the heading names the page after the old one has gone (rememberHeadingPage)
        assertTrue(pages.contains("OrbitPageHeading(nativeTabs(ctx.pages), if (heading == WinPage.MAIN) ctx.page else heading, glow, lamp)"))
        // only Performance falls back to the shared page
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        // a removing command asks first
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
    }

    @Test fun pageNamesAndTrackWords() {
        assertEquals(listOf("DISC", "DATA", "TRACKS", "COMMANDS"), NATIVE_TAB_ORDER.map(::orbitPageName))
        assertEquals("01", orbitTrackNo(0)); assertEquals("12", orbitTrackNo(11)); assertEquals("99", orbitTrackNo(98))
        assertEquals("100", orbitTrackNo(99))
        assertEquals("UNLOCKED 2026-11-01", orbitTrackLine(true, " 2026-11-01 "))
        assertEquals("UNLOCKED", orbitTrackLine(true, null)); assertEquals("UNLOCKED", orbitTrackLine(true, " "))
        assertEquals("LOCKED", orbitTrackLine(false, "2026-11-01"))
        assertEquals("3 / 12", orbitTrackCount(3, 12)); assertEquals("12 / 12", orbitTrackCount(15, 12))
        assertEquals("0 / 0", orbitTrackCount(2, 0)); assertEquals("0 / 0", orbitTrackCount(-1, -3))
    }

    @Test fun litCellsFollowTheMainConsole() {
        assertEquals(0, orbitLitCells(0, 0)); assertEquals(5, orbitLitCells(5, 12)); assertEquals(12, orbitLitCells(20, 12))
        // beyond 24 achievements the strip lights in proportion, as the DISC screen's track cells do
        assertEquals(12, orbitLitCells(60, 120)); assertEquals(24, orbitLitCells(120, 120)); assertEquals(0, orbitLitCells(0, 120))
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesOrbit.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
