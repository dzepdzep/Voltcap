package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.shelf.launcher.data.Features.SaveVaultStyle as S

/** Batch 523c: Endless with few games / fast presses (Night menu, Airy, rows) and Cinema's console mark. */
class Batch523cTest {
    @Test fun fastStepsAreAllCounted() {
        // two games, two ▼ in one frame: from 0, focus back on 0 -> both steps count (no bounce back)
        assertEquals(2, loopSteps(0, 0, 2, 1, 2))
        // three games, two ▲ in one frame: 0 -> 1 (= 0 - 2 mod 3)
        assertEquals(2, loopSteps(0, 1, 3, -1, 2))
        // a tick that doesn't explain the focus (a tap in between): one step, the jump logic takes the rest
        assertEquals(1, loopSteps(0, 2, 5, 1, 3))
        assertEquals(1, loopSteps(0, 1, 5, 1, 1))
    }

    @Test fun shortListsDrawEachGameOnce() {
        for (n in 1..6) for (base in -7..7) {
            val s = loopSlots(base, n, 4, loop = true)
            assertEquals("n=$n base=$base", n, s.size)                       // fewer games than 9 bars: each once
            assertEquals(n, s.map { it.second }.toSet().size)
            assertTrue(s.all { it.first.mod(n) == it.second })                  // drawn at one of its own places
            assertTrue(s.all { kotlin.math.abs(it.first - base) <= n / 2 + 1 }) // near the scroll place
            assertTrue(s.any { it.first == base })                              // the one you're on is where the list is
        }
        assertEquals(9, loopSlots(0, 20, 4, loop = true).size)                  // a long list: every bar, no repeats on screen
        assertEquals(9, loopSlots(0, 20, 4, loop = true).map { it.second }.toSet().size)
        assertEquals(9, loopSlots(0, 3, 4, loop = true, fill = true).size)      // Endless › Fill the row: the owner's choice kept
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), loopSlots(0, 3, 4, loop = false)) // Endless off: real places only
        assertTrue(loopSlots(0, 0, 4, loop = true).isEmpty())
    }
}

class Batch523cAppArtTest {
    private fun hsv(argb: Int): FloatArray { val r = (argb shr 16) and 255; val g = (argb shr 8) and 255; val b = argb and 255
        val max = maxOf(r, g, b) / 255f; val min = minOf(r, g, b) / 255f; return floatArrayOf(if (max == 0f) 0f else (max - min) / max, max) }

    @Test fun appArtGroundIsRestrainedAndReadable() {
        for (c in listOf(0xFFFF0000.toInt(), 0xFF00FF66.toInt(), 0xFF0A0A0A.toInt(), 0xFFFFFFFF.toInt(), 0xFF3355AA.toInt())) {
            val (top, bottom) = appArtTones(c)
            val t = hsv(top); val b = hsv(bottom)
            assertTrue(t[0] <= 0.63f)                     // no neon
            assertTrue(t[1] in 0.33f..0.45f)              // white text readable
            assertTrue(b[1] < t[1])                       // darker at the foot
        }
        // a red icon gives a red ground
        val (top, _) = appArtTones(0xFFE53935.toInt())
        assertTrue(((top shr 16) and 255) > ((top shr 8) and 255))
    }

    @Test fun appIconKeepsAReadableSizeNeverBlownUp() {
        assertEquals(46f, appArtIconSide(100f, 100f, wide = false), 0.01f)
        assertEquals(160f, appArtIconSide(600f, 600f, wide = false), 0.01f)   // a hero tile: capped, not blown up
        assertEquals(28f, appArtIconSide(40f, 40f, wide = false), 0.01f)     // tiny tile: still readable
        assertEquals(52f, appArtIconSide(300f, 100f, wide = true), 0.01f)
    }
}

class Batch523cStorageTest {
    @Test fun storageMenuHasAHierarchyAndAnEvenGrid() {
        assertEquals(2, storageKeysPerRow(520f))   // beside the case
        assertEquals(3, storageKeysPerRow(860f))
        val src = java.io.File("src/main/java/com/shelf/launcher/ui/WinScenes.kt").readText()
        val storage = src.substring(src.indexOf("private fun StorageRecord"), src.indexOf("internal fun storageKeysPerRow"))
        assertTrue(storage.contains("primary.forEach { a -> StorageKey(a, red, big = true"))   // Play alone, first, bigger
        assertTrue(!storage.contains("maxItemsInEachRow"))                                     // no ragged FlowRow
        assertTrue(!storage.contains("minFontSize = 8.sp"))                                    // no per-key shrinking to cut text
    }
}

/** Batch 523 item 4: Details in Row opens / Row reveal / Shared keeps the window's own look (no generic details screen). */
class Batch523DetailsInStyleTest {
    private fun src(f: String) = java.io.File("src/main/java/com/shelf/launcher/ui/$f").readText()
    @Test fun detailsStaysInTheWindow() {
        val rr = src("RowRevealWindow.kt")
        assertTrue(rr.contains("showAll = !showAll"))                         // Details toggles the in-window story
        assertTrue(rr.contains("if (showAll && !closing"))                      // B closes the story first
        assertTrue(rr.contains("detailsFocus.requestFocus()"))                  // and focus goes back to Details
        val gl = src("GlanceWindow.kt")
        assertTrue(gl.contains("\"Details\", accent, Modifier.focusRequester(detailsFocus)) { showAll = !showAll }"))
        assertTrue(gl.contains("if (showAll && !closing"))
        assertTrue(!gl.contains("\"Details\", accent) { live { RowReveal.reset(); onFullDetails() } }"))
    }
}

