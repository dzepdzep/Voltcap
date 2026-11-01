package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Update 421: popup audit — style choice, Classic dispatch and the layout → scene map. */
class Popup421Test {
    @Before fun reset() = clean()
    @After fun after() = clean()
    private fun clean() {
        Features.popupStyleByLayout.state.value = emptySet()
        Features.classicWindowLayouts.state.value = emptySet()
        Features.winBanner.state.value = false
        Features.winMotion.state.value = Features.WinMotion.NONE
    }

    @Test fun classicIsClassicWhateverTheLayoutsScene() {
        // BOX, CHAN, CINEMA have a scene; Classic must still win for the dispatcher.
        for (l in listOf(HomeLayout.BOX, HomeLayout.CHAN, HomeLayout.CINEMA, HomeLayout.SHELF)) {
            PopupStyle.set(l.name, PopupStyle.CLASSIC)
            assertTrue(l.name, PopupStyle.opensClassic(l.name))
            assertFalse(PopupStyle.banner(l.name))
            assertEquals(Features.WinMotion.NONE, PopupStyle.motion(l.name))
        }
        // The legacy per-layout switch on its own counts too (old saved prefs).
        clean(); Features.classicWindowLayouts.state.value = setOf("ARCTIC")
        assertTrue(PopupStyle.opensClassic("ARCTIC"))
        assertFalse(PopupStyle.opensClassic("BOX"))
    }

    @Test fun rowStylesHiddenWithoutARowButAChosenOneStaysListed() {
        val noRow = PopupStyle.offered("CHAN", hasRow = false)
        assertFalse(noRow.any { PopupStyle.rowStyle(it) })
        assertTrue(PopupStyle.CLASSIC in noRow && PopupStyle.OWN in noRow && PopupStyle.BANNER in noRow)
        assertEquals(PopupStyle.values().toList(), PopupStyle.offered("CHAN", hasRow = true))
        // Never reset silently: a saved row style stays visible even where the row is missing.
        PopupStyle.set("CHAN", PopupStyle.ROW_REVEAL)
        assertTrue(PopupStyle.ROW_REVEAL in PopupStyle.offered("CHAN", hasRow = false))
        assertEquals(PopupStyle.ROW_REVEAL, PopupStyle.current("CHAN"))
    }

    @Test fun everyStyleIsDescribed() {
        for (s in PopupStyle.values()) assertTrue(s.name, PopupStyle.describe(s).isNotBlank())
    }

    @Test fun layoutsThatAskedForTheirOwnPopupHaveOne() {
        val wanted = listOf(
            HomeLayout.CRATE, HomeLayout.CARDS, HomeLayout.DISCS, HomeLayout.GREENCORE, HomeLayout.CUBE,
            HomeLayout.POCKET_CLASSIC, HomeLayout.POCKET_RIBBON, HomeLayout.POCKET, HomeLayout.SHELF, HomeLayout.STACKS,
            HomeLayout.COVERDETAILS, HomeLayout.TWIN_DESK, HomeLayout.TWIN_ICONS, HomeLayout.TWIN_SHOWCASE, HomeLayout.ARTLIB,
            HomeLayout.BOX, HomeLayout.CHAN, HomeLayout.CINEMA, HomeLayout.ARCTIC
        )
        for (l in wanted) assertNotNull(l.name, winSceneFor(l))
        // Update 422: every layout has its own window except Layout Studio (user-built) and the TV room (opens on its TV).
        for (l in HomeLayout.values()) if (l != HomeLayout.CUSTOM && l != HomeLayout.TV) assertNotNull("${l.name} has no popup of its own", winSceneFor(l))
        // Every scene is used by at least one layout (no dead scene).
        val used = HomeLayout.values().mapNotNull { winSceneFor(it) }.toSet()
        for (s in WinScene.values()) assertTrue(s.name, s in used)
    }
}
