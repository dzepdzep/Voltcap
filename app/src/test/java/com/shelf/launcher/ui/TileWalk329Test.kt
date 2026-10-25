package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.CategoryBar
import com.shelf.launcher.data.CategoryBars
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 329: what the R1 tile walk on the AYN Thor found, kept from coming back. */
class TileWalk329Test {

    private fun ctx(a: AppearanceSettings) = R1Ctx(
        a, secondDisplay = true, bluetooth = true, lights = true, romm = false, raAccounts = 1,
        themeRecolorable = true, chargeLimitWorks = true
    )

    private fun <T> withFeature(p: Features.Pref<T>, v: T, block: () -> Unit) {
        val before = p.value
        p.value = v
        try { block() } finally { p.value = before }
    }

    /** The category title showed the console's key ("PS2") — sortCategoryKey is "cat:" + the platform filter. */
    @Test fun categoryTitleUsesTheConsolesName() {
        withFeature(Features.shortConsoleNames, false) {
            assertEquals("PlayStation 2", categoryKeyLabel("cat:PS2"))
            assertEquals("Game Boy Advance", categoryKeyLabel("cat:GBA"))
            assertEquals("Backlog", categoryKeyLabel("cat:col:Backlog"))
            assertEquals("All games", categoryKeyLabel("all"))
        }
        withFeature(Features.shortConsoleNames, true) {
            assertEquals("PS2", categoryKeyLabel("cat:PS2"))
        }
    }

    /** Twin desk / showcase draw every category only with their Additional category bar on. */
    @Test fun twinCategoriesOfferAllOnlyWithTheirBar() {
        for (l in listOf(HomeLayout.TWIN_DESK, HomeLayout.TWIN_SHOWCASE)) {
            withFeature(Features.twinSharedCategories, false) {
                assertEquals(listOf(CategoryBar.CURRENT, CategoryBar.OFF), CategoryBars.choices(l))
            }
            withFeature(Features.twinSharedCategories, true) {
                assertEquals(listOf(CategoryBar.CURRENT, CategoryBar.FULL, CategoryBar.OFF), CategoryBars.choices(l))
            }
        }
        assertEquals(3, CategoryBars.choices(HomeLayout.CLASSIC).size)
    }

    /** The two-screen layouts put their own half on the second screen: the Magnifier page can't show there. */
    @Test fun magnifierGreyOnTheTwoScreenLayouts() {
        withFeature(Features.secondMagnifier, true) {
            for (l in HomeLayout.values()) {
                val f = r1TileFit("magnifier", ctx(AppearanceSettings(homeLayout = l, useSecondScreen = true)))
                if (l.isTwin) assertTrue("magnifier on $l", f is R1Fit.Grey) else assertEquals("magnifier on $l", R1Fit.Show, f)
            }
        }
    }

    /** Twin showcase's grid has no Endless (StyleTwinShowcase keeps it off). */
    @Test fun noEndlessOnTwinShowcase() {
        assertEquals(R1Fit.Hide, r1TileFit("endless", ctx(AppearanceSettings(homeLayout = HomeLayout.TWIN_SHOWCASE))))
        assertEquals(R1Fit.Show, r1TileFit("endless", ctx(AppearanceSettings(homeLayout = HomeLayout.TWIN_ICONS))))
    }
}
