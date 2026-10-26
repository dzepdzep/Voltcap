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

    private val own = listOf(HomeLayout.MEMCARD, HomeLayout.CASEFLOW, HomeLayout.STORAGE, HomeLayout.FILESELECT)
    private val all = own + HomeLayout.BOX + HomeLayout.ORBIT + HomeLayout.AIRY + HomeLayout.NIGHT

    @Test fun newLayoutsArePickableStyleHomes() {
        for (l in all) {
            assertTrue(l in HomeLayout.pickable)
            assertTrue(l.styleHome)
            assertTrue("has presets", LayoutPresets.forLayout(l).isNotEmpty())
        }
        for (l in all) assertNotNull("draws its games its own way", layoutOwnLook(l))
        for (l in listOf(HomeLayout.BOX, HomeLayout.STORAGE, HomeLayout.FILESELECT, HomeLayout.ORBIT, HomeLayout.AIRY, HomeLayout.NIGHT)) assertTrue(l in CategoryBars.OWN)
        // Memory card shows the category as its card's name.
        assertTrue(HomeLayout.MEMCARD in CategoryBars.OWN)
    }

    /** Tiles that would change nothing there are hidden: the look, game size, badges, the achievement chip. */
    @Test fun r1TilesThatDoNothingAreHidden() {
        for (l in all) assertEquals("gamesize on $l", R1Fit.Hide, r1TileFit("gamesize", ctx(l)))
        for (l in all) {
            for (id in listOf("gamelook", "badges", "progressbar")) {
                assertEquals("$id on $l", R1Fit.Hide, r1TileFit(id, ctx(l)))
            }
            assertEquals(R1Fit.Show, r1TileFit("endless", ctx(l)))
        }
        // Case flow's cases take the case picked for every game.
        assertEquals(R1Fit.Show, r1TileFit("casestyle", ctx(HomeLayout.CASEFLOW)))
    }

    /** Phase 2: the layouts with a window of their own (WinScenes.kt); the rest keep the standard one. */
    @Test fun layoutsWithTheirOwnWindow() {
        assertEquals(WinScene.BOX_SHEET, winSceneFor(HomeLayout.BOX))
        assertEquals(WinScene.FILE_CARD, winSceneFor(HomeLayout.FILESELECT))
        assertEquals(WinScene.STORAGE_RECORD, winSceneFor(HomeLayout.STORAGE))
        assertEquals(WinScene.MEMCARD_INFO, winSceneFor(HomeLayout.MEMCARD))
        assertEquals(WinScene.CASE_MEDIA, winSceneFor(HomeLayout.CASEFLOW))
        assertEquals(WinScene.ORBIT_TERMINAL, winSceneFor(HomeLayout.ORBIT))
        assertEquals(WinScene.AIRY_PANEL, winSceneFor(HomeLayout.AIRY))
        assertEquals(WinScene.NIGHT_STATUS, winSceneFor(HomeLayout.NIGHT))
        assertEquals(null, winSceneFor(HomeLayout.GRID))
    }
}
