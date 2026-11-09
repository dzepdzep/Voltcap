package com.shelf.launcher.ui

import com.shelf.launcher.data.SystemLogos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 508 (staging only): P1 polish (case flight, Tile type, card front, cube light, undated timeline) and custom CD logos. */
class Batch508Test {
    private fun src(rel: String): String = listOf(File("../src_copy/$rel"), File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText()
    private fun asset(rel: String): File = listOf(File("src/main/assets/$rel"), File("app/src/main/assets/$rel")).first { it.parentFile?.exists() == true }

    @Test fun theCaseTakesATripYouCanFollow() {
        assertEquals(470, caseFlightMs(320, caseFlight = true, open = true))
        assertEquals(380, caseFlightMs(260, caseFlight = true, open = false))
        // Every other window keeps its own time.
        assertEquals(320, caseFlightMs(320, caseFlight = false, open = true))
        assertEquals(260, caseFlightMs(260, caseFlight = false, open = false))
        // Play's guard is not lengthened by it.
        assertEquals(450L, PLAY_GUARD_MS)
        // The trip follows the reveal's own easing (no second ease that front-loads it or holds it still).
        assertEquals(0.25f, caseFlightCurve(0.25f), 0.0001f)
        assertEquals(0.5f, caseFlightCurve(0.5f), 0.0001f)
        assertEquals(1f, caseFlightCurve(1f), 0f)
        assertEquals(0f, caseFlightCurve(-1f), 0f)
        val g = src("ui/GameWindow.kt")
        assertTrue(g.contains("caseFlightMs(sceneOpenMs(scene), caseFlight, open = true)"))
        assertTrue(g.contains("caseFlightMs(sceneCloseMs(scene), caseFlight, open = false)"))
        assertTrue(src("ui/WinSceneFamilies.kt").contains("val k = caseFlightCurve(sceneStage(ctx.reveal(), 0f, until))"))
        // It comes toward you on the way, and starts moving at once.
        assertTrue(CASE_FORWARD >= 0.15f && CASE_FORWARD <= 0.25f)
        assertTrue(g.contains("else if (caseFlight) androidx.compose.animation.core.LinearOutSlowInEasing else FastOutSlowInEasing"))
        // No wait before the reveal starts (measured: it only added ~100 ms to every window on the GT78).
        assertFalse(g.contains("awaitWindowDrawn"))
    }

    @Test fun tileTypeIsASizeUpOnSmallScreensOnly() {
        assertEquals(1f, tileLiftType(false), 0f)
        assertTrue(tileLiftType(true) > 1.1f && tileLiftType(true) < 1.3f)
        val f = src("ui/WinSceneFamilies.kt")
        assertTrue(f.contains("SceneFactsBody(ctx, muted, ink, upperLabels = false, omit = setOf(\"Console\"), scale = type)"))
        assertTrue(src("ui/WinScenesMore.kt").contains("scale: Float = 1f"))
    }

    @Test fun cardFrontAndTimelineHaveNoStrayDash() {
        assertEquals("NINTENDO 64  ·  1996", cardFrontLine("Nintendo 64", "1996"))
        assertEquals("NINTENDO 64", cardFrontLine("Nintendo 64", null))
        assertEquals("NINTENDO 64", cardFrontLine("Nintendo 64", " "))
        assertTrue(timelineDated("1998"))
        assertFalse(timelineDated(null)); assertFalse(timelineDated("")); assertFalse(timelineDated("—"))
        val f = src("ui/WinSceneFamilies.kt")
        assertFalse(f.contains("ctx.year ?: \"—\""))
        assertTrue(f.contains("TIMELINE_UNDATED"))
    }

    @Test fun cubeFacesTakeOneLight() {
        // The top is lit, the side shaded, darker toward the floor.
        assertTrue(cubeShade.topLight > 0f && cubeShade.sideTop < cubeShade.sideFoot)
        assertTrue(src("ui/WinSceneFamilies.kt").contains("cubeShade.topLight"))
    }

    @Test fun colourLogosGoOnInColourAndMonochromeOnesKeepTheWhitePrint() {
        // Mostly white with a little colour (GBA's lettering, Master System's red): colour.
        assertTrue(discLogoGoesInColour(IntArray(100) { if (it < 5) 0xFF2040D0.toInt() else 0xFFFFFFFF.toInt() }))
        // White, grey, black, or a navy that is not clearly coloured: the white print.
        assertFalse(discLogoGoesInColour(IntArray(100) { 0xFFFFFFFF.toInt() }))
        assertFalse(discLogoGoesInColour(IntArray(100) { 0xFF808080.toInt() }))
        assertFalse(discLogoGoesInColour(IntArray(100) { 0xFF000000.toInt() }))
        assertFalse(discLogoGoesInColour(IntArray(100) { if (it < 2) 0xFFE0201C.toInt() else 0xFFFFFFFF.toInt() }))
        // Sega CD's gold-and-black: its own (muted) colour.
        assertTrue(discLogoGoesInColour(IntArray(100) { if (it < 50) 0xFFB0AB80.toInt() else 0xFF000000.toInt() }))
        assertTrue(discLogoNeedsHalo(IntArray(100) { if (it < 50) 0xFFB0AB80.toInt() else 0xFF000000.toInt() }))
        // A dark colour logo gets its outline; a bright one doesn't.
        assertTrue(discLogoNeedsHalo(IntArray(100) { if (it < 10) 0xFF000000.toInt() else 0xFFE0201C.toInt() }))
        assertTrue(discLogoNeedsHalo(IntArray(100) { 0xFF3B2E91.toInt() }))
        assertFalse(discLogoNeedsHalo(IntArray(100) { 0xFFFFD200.toInt() }))
        assertEquals(16, discHaloRadius(320)); assertEquals(2, discHaloRadius(10))
    }

    @Test fun regionalModelsFindTheirOwnLogoAndBand() {
        for (k in listOf("MegaDrive", "TG16", "TGCD", "NeoGeoPocketColor", "WonderSwanColor")) {
            assertTrue(k, k in SystemLogos.mappedKeys)
            assertTrue(k, SystemLogos.discAsset(k) != null)
        }
        assertEquals("system_discs/megadrive.png", SystemLogos.discAsset("MegaDrive"))
        assertEquals("system_discs/genesis.png", SystemLogos.discAsset("Genesis"))
        // (509: the shipped SFC / Famicom assets are the Classic Mini's — not used, the generic CD stays.)
        assertEquals(null, SystemLogos.discAsset("SFC")); assertEquals(null, SystemLogos.discAsset("Famicom"))
        assertEquals("system_discs/snes.png", SystemLogos.discAsset("SNES")); assertEquals("system_discs/nes.png", SystemLogos.discAsset("NES"))
        assertEquals("system_discs/ngp.png", SystemLogos.discAsset("NeoGeoPocket"))
        assertEquals("system_discs/ngpc.png", SystemLogos.discAsset("NeoGeoPocketColor"))
        assertEquals("system_discs/wonderswan.png", SystemLogos.discAsset("WonderSwan"))
        // Nothing invented: no logo for the consoles none is shipped for.
        for (k in listOf("FDS", "SG1000", "Atari5200", "XboxOne", "XboxSeries", "Switch2")) assertEquals(k, null, SystemLogos.discAsset(k))
        // No console ever gets the Classic Mini's branding.
        for (k in SystemLogos.mappedKeys) {
            SystemLogos.discAsset(k)?.let { d -> assertFalse(k, SystemLogos.NOT_AUTHENTIC.any { d == "system_discs/$it.png" }) }
            SystemLogos.bundled(k)?.let { b -> assertFalse(k, SystemLogos.NOT_AUTHENTIC.any { b.endsWith("/$it.svg") }) }
        }
        // Every disc the map can hand out is shipped.
        for (k in SystemLogos.mappedKeys) SystemLogos.discAsset(k)?.let { assertTrue(it, asset(it).exists()) }
        assertEquals("Genesis", discAccentKey("MegaDrive")); assertEquals("PS2", discAccentKey("PS2"))
    }
}
