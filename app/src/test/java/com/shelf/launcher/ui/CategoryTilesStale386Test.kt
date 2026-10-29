package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D80 (update 386): the second screen's Wall showed "All games · 0 Games" because the category tiles were built once, before
 * the library had loaded, and were not worked out again while the category screen stayed closed.
 */
class CategoryTilesStale386Test {
    private fun all(count: Int) = CategoryTile("ALL", "All games", "", emptyList(), 210, count = count)

    @Test fun tilesBuiltBeforeTheLibraryLoadedAreStale() {
        assertTrue(categoryTilesStale(listOf(all(0)), 2213))
    }

    @Test fun tilesThatMatchTheLibraryAreNotStale() {
        assertFalse(categoryTilesStale(listOf(all(2213)), 2213))
    }

    @Test fun countChangeAfterAScanIsStale() {
        assertTrue(categoryTilesStale(listOf(all(2157)), 2213))
    }

    @Test fun noTilesOrNoAllTileIsLeftToTheOtherRules() {
        assertFalse(categoryTilesStale(emptyList(), 10))
        assertFalse(categoryTilesStale(listOf(CategoryTile("p:gb", "Game Boy", "", emptyList(), 210, count = 3)), 10))
    }
}
