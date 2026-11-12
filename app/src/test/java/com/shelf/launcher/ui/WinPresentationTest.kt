package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Popup infrastructure (12 Nov): which presentation every layout's game window opens, decided by one function
 * (winPresentation, WinPageFlow.kt) that GameWindow reads. Layout identity: a layout's window never turns into another
 * layout's / the shared presentation unless a popup style the user chose says so.
 */
class WinPresentationTest {
    private var styles: Set<String> = emptySet()
    private var classic: Set<String> = emptySet()
    private var banner = false
    private var scenes = true
    private var motion = Features.WinMotion.NONE

    @Before fun save() {
        styles = Features.popupStyleByLayout.state.value; classic = Features.classicWindowLayouts.state.value
        banner = Features.winBanner.state.value; scenes = Features.winScenes.state.value; motion = Features.winMotion.state.value
        Features.popupStyleByLayout.state.value = emptySet(); Features.classicWindowLayouts.state.value = emptySet()
        Features.winBanner.state.value = false; Features.winScenes.state.value = true; Features.winMotion.state.value = Features.WinMotion.NONE
    }

    @After fun restore() {
        Features.popupStyleByLayout.state.value = styles; Features.classicWindowLayouts.state.value = classic
        Features.winBanner.state.value = banner; Features.winScenes.state.value = scenes; Features.winMotion.state.value = motion
    }

    @Test fun defaultsEveryLayoutOpensItsOwnWindow() {
        for (l in HomeLayout.values()) {
            val want = if (winSceneFor(l) != null) WinPresentation.SCENE else WinPresentation.PLAIN
            assertEquals(l.name, want, winPresentation(l, inspect = false, inspectForced = false))
        }
    }

    @Test fun classicForOneLayoutOnly() {
        PopupStyle.set(HomeLayout.STORAGE.name, PopupStyle.CLASSIC)
        assertEquals(WinPresentation.CLASSIC, winPresentation(HomeLayout.STORAGE, false, false))
        for (l in HomeLayout.values()) if (l != HomeLayout.STORAGE)
            assertTrue(l.name, winPresentation(l, false, false) != WinPresentation.CLASSIC)
        // An explicit Inspect opens the layout's own window even where Classic is chosen (update 421).
        assertEquals(WinPresentation.SCENE, winPresentation(HomeLayout.STORAGE, true, false))
        assertEquals(WinPresentation.SCENE, winPresentation(HomeLayout.STORAGE, false, true))
    }

    @Test fun globalStylesAndOwnChoice() {
        Features.winBanner.state.value = true
        assertEquals(WinPresentation.BANNER, winPresentation(HomeLayout.NIGHT, false, false))
        PopupStyle.set(HomeLayout.NIGHT.name, PopupStyle.OWN)
        assertEquals(WinPresentation.SCENE, winPresentation(HomeLayout.NIGHT, false, false))
        Features.winBanner.state.value = false
        Features.winMotion.state.value = Features.WinMotion.SHARED
        assertEquals(WinPresentation.GLANCE, winPresentation(HomeLayout.TILES, false, false))
        Features.winMotion.state.value = Features.WinMotion.ROW_OPENS
        assertEquals(WinPresentation.ROW_MOTION, winPresentation(HomeLayout.TILES, false, false))
        // Inspect pressed in the window: the layout's own window, whatever the style.
        assertEquals(WinPresentation.SCENE, winPresentation(HomeLayout.TILES, true, false))
        Features.winScenes.state.value = false
        assertEquals(WinPresentation.PLAIN, winPresentation(HomeLayout.TILES, true, false))
    }

    @Test fun nativeLayoutsKeepEveryPage() {
        for (s in NATIVE_PAGE_SCENES) for (p in WinPage.values()) assertFalse("$s $p", sharedPageFor(s, p))
        // Every other scene: the main page is its own, the other pages the shared page (Cross keeps its options list).
        for (s in WinScene.values()) {
            assertFalse("$s MAIN", sharedPageFor(s, WinPage.MAIN))
            if (s !in NATIVE_PAGE_SCENES && s != WinScene.CROSS_OPTIONS)
                for (p in WinPage.values()) if (p != WinPage.MAIN) assertTrue("$s $p", sharedPageFor(s, p))
        }
        assertEquals(setOf(WinScene.STORAGE_RECORD, WinScene.NIGHT_STATUS, WinScene.TILE_CARD, WinScene.BLADE_GUIDE),
            setOf(winSceneFor(HomeLayout.STORAGE), winSceneFor(HomeLayout.NIGHT), winSceneFor(HomeLayout.TILES), winSceneFor(HomeLayout.BLADES)))
    }

    /** The window reads the one decision: no second, diverging copy of the style checks in GameWindow. */
    @Test fun gameWindowUsesTheOneDecision() {
        val src = listOf(File("src/main/java/com/shelf/launcher/ui/GameWindow.kt"), File("app/src/main/java/com/shelf/launcher/ui/GameWindow.kt")).first { it.exists() }.readText()
        assertTrue(src.contains("val presentation = winPresentation(layout, askedInspect, MediaInspect.force == g.stableKey)"))
        assertFalse(src.contains("PopupStyle.opensClassic(layout.name) && !askedInspect"))
        assertFalse(src.contains("rowMotion == Features.WinMotion.SHARED && !askedInspect"))
        assertTrue(src.contains("if (sharedPageFor(scene, page)) SceneExpandedPage("))
        assertEquals(1, Regex("SceneExpandedPage\\(look, ctx").findAll(src).count())
    }
}
