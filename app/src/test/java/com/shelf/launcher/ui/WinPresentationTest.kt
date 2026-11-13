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

    /**
     * Phase 10 (settings conflicts): the five prefs that decide what A opens (popup_style_by_layout, classic_window_layouts,
     * win_banner, win_motion, win_scenes) in every combination, for every layout: one answer each, never a crash, and the
     * documented precedence - the layout's own choice, then its Classic switch, then the global default; Inspect wins over all.
     */
    @Test fun everyCombinationOfThePopupPrefsHasOneAnswer() {
        val own = listOf<PopupStyle?>(null) + PopupStyle.values()
        var n = 0
        for (l in HomeLayout.values()) for (choice in own) for (cl in listOf(false, true)) for (b in listOf(false, true))
            for (m in Features.WinMotion.values()) for (sc in listOf(true, false)) {
                Features.popupStyleByLayout.state.value = if (choice == null) emptySet() else setOf("${l.name}=${choice.name}")
                Features.classicWindowLayouts.state.value = if (cl) setOf(l.name) else emptySet()
                Features.winBanner.state.value = b; Features.winMotion.state.value = m; Features.winScenes.state.value = sc
                val p = winPresentation(l, false, false)
                val style = choice ?: if (cl) PopupStyle.CLASSIC else PopupStyle.globalDefault()
                val ownWin = if (sc && winSceneFor(l) != null) WinPresentation.SCENE else WinPresentation.PLAIN
                val want = when (style) {
                    PopupStyle.CLASSIC -> WinPresentation.CLASSIC
                    PopupStyle.SHARED -> WinPresentation.GLANCE
                    PopupStyle.ROW_OPENS, PopupStyle.ROW_REVEAL -> WinPresentation.ROW_MOTION
                    PopupStyle.BANNER -> WinPresentation.BANNER
                    PopupStyle.OWN -> ownWin
                }
                assertEquals("$l choice=$choice classic=$cl banner=$b motion=$m scenes=$sc", want, p)
                assertEquals("$l: Inspect opens the layout's own window", ownWin, winPresentation(l, true, false))
                n++
            }
        assertEquals(HomeLayout.values().size * own.size * 2 * 2 * Features.WinMotion.values().size * 2, n)
    }
}
