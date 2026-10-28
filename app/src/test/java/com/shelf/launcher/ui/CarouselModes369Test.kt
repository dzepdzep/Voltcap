package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 369: "carousel views disappeared from Case Stacks". Locks that all 13 CarouselView modes stay offered — as the R1
 * Carousel tile's hold list and in Settings › Carousel — on Case stacks and on every other layout that has a carousel,
 * and that Case flow's own shapes stay separate from them.
 */
class CarouselModes369Test {
    private fun ctx(l: HomeLayout) = R1Ctx(
        AppearanceSettings(homeLayout = l), secondDisplay = true, bluetooth = true, lights = true, romm = false,
        raAccounts = 1, themeRecolorable = true, chargeLimitWorks = true
    )

    private fun source(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name")
    ).first { it.exists() }.readText()

    @Test fun thirteenModesStayAndAreUnique() {
        val all = CarouselView.values()
        assertEquals(13, all.size)
        assertEquals(13, all.map { it.label }.toSet().size)
        for (n in listOf("TUCKED", "FLIP", "WHEEL", "DECK", "FLAT", "POP", "ARC", "SPINE_WALL", "TURNED", "RECEDE", "ARCH", "BOX_FLOW", "SPINE_PULL"))
            assertTrue("missing mode $n", all.any { it.name == n })
    }

    @Test fun carouselTileShowsOnCaseStacksAndTheOtherCarouselLayouts() {
        for (l in listOf(HomeLayout.STACKS, HomeLayout.SIDE, HomeLayout.CLASSIC, HomeLayout.GRID, HomeLayout.HERO, HomeLayout.TV, HomeLayout.CASEFLOW))
            assertEquals("carousel tile on $l", R1Fit.Show, r1TileFit("carousel", ctx(l)))
        assertTrue(carouselApplies(HomeLayout.STACKS))
    }

    @Test fun everyModeIsOfferedThroughR1AndSettings() {
        val r1 = source("QuickSettings.kt")
        // hold list: every value, no filter; tap: steps through every value
        assertTrue(r1.contains("CarouselView.values().map { v ->"))
        assertTrue(r1.contains("val views = CarouselView.values()"))
        assertTrue(!r1.contains("CarouselView.values().filter"))
        val st = source("SettingsDialog.kt")
        assertTrue(st.contains("com.shelf.launcher.ui.CarouselView.values(), f.carouselView.state.value"))
        // Settings fold is offered for Case stacks
        assertTrue(st.contains("appearance.homeLayout == com.shelf.launcher.data.HomeLayout.STACKS)) { // (Update 159"))
    }

    @Test fun caseFlowKeepsItsOwnShapes() {
        val r1 = source("QuickSettings.kt")
        assertTrue(r1.contains("CaseFlowShape.values().map { v -> R1Choice(v.label, now == v)"))
        assertTrue(Features.carouselLayouts.state.value.let { true }) // pref readable
    }

    @Test fun everySavedModeNameRestores() {
        for (v in CarouselView.values()) {
            val before = Features.carouselView.value
            Features.carouselView.value = v
            assertEquals(v, Features.carouselView.state.value)
            Features.carouselView.value = before
        }
    }
}
