package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 403: Cardboard box without synthetic slabs (row-wide sheen / contact rectangles, near-black case sides), side and end
 * faces attached to their own case and made of what the case is made of, and the window veil that follows the flight instead of
 * dimming the screen for ~8 frames before the case moves.
 */
class Physical403Test {
    private fun source(name: String): String {
        val roots = listOf("src/main/java/com/shelf/launcher/ui/", "app/src/main/java/com/shelf/launcher/ui/")
        return roots.map { File(it + name) }.first { it.exists() }.readText()
    }

    private fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    private fun between(s: String, a: String, b: String): String {
        val i = s.indexOf(a); val j = s.indexOf(b, i + 1)
        assertTrue("anchor $a", i >= 0); assertTrue("anchor $b", j > i)
        return s.substring(i, j)
    }

    // ---- veil follows the flight -------------------------------------------------------------------------------------

    @Test fun veilIsInvisibleUntilThePieceMoves() {
        assertEquals(0f, flightVeil(0f), 0f)
        assertEquals(0f, flightVeil(-0.2f), 0f)
    }

    @Test fun veilIsMonotonicAndEqualsTheFlight() {
        var last = -1f
        for (i in 0..100) { val v = flightVeil(i / 100f); assertTrue(v >= last); last = v }
        assertEquals(0.5f, flightVeil(0.5f), 0.0001f)
        assertEquals(1f, flightVeil(1f), 0f)
    }

    @Test fun windowVeilIsGatedByTheFlight() {
        val s = source("GameWindow.kt")
        assertTrue(s.contains("flightVeil(motion.lift.floatValue)"))
    }

    // ---- side / end faces ---------------------------------------------------------------------------------------------

    @Test fun sideTonesAreNeverBlackSlabs() {
        for (p in listOf("N64", "GBA", "SNES", "PS2", "PSP", "NDS", "3DS", "GBC", "Switch", null, "NoSuchConsole")) {
            val (lit, base) = boxSideTone(p)
            assertTrue("$p lit edge too dark", lum(lit) > 0.28f)
            assertTrue("$p shaded base too dark", lum(base) > 0.15f)
            assertTrue("$p shade must be darker than the lit edge", lum(base) < lum(lit))
        }
    }

    @Test fun cardboardBoxesShowCardboardAndPlasticShowsPlastic() {
        val cardboard = boxSideTone("N64").first
        val plastic = boxSideTone("PS2").first
        assertNotEquals(cardboard, plastic)
        // cardboard is warm (red above blue), the plastic grey is not
        assertTrue(cardboard.red > cardboard.blue + 0.15f)
        assertTrue(kotlin.math.abs(plastic.red - plastic.blue) < 0.1f)
    }

    @Test fun sideFaceSitsOnTheCaseOwnEdgeTowardTheMiddle() {
        // left of the middle: its right edge; right of the middle: its left edge (never an edge of another case, never off the case)
        for (x0 in listOf(-400f, -250f, -90f, -20f)) { val (x, left) = boxSideFaceX(x0, x0 + 20f); assertEquals(x0 + 20f, x, 0f); assertFalse(left) }
        for (x0 in listOf(10f, 120f, 300f)) { val (x, left) = boxSideFaceX(x0, x0 + 20f); assertEquals(x0, x, 0f); assertTrue(left) }
    }

    @Test fun sidesAndEndsAreToneByMaterialNotFixedBlack() {
        val s = source("StyleCardboardBox.kt")
        val faces = between(s, "private fun BoxCaseEnds(", "private fun BoxSheen")
        assertFalse(faces.contains("0xFF0C0C0E"))
        assertFalse(faces.contains("0xFF16171A"))
        assertTrue(faces.contains("boxSideTone("))
    }

    // ---- no synthetic row-wide plates -----------------------------------------------------------------------------------

