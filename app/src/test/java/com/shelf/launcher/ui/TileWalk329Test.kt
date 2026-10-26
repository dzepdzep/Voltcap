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
        // Twin icons has no name of its own: with its Category name part taken off in Edit Home the tile is greyed
        // (saying how to bring it back) until the Additional category bar is on.
        withFeature(Features.hiddenParts, setOf("TWIN_ICONS:category")) {
            withFeature(Features.twinSharedCategories, false) {
                assertTrue(r1TileFit("categories", ctx(AppearanceSettings(homeLayout = HomeLayout.TWIN_ICONS))) is R1Fit.Grey)
            }
            withFeature(Features.twinSharedCategories, true) {
                assertEquals(R1Fit.Show, r1TileFit("categories", ctx(AppearanceSettings(homeLayout = HomeLayout.TWIN_ICONS))))
            }
        }
        withFeature(Features.hiddenParts, emptySet<String>()) {
            assertEquals(R1Fit.Show, r1TileFit("categories", ctx(AppearanceSettings(homeLayout = HomeLayout.TWIN_ICONS))))
        }
    }

    /** The music player taken off in Edit Home can only sit in the status bar. */
    @Test fun musicPlayerTakenOffNeedsTheStatusBar() {
        withFeature(Features.hiddenParts, setOf("music")) {
            // Hero tiles has no Voltcap status bar by default.
            assertTrue(r1TileFit("musicspot", ctx(AppearanceSettings(homeLayout = HomeLayout.HERO_TILES))) is R1Fit.Grey)
            assertEquals(R1Fit.Show, r1TileFit("musicspot", ctx(AppearanceSettings(homeLayout = HomeLayout.CLASSIC))))
        }
        withFeature(Features.hiddenParts, emptySet<String>()) {
            assertEquals(R1Fit.Show, r1TileFit("musicspot", ctx(AppearanceSettings(homeLayout = HomeLayout.HERO_TILES))))
        }
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

    /** A cartridge game on a disc keeps its own console's plain disc: the Disc tile is greyed on one, shown on a disc game. */
    @Test fun discTileFollowsTheGameYoureOn() {
        val a = AppearanceSettings(homeLayout = HomeLayout.CLASSIC, cardArtType = com.shelf.launcher.data.CardArtType.DISC)
        fun fit(p: String?) = r1TileFit("discstyle", R1Ctx(a, secondDisplay = true, bluetooth = true, lights = true, romm = false,
            raAccounts = 1, themeRecolorable = true, chargeLimitWorks = true, focusPlatform = p))
        assertTrue(fit("N64") is R1Fit.Grey)
        assertEquals(R1Fit.Show, fit("PS2"))
        assertEquals(R1Fit.Show, fit(null))
    }

    /** Twin showcase's grid has no Endless (StyleTwinShowcase keeps it off). */
    @Test fun noEndlessOnTwinShowcase() {
        assertEquals(R1Fit.Hide, r1TileFit("endless", ctx(AppearanceSettings(homeLayout = HomeLayout.TWIN_SHOWCASE))))
        assertEquals(R1Fit.Show, r1TileFit("endless", ctx(AppearanceSettings(homeLayout = HomeLayout.TWIN_ICONS))))
    }

    /** The PS2 "7 Wonders" took the DS game's TheGamesDB entry (Nintendo's logo on its back): searched on its console now. */
    @Test fun tgdbSearchesTheGamesConsole() {
        assertEquals(11, com.shelf.launcher.data.TgdbPlatforms.idFor("PS2"))
        assertEquals(8, com.shelf.launcher.data.TgdbPlatforms.idFor("NDS"))
        assertEquals(41, com.shelf.launcher.data.TgdbPlatforms.idFor("GBC"))
        assertEquals(2, com.shelf.launcher.data.TgdbPlatforms.idFor("GameCube"))
        assertEquals(null, com.shelf.launcher.data.TgdbPlatforms.idFor("PICO8"))
        assertEquals(null, com.shelf.launcher.data.TgdbPlatforms.idFor(null))
    }
}
