package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.FocusPosition
import com.shelf.launcher.data.FocusedCardStyle
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.TileFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 723a (owner Task 2): R1's Focus position tile. It reuses the Voltcap row's FocusPosition and Tile home's TileFocus (no new
 * pref), steps round the choices, shows only on those two of the 53 layouts and is greyed while the carousel look or
 * "first one wide" keeps the game in one place. Fails on the old code (no tile, no rule: r1TileFit fell through to Show).
 */
class FocusPosTile723aTest {

    private val first53 = HomeLayout.values().take(HomeLayout.values().indexOf(HomeLayout.ARTLIB) + 1)

    private fun ctx(a: AppearanceSettings) = R1Ctx(
        a, secondDisplay = false, bluetooth = true, lights = false, romm = false, raAccounts = 1,
        themeRecolorable = true, chargeLimitWorks = true
    )

    private fun <T> withFeature(p: Features.Pref<T>, v: T, block: () -> Unit) {
        val before = p.value
        p.value = v
        try { block() } finally { p.value = before }
    }

    @Test fun voltcapRowStepsLeftMiddleAndRound() {
        val a = AppearanceSettings(homeLayout = HomeLayout.CLASSIC, focusPosition = FocusPosition.LEFT)
        assertEquals(listOf("Left", "Middle"), FocusPosTile.choices(a))
        assertEquals("Focus\nLeft", FocusPosTile.label(a))
        val b = FocusPosTile.next(a)
        assertEquals(FocusPosition.CENTER, b.focusPosition)
        assertEquals("Focus\nMiddle", FocusPosTile.label(b))
        assertEquals(FocusPosition.LEFT, FocusPosTile.next(b).focusPosition)
        // Only the focus setting changes (Tile home's own choice stays).
        assertEquals(a.copy(focusPosition = FocusPosition.CENTER), b)
    }

    @Test fun tileHomeStepsAllFourAndPicksDirectly() {
        var a = AppearanceSettings(homeLayout = HomeLayout.TILES, tileFocus = TileFocus.EDGES)
        assertEquals(TileFocus.values().map { it.label }, FocusPosTile.choices(a))
        assertEquals("Focus\nEdges", FocusPosTile.label(a))
        val seen = mutableListOf<TileFocus>()
        repeat(4) { a = FocusPosTile.next(a); seen += a.tileFocus }
        assertEquals(listOf(TileFocus.LEFT, TileFocus.CENTER, TileFocus.RIGHT, TileFocus.EDGES), seen)
        val picked = FocusPosTile.pick(a, TileFocus.RIGHT.ordinal)
        assertEquals(TileFocus.RIGHT, picked.tileFocus)
        assertEquals(a.focusPosition, picked.focusPosition)
        assertEquals(picked, FocusPosTile.pick(picked, 99))
    }

    @Test fun shownOnlyOnTheTwoLayoutsWithAFocusAnchor() {
        assertEquals(53, first53.size)
        withFeature(Features.carouselLayouts, emptySet()) {
            val shown = first53.filter { l ->
                r1TileFit(FocusPosTile.ID, ctx(AppearanceSettings(homeLayout = l))) != R1Fit.Hide
            }
            assertEquals(listOf(HomeLayout.CLASSIC, HomeLayout.TILES), shown)
            assertTrue(r1TileFit(FocusPosTile.ID, ctx(AppearanceSettings(homeLayout = HomeLayout.CLASSIC))) == R1Fit.Show)
            assertTrue(r1TileFit(FocusPosTile.ID, ctx(AppearanceSettings(homeLayout = HomeLayout.TILES))) == R1Fit.Show)
            // Another layout: the tile does nothing, and a press there would change nothing either.
            val hero = AppearanceSettings(homeLayout = HomeLayout.HERO)
            assertEquals(hero, FocusPosTile.next(hero))
        }
        assertTrue(FocusPosTile.ID in R1_RULED_TILES && FocusPosTile.ID in R1_APPENDED && FocusPosTile.ID in TileCheck.HOME_TILES)
    }

    @Test fun greyedWhileTheGameCannotMove() {
        withFeature(Features.carouselLayouts, setOf(HomeLayout.CLASSIC.name, HomeLayout.TILES.name)) {
            for (l in listOf(HomeLayout.CLASSIC, HomeLayout.TILES))
                assertTrue(l.name, r1TileFit(FocusPosTile.ID, ctx(AppearanceSettings(homeLayout = l))) is R1Fit.Grey)
        }
        withFeature(Features.carouselLayouts, emptySet()) {
            withFeature(Features.squareTiles, false) {
                val pinned = AppearanceSettings(homeLayout = HomeLayout.CLASSIC, focusedCardStyle = FocusedCardStyle.FIRST_ONLY)
                assertTrue(pinnedFirstOn(pinned))
                assertTrue(r1TileFit(FocusPosTile.ID, ctx(pinned)) is R1Fit.Grey)
            }
        }
    }
}
