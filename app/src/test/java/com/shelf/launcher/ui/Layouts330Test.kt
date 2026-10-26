package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.CategoryBars
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.LayoutPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 330: the new layouts (Memory card, Case flow) — in the picker, their R1 tiles, their presets. */
class Layouts330Test {

    private fun ctx(l: HomeLayout) = R1Ctx(
        AppearanceSettings(homeLayout = l), secondDisplay = true, bluetooth = true, lights = true, romm = false, raAccounts = 1,
        themeRecolorable = true, chargeLimitWorks = true
    )

    @Test fun newLayoutsArePickableStyleHomes() {
        for (l in listOf(HomeLayout.MEMCARD, HomeLayout.CASEFLOW)) {
            assertTrue(l in HomeLayout.pickable)
            assertTrue(l.styleHome)
            assertNotNull("draws its games its own way", layoutOwnLook(l))
            assertTrue("has presets", LayoutPresets.forLayout(l).isNotEmpty())
        }
        // Memory card shows the category as its card's name.
        assertTrue(HomeLayout.MEMCARD in CategoryBars.OWN)
    }

    /** Tiles that would change nothing there are hidden: the look, game size, badges, the achievement chip. */
    @Test fun r1TilesThatDoNothingAreHidden() {
        for (l in listOf(HomeLayout.MEMCARD, HomeLayout.CASEFLOW)) {
            for (id in listOf("gamelook", "gamesize", "badges", "progressbar")) {
                assertEquals("$id on $l", R1Fit.Hide, r1TileFit(id, ctx(l)))
            }
            assertEquals(R1Fit.Show, r1TileFit("endless", ctx(l)))
        }
        // Case flow's cases take the case picked for every game.
        assertEquals(R1Fit.Show, r1TileFit("casestyle", ctx(HomeLayout.CASEFLOW)))
    }
}
