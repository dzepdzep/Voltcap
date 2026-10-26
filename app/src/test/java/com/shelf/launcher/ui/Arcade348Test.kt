package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Update 348: the cabinets as panels and a front between them, their light, and the hall's spacing. */
class Arcade348Test {
    private val builds = CabBuild.values().map { cabModel(it) }

    /** The front of [pts] (how far forward the outline reaches) at height [y]. */
    private fun frontAt(pts: List<Offset>, y: Float): Float {
        var best = -1f
        for (i in pts.indices) {
            val a = pts[i]; val b = pts[(i + 1) % pts.size]
            if ((a.y - y) * (b.y - y) <= 0f && a.y != b.y) best = maxOf(best, a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y))
        }
        return best
    }

    /** Side outlines go round counter-clockwise; the builds with panels have their fronts set in between them. */
    @Test fun panelsAndFront() {
        for (m in builds) {
            var area = 0f
            for (i in m.side.indices) { val a = m.side[i]; val b = m.side[(i + 1) % m.side.size]; area += a.x * b.y - b.x * a.y }
            assertTrue("${m.build} side counter-clockwise", area > 0f)
        }
        assertEquals(0f, cabModel(CabBuild.CANDY).inset, 0f)
        for (b in listOf(CabBuild.UPRIGHT, CabBuild.NEO, CabBuild.BARTOP)) {
            val m = cabModel(b)
            assertTrue("$b has panels", m.inset > 0f)
            // The screen sits back between the panels; the control panel juts out past them.
            val s = m.edge(CabRole.SCREEN); val mid = (m.pts[s].y + m.pts[s + 1].y) / 2f
            assertTrue("$b screen recessed", frontAt(m.pts, mid) < frontAt(m.side, mid) - 0.01f)
            val dk = m.edge(CabRole.DECK)
            assertTrue("$b deck juts out", m.pts[dk].x > frontAt(m.side, m.pts[dk].y) + 0.01f)
        }
    }

    /** The builds differ where it shows: deck height (seated candy lowest), depth, the bartop on its counter. */
    @Test fun proportionsDiffer() {
        fun deckY(m: CabModel) = m.pts[m.edge(CabRole.DECK)].y
        assertTrue(deckY(cabModel(CabBuild.CANDY)) < deckY(cabModel(CabBuild.NEO)))
        assertTrue(deckY(cabModel(CabBuild.NEO)) < deckY(cabModel(CabBuild.UPRIGHT)))
        assertTrue(cabModel(CabBuild.BARTOP).foot in 0.7f..0.9f)
        assertTrue(cabModel(CabBuild.UPRIGHT).h > cabModel(CabBuild.NEO).h && cabModel(CabBuild.NEO).h > cabModel(CabBuild.CANDY).h)
    }

    /** Light never pushes a paint past its own colour (a white cabinet doesn't glow); more light, lighter. */
    @Test fun lightKeepsDetail() {
        val white = Color(0xFFD3CFC5)
        for (k in listOf(0f, 0.3f, 0.7f, 1f)) {
            val c = litColor(white, k)
            assertTrue(c.red <= white.red + 1e-4f && c.green <= white.green + 1e-4f)
        }
        assertTrue(litColor(white, 0.2f).red < litColor(white, 0.8f).red)
    }

    /** The hall's places: further out each time, and seen from the eye each one clear of the one before it. */
    @Test fun hallMachinesDontOverlap() {
        val r = 10f; val z0 = 3.4f; val w = 0.8f; val d = 0.9f
        val s = hallSlots(r, z0, w, d, 0.16f, 11f, 0.55f, n = 4, maxPhi = 1.35f)
        assertEquals(0f, s[0], 0f)
        for (i in 1 until s.size) assertTrue(s[i] > s[i - 1])
        // The first three each side fit before the arc's end.
        assertTrue(s[3] < 1.25f)
        fun x(p: Float) = r * sin(p) / (z0 + r * (1f - cos(p)))
        for (i in 2..3) assertTrue(x(s[i]) - x(s[i - 1]) > 0.2f)
        assertEquals(-hallPhi(s, 1.5f), hallPhi(s, -1.5f), 1e-6f)
        assertEquals((s[1] + s[2]) / 2f, hallPhi(s, 1.5f), 1e-5f)
    }
}