    @Test fun sheenIsPerCaseInsideItsOwnFace() {
        val s = source("StyleCardboardBox.kt")
        val sheen = between(s, "private fun BoxSheen(", "private fun BoxScene(")
        assertFalse("no row bounding rectangle", sheen.contains("r.maxOf") || sheen.contains("r.first().x0"))
        assertFalse("no 45 % black wall bar", sheen.contains("0.45f"))
        assertTrue(sheen.contains("for (i in slots.indices)"))
        assertTrue(sheen.contains("s.x0, s.y0, s.stand"))
    }

    @Test fun contactShadowsAreAtEachCaseFootNotRowWide() {
        val s = source("StyleCardboardBox.kt")
        val body = between(s, "private fun BoxBody(", "private fun BoxCaseEnds(")
        assertFalse("row-wide shadow bands are gone", body.contains("rowsHere"))
        assertFalse(body.contains("r.maxOf { it.y1 }"))
        assertFalse(body.contains("A row that isn't full"))
        assertTrue(body.contains("for (s in slots)"))
    }

    @Test fun oneCasePerSlotKeyedByGame() {
        val s = source("StyleCardboardBox.kt")
        assertTrue(s.contains("key(g.stableKey) {\n                        BoxCase("))
        // only the lifted case is drawn above the row, and only once
        assertEquals(1, Regex("Modifier\\.zIndex\\(5f\\)").findAll(between(s, "private fun BoxCase(", "private fun smoothUp")).count())
    }

    @Test fun focusHasNoThemeAccentFillInCardboardBox() {
        val s = source("StyleCardboardBox.kt")
        val box = between(s, "private fun BoxCase(", "private fun smoothUp")
        assertFalse(box.contains("accent"))
        assertFalse(box.contains("focusRing"))
        assertFalse(box.contains(".background("))
    }
}

/** Update 403: Cover details shows the big case's spine and top (reference 07) without clipping it. */
class CoverDetails403Test {
    private val D = com.shelf.launcher.data.Features.SpineDepth.values()

    @Test fun turnAndTiltGrowWithSpineDepth() {
        val order = listOf(com.shelf.launcher.data.Features.SpineDepth.OFF, com.shelf.launcher.data.Features.SpineDepth.SUBTLE, com.shelf.launcher.data.Features.SpineDepth.REALISTIC)
        for (i in 1 until order.size) {
            assertTrue(bigCaseYaw(order[i]) > bigCaseYaw(order[i - 1]))
            assertTrue(bigCasePitch(order[i]) >= bigCasePitch(order[i - 1]))
        }
        assertEquals(0f, bigCaseYaw(order[0]), 0f)
    }

    @Test fun spineIsVisibleInSubtleAndRealistic() {
        // a thickness of 0.22 of the height (N64 / SNES box) shows at least ~3 % of the height as spine at the SUBTLE turn
        for (d in D) if (d != com.shelf.launcher.data.Features.SpineDepth.OFF) {
            val visible = 0.22f * kotlin.math.sin(Math.toRadians(bigCaseYaw(d).toDouble())).toFloat()
            assertTrue("$d spine share $visible", visible > 0.04f)
        }
    }

    @Test fun moreTurnLeavesMoreRoomSoTheCaseIsNeverCut() {
        val o = com.shelf.launcher.data.Features.SpineDepth.OFF
        val s = com.shelf.launcher.data.Features.SpineDepth.SUBTLE
        val r = com.shelf.launcher.data.Features.SpineDepth.REALISTIC
        assertTrue(bigCaseFill(o) > bigCaseFill(s)); assertTrue(bigCaseFill(s) > bigCaseFill(r))
        // the front's width at the turn (cos) plus the spine's (0.22 sin) times the fill never exceeds the room it was given
        for (d in listOf(s, r)) {
            val a = Math.toRadians(bigCaseYaw(d).toDouble())
            val across = (kotlin.math.cos(a) + 0.22 * kotlin.math.sin(a)).toFloat() * bigCaseFill(d)
            assertTrue("$d across $across", across <= 1.0f)
        }
    }
}
