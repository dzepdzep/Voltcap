package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 batch 2 (owner 18 Nov 14:54, GO): each layout's own Details / Achievements / Options pages, not the shared bodies. */
class NativePagesBatch2Test {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()
    private fun between(s: String, a: String, b: String) = s.substring(s.indexOf(a), s.indexOf(b, s.indexOf(a)))

    @Test fun magazinePrintsItsOwnPages() {
        assertTrue(nativePages(WinScene.MAG_PAGE))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.MAG_PAGE, p))
        assertEquals("THE REVIEW", magPageName(WinPage.DETAILS)); assertEquals("CHECKLIST", magPageName(WinPage.TROPHIES))
        assertEquals("READER SERVICES", magPageName(WinPage.OPTIONS)); assertEquals("FEATURE", magPageName(WinPage.MAIN))
        assertEquals("3 / 12" to "40 OF 150 PTS", magScore(3, 12, 40, 150)); assertEquals("0 / 0" to "NO POINTS", magScore(0, 0, 0, 0))
        val scene = between(src("WinScenesConsole.kt"), "internal fun MagazinePage(", "// Rental shop: the rental tag")
        assertFalse(scene.contains("ctx.pageBody("))
        assertTrue(scene.contains("MagPage(ctx, st, pg, pageLive,") && scene.contains("it.inert()"))
        assertEquals(1, src("WinPagesMag.kt").split("ctx.pageFor(").size - 1) // only Performance falls back
        assertTrue(src("WinPagesMag.kt").contains("rememberNativeConfirm(ctx)"))
    }

    @Test fun cinemaShowsTheServicesOwnScreens() {
        assertTrue(nativePages(WinScene.CINEMA_PREMIERE))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.CINEMA_PREMIERE, p))
        assertEquals("MORE INFO", cinemaPageName(WinPage.DETAILS)); assertEquals("EPISODES", cinemaPageName(WinPage.TROPHIES))
        assertEquals("MY LIST & MORE", cinemaPageName(WinPage.OPTIONS))
        assertEquals("1", cinemaEpisodeNo(0)); assertEquals("10 pts", cinemaRuntime(10)); assertEquals("-", cinemaRuntime(null))
        val scene = between(src("WinScenesHomes.kt"), "internal fun CinemaPremiere(", "// Arctic info drawer")
        assertFalse(scene.contains("ctx.pageBody("))
        assertTrue(scene.contains("CinemaPage(ctx, pg, pageLive, accent,"))
        assertEquals(1, src("WinPagesCinema.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun arcadeShowsTheCabinetsOwnScreens() {
        assertTrue(nativePages(WinScene.ARCADE_MENU))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.ARCADE_MENU, p))
        assertEquals("INSTRUCTIONS", arcadeNativePageName(WinPage.DETAILS)); assertEquals("HIGH SCORES", arcadeNativePageName(WinPage.TROPHIES))
        assertEquals("SERVICE MODE", arcadeNativePageName(WinPage.OPTIONS))
        assertEquals(listOf("1ST", "2ND", "3RD", "4TH", "11TH", "12TH", "13TH", "21ST", "22ND"), listOf(0, 1, 2, 3, 10, 11, 12, 20, 21).map(::arcadeRank))
        assertEquals("000050", arcadeScore(50, true)); assertEquals("-----", arcadeScore(50, false)); assertEquals("000000", arcadeScore(null, true))
        val scene = between(src("WinScenesArcade.kt"), "internal fun ArcadeMenu(", "private fun ArcadeMenuRow(")
        assertFalse(scene.contains("ctx.pageBody("))
        assertTrue(scene.contains("ArcadePage(ctx, pg, pageLive, font, line, small,"))
        assertEquals(1, src("WinPagesArcade.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun cubeTurnsToItsOtherFaces() {
        assertTrue(nativePages(WinScene.CUBE_FACE))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.CUBE_FACE, p))
        assertEquals("FACE 2 · SPECS", cubePageName(WinPage.DETAILS)); assertEquals("FACE 3 · CUBES", cubePageName(WinPage.TROPHIES))
        assertEquals("FACE 4 · CONTROLS", cubePageName(WinPage.OPTIONS))
        assertEquals(5, cubeWallColumns(82f)); assertEquals(1, cubeWallColumns(5f))
        val scene = between(src("WinSceneFamilies.kt"), "internal fun CubeTurnFace(", "// ───────────────────────────── Console hub")
        assertFalse(scene.contains("ctx.pageBody("))
        assertTrue(scene.contains("CubePage(ctx, pg, pageLive, a, faceBottom,"))
        assertEquals(1, src("WinPagesCube.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun spotlightPrintsThePostersOwnParts() {
        // (682t) THE PLOT / THE CREDITS / BACKSTAGE; the pool of light moves onto the page; Play's row is the same on every page.
        assertTrue(nativePages(WinScene.SPOT_POSTER))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.SPOT_POSTER, p))
        assertEquals("THE PLOT", spotPageName(WinPage.DETAILS)); assertEquals("THE CREDITS", spotPageName(WinPage.TROPHIES))
        assertEquals("BACKSTAGE", spotPageName(WinPage.OPTIONS))
        assertEquals("★ 10", spotBilling(10)); assertEquals("★", spotBilling(null))
        assertEquals("MADE BY", spotCreditLabel("Developer")); assertEquals("GENRE", spotCreditLabel("Genres"))
        val scene = between(src("WinScenesHomes2.kt"), "internal fun SpotPoster(", "// ───────────── Icon bar")
        assertFalse(scene.contains("ctx.pageBody("))
        assertTrue(scene.contains("SpotPage(ctx, pg, pageLive, lit,"))
        // one key row for every page: the same KeyRow call, not one per page
        assertEquals(1, scene.split("KeyRow(keys, 8.dp, slots = rememberKeySlots(ctx.page,").size - 1)
        assertEquals(1, src("WinPagesSpot.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun badgeFallbackMark() {
        // (682t) a badge that never loads leaves the achievement's own mark, never an empty black square
        assertEquals("Q", nativeBadgeMark("QA achievement 1")); assertEquals("1", nativeBadgeMark("  100 points"))
        assertEquals("★", nativeBadgeMark(null)); assertEquals("★", nativeBadgeMark("  ·  "))
    }

    @Test fun pageKeysKeepTheMainPagesSlots() {
        // (682t, owner 18 Nov: Play the same size in the same place on every page) 4 slots in 400 px, 8 px apart: 94 px each
        val g = uniformSlotGeom(2, 4, 90, 400, 8)!!
        assertEquals(94, g.keyW); assertEquals(0, g.viewStart); assertEquals(0, g.firstW)
        // a page key wider than a slot: the usual row where the main row had scrolled (Play would be cut) ...
        assertEquals(null, uniformSlotGeom(2, 4, 150, 400, 8))
        // ... and Play keeps its slot where the main row showed every key whole (682x)
        val w = uniformSlotGeom(2, 4, 150, 400, 8, mainFit = true)!!
        assertEquals(94, w.firstW); assertEquals(400 - 94 - 8, w.keyW)
        assertEquals(null, uniformSlotGeom(4, 4, 90, 400, 8))    // the main page itself: the usual row
        assertEquals(null, uniformSlotGeom(2, 0, 90, 400, 8))    // no main page seen
        assertEquals(null, uniformSlotGeom(3, 4, 200, 400, 8))   // nothing fits whole: the usual (scrolling) row
    }

    @Test fun batch3PagesAreTheirOwn() {
        // (682w) Terminal, Dashboard, Library, Card binder: their own pages, no shared page body left in their scenes
        for (sc in listOf(WinScene.TERM_RUN, WinScene.DASH_CARD, WinScene.LIBRARY_PAGE, WinScene.CARD_BACK, WinScene.SIDE_DRAWER, WinScene.ARCTIC_DRAWER, WinScene.DOCK_FLYOUT, WinScene.STORAGE_RECORD)) {
            assertTrue("$sc", nativePages(sc))
            for (p in WinPage.values()) assertFalse("$sc $p", sharedPageFor(sc, p))
        }
        assertEquals("DIR ACHIEVE\\ /O:RAREST_FIRST", termCommand(WinPage.TROPHIES, "TETRIS", "rarest first"))
        assertEquals("TYPE TETRIS.TXT", termCommand(WinPage.DETAILS, "TETRIS"))
        assertEquals("[X] FIRST BLOOD", termDirLine(true, "First blood")); assertEquals("[ ] UNTITLED", termDirLine(false, null))
        assertEquals(" 10 PTS", termPoints(10)); assertEquals("  - PTS", termPoints(null))
        assertEquals(50, dashPercent(5, 10)); assertEquals(0, dashPercent(3, 0))
        assertEquals("ABOUT THIS GAME", libraryTab(WinPage.DETAILS)); assertEquals(0.5f, libraryRate(50f), 0.001f); assertEquals(1f, libraryRate(140f), 0.001f)
        assertEquals("MOVES", cardSection(WinPage.TROPHIES)); assertEquals("—", cardMoveValue(null))
        assertFalse(src("WinScenesConsole.kt").let { it.substring(it.indexOf("internal fun TerminalDialog("), minOf(it.length, it.indexOf("internal fun TerminalDialog(") + 9000)) }.contains("ctx.pageBody("))
        for ((f, fn) in listOf("WinScenesHomes2.kt" to "internal fun DashCard(", "WinScenesHomes2.kt" to "internal fun LibraryPage(", "WinSceneFamilies.kt" to "internal fun CardTurnOver(")) {
            val s = src(f); val i = s.indexOf(fn); val body = s.substring(i, s.indexOf("\n}\n", i))
            assertFalse(fn, body.contains("ctx.pageBody("))
            assertTrue(fn, body.contains("slots = sceneKeySlots(ctx)"))
        }
    }

    // 682y: Play keeps its slot on a page with Play alone (Card binder Options) where the main page showed Play alone.
    @Test fun pageRowsKeepTwoSlots() {
        assertEquals(1, pageKeySlots(WinPage.MAIN, 1))
        assertEquals(2, pageKeySlots(WinPage.OPTIONS, 1))
        assertEquals(3, pageKeySlots(WinPage.TROPHIES, 3))
        val g = uniformSlotGeom(1, pageKeySlots(WinPage.OPTIONS, 1), need = 120, width = 826, gap = 10)!!
        assertEquals(408, g.keyW); assertEquals(0, g.viewStart)
        // main page of six keys (scrolled): its slot is too narrow for Play - Play alone still takes the first half
        assertEquals(null, uniformSlotGeom(1, 6, need = 120, width = 440, gap = 6))
        val h = pageAloneGeom(1, 6, need = 120, width = 440, gap = 6)!!
        assertEquals(217, h.keyW); assertEquals(0, h.viewStart)
        assertEquals(null, pageAloneGeom(1, 1, need = 120, width = 440, gap = 6))
    }

    // 682y: "PlayStatio / n 2" on the Card binder's slim no-art plate - the console's name only where its longest word fits.
    @Test fun noArtPlateNameOnlyWhereItsWordFits() {
        assertFalse(noArtNameFits("PlayStation 2", 58f))
        assertTrue(noArtNameFits("PlayStation 2", 110f))
        assertTrue(noArtNameFits("Game Boy", 58f))
    }

    // D364: "ACHIEVEMENT / S" on Storage's Info sheet (RP Classic x1.3) - fewer cells a row rather than a broken word.
    @Test fun storageMenuCellsKeepTheLongestWordWhole() {
        // two cells of (300 - 3) / 2 = 148 px can't hold a 140 px word + 45 px chrome: one a row
        assertEquals(1, storageCellsPerRow(300f, 140f, 45f, 3f, 2))
        assertEquals(2, storageCellsPerRow(400f, 140f, 45f, 3f, 2))
        assertEquals(3, storageCellsPerRow(700f, 140f, 45f, 3f, 3))
        assertEquals(1, storageCellsPerRow(100f, 140f, 45f, 3f, 2))
    }

    // 682aa (P2 batch 5, audit NATIVE_PAGES_AUDIT.md): the shelf family's cards draw their own catalogue, checklist and entries -
    // no shared page body left in ShelfFamilyPages for Details / Achievements / Options.
    @Test fun shelfFamilyCardsAreTheirOwn() {
        val s = src("WinSceneShelfPages.kt"); val i = s.indexOf("internal fun ShelfFamilyPages("); val body = s.substring(i, s.indexOf("\n}\n", i))
        assertFalse(body.contains("ctx.pageFor("))
        assertTrue(body.contains("ShelfCardPage("))
        val pages = src("WinPagesShelf.kt")
        for (fn in listOf("ShelfCatalogue(", "ShelfChecklist(", "ShelfEntries(")) assertTrue(fn, pages.contains("private fun $fn"))
        assertEquals("12\u00A0PTS", shelfPoints(12)); assertEquals("0\u00A0PTS", shelfPoints(null))
        // the Twin family's touch screen: its own tiles, touch list and buttons
        val t = src("WinSceneTwin.kt"); val j = t.indexOf("internal fun TwinTouchPages("); val tb = t.substring(j, t.indexOf("\n}\n", j))
        assertFalse(tb.contains("ctx.pageFor(")); assertTrue(tb.contains("TwinPage(")); assertTrue(tb.contains("page = true"))
        for (fn in listOf("TwinFacts(", "TwinAchievements(", "TwinButtons(")) assertTrue(fn, src("WinPagesTwin.kt").contains("private fun $fn"))
    }
}
