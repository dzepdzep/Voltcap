package com.shelf.launcher.data

import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.SpecialFilter
import com.shelf.launcher.ui.TitleOptions
import com.shelf.launcher.ui.buildCategoryTiles
import com.shelf.launcher.ui.styleStops
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 367: every way of moving between categories sees the same list — L2 / R2 (and the warm-up, the tabs' jump) through
 * [CategoryStops], the style homes' tabs ([styleStops]) and the category screen's tiles ([buildCategoryTiles] minus the tiles
 * taken off) — in the same order, with the same identities, honouring Settings › Categories and Add category.
 */
class CategoryStopsTest {
    private fun game(name: String, platform: String) =
        Game(name, platform, 0, SourceType.ROM, romPath = "/roms/$platform/$name.rom")

    private val games = listOf(
        game("A", "SNES"), game("B", "SNES"), game("C", "N64"), game("D", "PSX"), game("E", "GBA"), game("F", "Genesis")
    )

    private fun state(
        hidden: Set<String> = CategorySettings.DEFAULT_HIDDEN,
        order: List<String> = CategorySettings.DEFAULT_ORDER
    ) = LauncherUiState(games = games, categories = CategorySettings(order = order, hidden = hidden))

    @After fun reset() {
        CategoryTileOrder.reset()
        listOf("ALL", "RECENT", "p:N64", "p:SNES", "p:PSX").forEach { CategoryTileOrder.setHidden(it, false) }
    }

    /** What the category screen shows: its tiles minus the ones taken off (LauncherScreen filters them the same way). */
    private fun screenKeys(s: LauncherUiState) =
        buildCategoryTiles(s, fullNames = false, categoryImages = emptyMap()).map { it.key }.filterNot { CategoryTileOrder.isHidden(it) }

    private fun tabKeys(s: LauncherUiState) = styleStops(s, TitleOptions()).first.map { CategoryStops.keyOf(it.special, it.platform) }

    private fun assertAllAgree(s: LauncherUiState) {
        val stops = CategoryStops.keys(s)
        assertTrue("some categories", stops.isNotEmpty())
        assertEquals("tabs = L2/R2 stops", stops, tabKeys(s))
        // Tiles of categories with games: the same ones, in the same order (an empty special is still a tile on the screen).
        val screen = screenKeys(s)
        assertEquals("category screen = L2/R2 stops", stops, screen)
    }

    @Test fun defaultStateAgrees() {
        val s = state()
        assertAllAgree(s)
        assertEquals(listOf("ALL", "RECENT", "p:GBA", "p:Genesis", "p:N64", "p:PSX", "p:SNES").sorted(), CategoryStops.keys(s).sorted())
    }

    @Test fun tileTakenOffIsOffEverywhere() {
        CategoryTileOrder.setHidden("p:N64", true)
        CategoryTileOrder.setHidden("RECENT", true)
        val s = state()
        assertAllAgree(s)
        assertFalse("p:N64" in CategoryStops.keys(s)); assertFalse("p:N64" in tabKeys(s)); assertFalse("p:N64" in screenKeys(s))
        assertFalse("RECENT" in CategoryStops.keys(s)); assertFalse("RECENT" in tabKeys(s))
    }

    @Test fun settingsHiddenGroupsStayHidden() {
        val s = state(hidden = CategorySettings.DEFAULT_HIDDEN + CategorySettings.CONSOLES)
        assertAllAgree(s)
        assertTrue(CategoryStops.keys(s).none { it.startsWith("p:") })
    }

    @Test fun switchedOnSpecialAppearsEverywhere() {
        val s = state(hidden = CategorySettings.DEFAULT_HIDDEN - "FAVORITES")
        assertAllAgree(s)
        assertTrue("FAVORITES" in CategoryStops.keys(s))
    }

    @Test fun yourTileOrderIsTheSameEverywhere() {
        val s0 = state()
        val now = CategoryStops.keys(s0)
        CategoryTileOrder.move(now, now.size - 1, 0) // last one to the front
        val s = state()
        assertAllAgree(s)
        assertEquals(now.last(), CategoryStops.keys(s).first())
    }

    @Test fun platformOrderIsTheSameEverywhere() {
        PlatformOrder.move(listOf("GBA", "Genesis", "N64", "PSX", "SNES"), 4, 0)
        try { assertAllAgree(state()) } finally { PlatformOrder.reset() }
    }

    @Test fun onlyAllLeftStillAgrees() {
        val s = state(hidden = CategorySettings.DEFAULT_HIDDEN + CategorySettings.CONSOLES + "RECENT")
        assertEquals(listOf("ALL"), CategoryStops.keys(s))
        assertAllAgree(s)
    }

    @Test fun stepFromHandlesOutsideTheList() {
        assertEquals(0, CategoryStops.stepFrom(-1, 1, 5))   // R2 from a category outside the list: the first, not the second
        assertEquals(4, CategoryStops.stepFrom(-1, -1, 5))  // L2: the last
        assertEquals(0, CategoryStops.stepFrom(4, 1, 5))
        assertEquals(4, CategoryStops.stepFrom(0, -1, 5))
        assertEquals(2, CategoryStops.stepFrom(1, 1, 5))
        assertEquals(-1, CategoryStops.stepFrom(0, 1, 0))
    }

    @Test fun keyIdentity() {
        assertEquals("ALL", CategoryStops.keyOf(null, null))
        assertEquals("RECENT", CategoryStops.keyOf(SpecialFilter.RECENT, null))
        assertEquals("p:SNES", CategoryStops.keyOf(null, "SNES"))
    }
}
