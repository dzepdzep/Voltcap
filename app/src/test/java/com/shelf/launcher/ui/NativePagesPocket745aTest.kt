package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp
import java.io.File

/** P2 745a (class C): the Pocket family (POCKET_LCD, three faces) has its own pages - each face its own, not the shared ones. */
class NativePagesPocket745aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    private fun face(name: String): String { val f = src("WinScenesStyled.kt"); val a = f.indexOf("private fun $name("); return f.substring(a, f.indexOf("\n}\n", a)) }

    @Test fun allThreeFacesHaveTheirOwnPages() {
        for (l in listOf(HomeLayout.POCKET_CLASSIC, HomeLayout.POCKET_RIBBON, HomeLayout.POCKET)) assertEquals(WinScene.POCKET_LCD, winSceneFor(l))
        assertTrue(nativePages(WinScene.POCKET_LCD))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.POCKET_LCD, p))
        val faces = mapOf("PocketClassicMenu" to "PocketFace.CLASSIC", "PocketRibbonSheet" to "PocketFace.RIBBON", "PocketQuestPanel" to "PocketFace.QUEST")
        for ((name, id) in faces) {
            val c = face(name)
            assertTrue(name, c.contains(".then(pocketPageBox(ctx, place)).nativeBumpers(ctx)"))
            assertTrue(name, c.contains("PocketPageArea("))
            assertFalse("$name: no shared page body", c.contains("ctx.pageBody("))
            // Each face draws with its own ink.
            assertTrue(name, c.contains(id) || (id == "PocketFace.QUEST" && c.contains("PocketInk(PocketFace.QUEST")))
        }
        // Pocket classic names the page in its title bar.
        assertTrue(face("PocketClassicMenu").contains("ClassicPageBar(ctx, blue, onColour(blue), font)"))
        // On its pages the keys keep the page slots (Play first, put).
        val styled = src("WinScenesStyled.kt")
        assertTrue(styled.contains("KeyRow(sceneKeys(ctx), 6.dp, slots = if (ctx.page == WinPage.MAIN) 0 else PAGE_SLOTS)"))
        assertTrue(face("PocketQuestPanel").contains("twoLineOnlyWhenAlone = true, slots = PAGE_SLOTS"))
        val pages = src("WinPagesPocket.kt")
        // The window grows upward only on its pages (More closed measured), so Play stays where the first page had it.
        assertTrue(pages.contains("pageH != null -> Modifier.height(pageH).offset(y = -(pageH - mainH) / 2)"))
        assertTrue(pages.contains("onSizeChanged { if (moreShut) place.mainH"))
        // Where the art beside the facts is taller (Pocket ribbon, Pocket Quest): the room under the first page's keys is kept.
        for (name in listOf("PocketRibbonSheet", "PocketQuestPanel")) {
            val c = face(name)
            assertTrue(name, c.contains("Modifier.pocketKeys(ctx, place)")); assertTrue(name, c.contains("PocketKeyGap(ctx, place, ")); assertTrue(name, c.contains(".pocketPanel(place)"))
        }
        assertEquals(1, pages.split("ctx.pageFor(").size - 1)
        assertTrue(pages.contains("confirm.press(i, o.destructive)"))
        assertTrue(pages.contains("move = { d -> pocketMove(k.face, d) }"))
    }

    @Test fun eachFaceHasItsOwnWords() {
        assertEquals(listOf("GAME", "DATA", "MEDALS", "MENU"), NATIVE_TAB_ORDER.map { pocketPageName(PocketFace.CLASSIC, it) })
        assertEquals(listOf("Overview", "Details", "Records", "Settings"), NATIVE_TAB_ORDER.map { pocketPageName(PocketFace.RIBBON, it) })
        assertEquals(listOf("Overview", "Status", "Quest log", "Commands"), NATIVE_TAB_ORDER.map { pocketPageName(PocketFace.QUEST, it) })
        for (f in PocketFace.values()) assertEquals("$f", WinPage.values().size, WinPage.values().map { pocketPageName(f, it) }.toSet().size)
        assertEquals("‹ DATA ›" to "2/4", classicPageHeader(NATIVE_TAB_ORDER, WinPage.DETAILS))
        assertEquals("‹ MENU ›" to "3/3", classicPageHeader(listOf(WinPage.MAIN, WinPage.DETAILS, WinPage.OPTIONS), WinPage.OPTIONS))
        assertEquals("GOT 2026-11-01", pocketAwardLine(PocketFace.CLASSIC, true, " 2026-11-01 "))
        assertEquals("NOT YET", pocketAwardLine(PocketFace.CLASSIC, false, "2026-11-01"))
        assertEquals("Earned", pocketAwardLine(PocketFace.RIBBON, true, " "))
        assertEquals("Not earned yet", pocketAwardLine(PocketFace.RIBBON, false, null))
        assertEquals("CLEARED", pocketAwardLine(PocketFace.QUEST, true, null))
        assertEquals("OPEN", pocketAwardLine(PocketFace.QUEST, false, null))
    }

    @Test fun countsAndBlocks() {
        assertEquals("3/12", pocketCount(3, 12, spaced = false)); assertEquals("12 / 12", pocketCount(15, 12, spaced = true))
        assertEquals("0/0", pocketCount(-2, -1, spaced = false))
        assertEquals("3 of 12 earned", ribbonRecordLine(3, 12)); assertEquals("0 of 0 earned", ribbonRecordLine(4, 0))
        assertEquals(0, pocketBlocks(0, 12)); assertEquals(0, pocketBlocks(3, 0))
        assertEquals(1, pocketBlocks(1, 200)) // one earned always shows
        assertEquals(9, pocketBlocks(199, 200)) // full only when all are earned
        assertEquals(10, pocketBlocks(12, 12)); assertEquals(5, pocketBlocks(6, 12))
    }

    @Test fun keyGapKeepsTheFirstPagesRoom() {
        assertEquals(30f, pocketExtraGap(40.dp, 10.dp).value, 1e-4f)
        assertEquals(0f, pocketExtraGap(10.dp, 14.dp).value, 1e-4f)
        assertEquals(0f, pocketExtraGap(14.dp, 14.dp).value, 1e-4f)
    }

    @Test fun lcdStepsAreHardSteps() {
        val e = lcdSteps(4)
        assertEquals(0f, e.transform(0.2f), 1e-6f); assertEquals(0.25f, e.transform(0.3f), 1e-6f)
        assertEquals(0.75f, e.transform(0.99f), 1e-6f); assertEquals(1f, e.transform(1f), 1e-6f)
    }

    @Test fun noTruncationNoIntrinsics() {
        val c = src("WinPagesPocket.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        assertFalse(c.contains("IntrinsicSize"))
    }
}
