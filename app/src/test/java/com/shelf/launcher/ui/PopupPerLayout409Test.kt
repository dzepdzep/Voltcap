package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Update 409 (D105): the popup style is chosen per home layout; the old settings are the global default. */
class PopupPerLayout409Test {
    @Before fun reset() = clean()
    @After fun after() = clean()
    private fun clean() {
        Features.popupStyleByLayout.state.value = emptySet()
        Features.classicWindowLayouts.state.value = emptySet()
        Features.winBanner.state.value = false
        Features.winMotion.state.value = Features.WinMotion.NONE
    }

    @Test fun oldSettingsAreMigratedUnchanged() {
        Features.winMotion.state.value = Features.WinMotion.ROW_REVEAL
        Features.classicWindowLayouts.state.value = setOf("SHELF")
        assertEquals(PopupStyle.ROW_REVEAL, PopupStyle.current("BOX"))
        assertEquals(PopupStyle.CLASSIC, PopupStyle.current("SHELF"))
        assertEquals(Features.WinMotion.ROW_REVEAL, PopupStyle.motion("BOX"))
        assertEquals(Features.WinMotion.NONE, PopupStyle.motion("SHELF"))
    }

    @Test fun changingOneLayoutLeavesOthersAlone() {
        Features.winBanner.state.value = true
        PopupStyle.set("BOX", PopupStyle.SHARED)
        PopupStyle.set("CHAN", PopupStyle.OWN)
        assertEquals(PopupStyle.SHARED, PopupStyle.current("BOX"))
        assertEquals(Features.WinMotion.SHARED, PopupStyle.motion("BOX"))
        assertEquals(PopupStyle.OWN, PopupStyle.current("CHAN"))
        assertFalse(PopupStyle.banner("CHAN"))            // an explicit Own beats the global banner
        assertEquals(PopupStyle.BANNER, PopupStyle.current("ARCTIC"))
        assertTrue(PopupStyle.banner("ARCTIC"))
        assertEquals(Features.WinMotion.NONE, Features.winMotion.state.value) // global untouched
        assertTrue(Features.winBanner.state.value)
    }

    @Test fun classicStaysInStepAndDefaultComesBack() {
        PopupStyle.set("TILES", PopupStyle.CLASSIC)
        assertTrue("TILES" in Features.classicWindowLayouts.state.value)
        PopupStyle.set("TILES", PopupStyle.BANNER)
        assertFalse("TILES" in Features.classicWindowLayouts.state.value)
        assertTrue(PopupStyle.banner("TILES"))
        PopupStyle.useDefault("TILES")
        assertFalse(PopupStyle.isOwnChoice("TILES"))
        assertEquals(PopupStyle.OWN, PopupStyle.current("TILES"))
    }

    @Test fun globalDefaultAppliesOnlyWhereNothingChosen() {
        PopupStyle.set("BOX", PopupStyle.OWN)
        PopupStyle.setGlobalDefault(PopupStyle.ROW_OPENS)
        assertEquals(PopupStyle.OWN, PopupStyle.current("BOX"))
        assertEquals(PopupStyle.ROW_OPENS, PopupStyle.current("SHELF"))
        assertEquals(Features.WinMotion.NONE, PopupStyle.motion("BOX"))
    }
}