/** Batch 523 item 9: Case flow with a short Endless list - each game once, continuous, never a visible jump. */
class Batch523CaseFlowTest {
    @Test fun eachGameOnceAndNoVisibleJump() {
        for (n in 2..10) {
            var pos = -3f
            val last = FloatArray(n) { flowPlace(it, pos, n) }
            while (pos < 3f) {
                pos += 0.01f
                val places = (0 until n).map { flowPlace(it, pos, n) }
                assertEquals("n=$n", n, places.map { Math.round(it * 1000) }.toSet().size) // every game its own place
                places.forEachIndexed { i, r ->
                    if (kotlin.math.abs(r - last[i]) > 0.5f)                             // jumped round the loop
                        assertTrue("n=$n i=$i r=$r", flowEdgeFade(r, n) < 0.05f && flowEdgeFade(last[i], n) < 0.05f)
                    last[i] = r
                }
            }
            // at rest every game is seen (2 games: both; 4 games: the extra one on the right)
            val rest = (0 until n).map { flowEdgeFade(flowPlace(it, 0f, n), n) }
            assertTrue("n=$n $rest", rest.all { it > 0.99f })
        }
        assertEquals(1f, flowPlace(1, 0f, 2), 1e-4f)
        assertEquals(2f, flowPlace(2, 0f, 4), 1e-4f); assertEquals(-1f, flowPlace(3, 0f, 4), 1e-4f)
    }
}

/** Batch 523 items 11 / 12: Icons only is flat icons; the PS2 browser is its own look; a save with no art gets its console's symbol. */
class Batch523VaultLooksTest {
    @Test fun ps2BrowserLookDrawsEverySaveAsTheBrowserDoes() {
        assertEquals(true, ps2BrowserFor(S.PS2, false, "PS2"))      // a PS2 save: its own 3D icon
        assertEquals(false, ps2BrowserFor(S.PS2, false, "SNES"))    // any other game: its logo badge, in the browser
        assertEquals(true, ps2BrowserFor(S.ICONS, false, "PS2"))    // Icons only: a PS2 save's icon IS its icon
        assertEquals(null, ps2BrowserFor(S.ICONS, true, "SNES"))    // ... and everything else flat, whatever the old switch
        assertEquals(null, ps2BrowserFor(S.MEMORY_CARD, false, "PS2")) // card looks: the switch, as before
        assertEquals(true, ps2BrowserFor(S.MEMORY_CARD, true, "PS2"))
        assertEquals(S.ICONS.ordinal + 1, S.PS2.ordinal)          // added last: saved choices unchanged
    }

    @Test fun iconsOnlyIsFlatAndNeverACover() {
        val ex = java.io.File("src/main/java/com/shelf/launcher/ui/SaveExplorer.kt").readText()
        val icons = ex.substring(ex.indexOf("vaultStyle == Features.SaveVaultStyle.ICONS) {"), ex.indexOf("} else Box(", ex.indexOf("vaultStyle == Features.SaveVaultStyle.ICONS) {")))
        assertTrue(!icons.contains("LogoSlab("))                  // the turning badge is the PS2 look's
        assertTrue(icons.contains("VaultIconFallback(game)"))
        val ls = java.io.File("src/main/java/com/shelf/launcher/ui/LauncherScreen.kt").readText()
        val logoFor = ls.substring(ls.indexOf("logoFor = { g ->"), ls.indexOf("logoFor = { g ->") + 600)
        assertTrue(logoFor.contains("vaultLogo[g.stableKey]") && !logoFor.contains("?.cover"))
    }
}

/** Batch 523 items 1 / 2: one object out of the shelf and back - no second copy, no flip. */
class Batch523PickupTest {
    @Test fun shelfBoxTurnsAsOneBox() {
        for (i in 0..20) {
            val t = i / 20f
            val (sp, fr) = shelfBoxFaces(t, 30f, 200f)
            val a = (t * Math.PI / 2).toFloat()
            val ds = 30f * kotlin.math.cos(a); val ws = 200f * kotlin.math.sin(a)
            assertEquals("faces touch, never overlap", ds / 2 + ws / 2, fr - sp, 1e-3f)
            assertEquals("centred on the box", 0f, (sp - ds / 2 + fr + ws / 2) / 2, 1e-3f)
        }
        // the phases: slide, then turn, then come forward - in that order, and the turn is done before the growth is
        assertEquals(0f, shelfTurn(0.3f), 1e-4f); assertEquals(1f, shelfTurn(0.7f), 1e-4f)
        assertEquals(0f, shelfGrow(0.62f), 1e-4f); assertTrue(shelfGrow(0.7f) < 0.3f)
    }

    @Test fun coverFlightIsTheSameObject() {
        CoverFlight.flights["k"] = androidx.compose.runtime.mutableStateOf(0.05f)
        assertEquals(1f, CoverFlight.hide("k"), 0f)                     // the slot is empty at once, no crossfade copy
        CoverFlight.flights.remove("k")
        assertEquals(CoverFlight.SPINE_YAW, CoverFlight.yaw(0f, -20f), 1e-4f) // leaves its slot spine-on
        assertEquals(-20f, CoverFlight.yaw(0.6f, -20f), 1e-4f)            // faces you before it lands
        var prev = CoverFlight.yaw(0f, -20f)
        for (i in 1..100) { val y = CoverFlight.yaw(i / 100f, -20f); assertTrue(y <= prev + 1e-4f); prev = y } // one way round, no flip
    }
}
