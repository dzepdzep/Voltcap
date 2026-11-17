package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 673f, native pages (owner directive 14 Nov: layout-aware pages for the remaining layouts): Disc station's pages are the
 * deck's tracks on its LCD - Info 01, Details 02, Achievements 03, Options 04; L1 / R1 skip a track.
 */
class DiscTracks673Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun eachPageIsATrackOfTheGamesOwnPages() {
        val all = DISC_TRACKS
        assertEquals("DISC 1 · TRACK 01", discTrackLabel(WinPage.MAIN, all))
        assertEquals("DISC 1 · TRACK 02", discTrackLabel(WinPage.DETAILS, all))
        assertEquals("DISC 1 · TRACK 04", discTrackLabel(WinPage.OPTIONS, all))
        // No achievements: Options is the third track; a page outside the walk (Performance) counts after them.
        val noTrophies = DISC_TRACKS.filter { it != WinPage.TROPHIES }
        assertEquals("DISC 1 · TRACK 03", discTrackLabel(WinPage.OPTIONS, noTrophies))
        assertEquals("DISC 1 · TRACK 04", discTrackLabel(WinPage.PERF, noTrophies))
    }

    @Test fun shouldersSkipTracksAndWrap() {
        val t = DISC_TRACKS
        assertEquals(WinPage.DETAILS, pageStep(t, WinPage.MAIN, 1))
        assertEquals(WinPage.OPTIONS, pageStep(t, WinPage.MAIN, -1))
        assertEquals(1, pageDirection(t, WinPage.DETAILS, WinPage.OPTIONS))
        assertEquals(-1, pageDirection(t, WinPage.OPTIONS, WinPage.MAIN))
    }

    @Test fun theDeckDrawsItsOwnPages() {
        assertTrue(nativePages(WinScene.DISC_PANEL))
        assertTrue(!sharedPageFor(WinScene.DISC_PANEL, WinPage.DETAILS))
        val s = src("WinScenesMore.kt")
        val i = s.indexOf("internal fun DiscPlayerPanel(")
        val body = s.substring(i, s.indexOf("internal fun discPanelGeometry", i))
        // the page plays on the LCD with the LCD as its paper; the leaving track draws its own page, inert
        assertTrue(body.contains("LocalNativePageHost provides NativePageHost(Color.Transparent, dark = true, showsCover = true, showsName = false)"))
        assertTrue(body.contains("ctx.pageFor(p, Modifier.fillMaxSize(), live)"))
        assertTrue(body.contains("CompositionLocalProvider(LocalShownPage provides p)"))
        assertTrue(body.contains("val to = pageStep(tracks, ctx.page, step)"))
    }
}
