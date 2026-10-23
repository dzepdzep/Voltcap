package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.CategoryBar
import com.shelf.launcher.data.CategoryBars
import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.categoryBarFor
import com.shelf.launcher.data.categoryBarHere
import com.shelf.launcher.data.dockHere
import com.shelf.launcher.data.withCategoryBar
import com.shelf.launcher.data.withDockHere
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 315: the category name per layout (R1 Categories tile on every layout) and Tile home's own place. */
class HomeLayouts315Test {
    @Test fun barLayoutsKeepTheChoiceTheyHadAndTheOthersShowJustTheName() {
        val a = AppearanceSettings(categoryBar = CategoryBar.FULL)
        for (l in CategoryBars.BAR) assertEquals(l.name, CategoryBar.FULL, a.categoryBarFor(l))
        // The layouts without their own label got the bar of every category pasted on; now just the current one's name.
        assertEquals(CategoryBar.CURRENT, a.categoryBarFor(HomeLayout.HERO))
        assertEquals(CategoryBar.CURRENT, a.categoryBarFor(HomeLayout.TV))
        assertEquals(CategoryBar.CURRENT, a.categoryBarFor(HomeLayout.STACKS))
    }

    @Test fun offStillTakesTheNameOffWhereItDidBefore() {
        val a = AppearanceSettings(categoryBar = CategoryBar.OFF)
        assertEquals(CategoryBar.OFF, a.categoryBarFor(HomeLayout.CLASSIC))
        assertEquals(CategoryBar.OFF, a.categoryBarFor(HomeLayout.HERO))
        // Style homes kept their category with the old setting off.
        assertEquals(CategoryBar.CURRENT, a.categoryBarFor(HomeLayout.SHELF))
    }

    @Test fun eachLayoutKeepsItsOwnChoice() {
        val a = AppearanceSettings(homeLayout = HomeLayout.STACKS)
            .withCategoryBar(HomeLayout.STACKS, CategoryBar.OFF)
            .withCategoryBar(HomeLayout.HERO, CategoryBar.FULL)
        assertEquals(CategoryBar.OFF, a.categoryBarHere)
        assertEquals(CategoryBar.FULL, a.categoryBarFor(HomeLayout.HERO))
        assertEquals(CategoryBar.FULL, a.categoryBarFor(HomeLayout.CLASSIC))
        // Survives being written and read back.
        val back = CategoryBars.parse(a.categoryBarByLayout)
        assertEquals(CategoryBar.OFF, back[HomeLayout.STACKS])
        assertEquals(CategoryBar.FULL, back[HomeLayout.HERO])
    }

    @Test fun captionLayoutsOfferShownOrOff() {
        assertEquals(listOf(CategoryBar.CURRENT, CategoryBar.OFF), CategoryBars.choices(HomeLayout.SHELF))
        assertEquals("Shown", CategoryBars.short(HomeLayout.SHELF, CategoryBar.CURRENT))
        assertEquals(3, CategoryBars.choices(HomeLayout.HERO).size)
    }

    @Test fun everyLayoutIsInExactlyOneGroupOrGetsThePart() {
        for (l in HomeLayout.values()) {
            val groups = listOf(CategoryBars.BAR, CategoryBars.OWN, CategoryBars.MENU).count { l in it }
            assertTrue(l.name, groups <= 1)
        }
    }

    @Test fun garbageInTheStoredStringIsIgnored() {
        assertTrue(CategoryBars.parse("NOPE=FULL;HERO=MAYBE;;=").isEmpty())
    }

    @Test fun tileHomeSitsInTheMiddleByDefaultAndKeepsItsOwnPlace() {
        val tiles = AppearanceSettings(homeLayout = HomeLayout.TILES)
        assertEquals(DockPosition.MIDDLE, tiles.dockHere)
        val moved = tiles.withDockHere(DockPosition.TOP)
        assertEquals(DockPosition.TOP, moved.dockHere)
        // The Voltcap row's own place isn't touched.
        assertEquals(DockPosition.BOTTOM, moved.dockPosition)
        assertEquals(DockPosition.BOTTOM, moved.copy(homeLayout = HomeLayout.CLASSIC).dockHere)
    }
}
