package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Size
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.arcadeGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Update 347: Arcade cabinets 2.0 — the machines' shapes, the way they're seen, and the layout coming back safely. */
class Arcade347Test {
    private val builds = CabBuild.values().map { cabModel(it) }

    /** A home saved as Arcade before 347 stays on the Voltcap row; one picked again now opens as Arcade. */
    @Test fun arcadeComesBackOnlyWhenPicked() {
        assertEquals(HomeLayout.CLASSIC, arcadeGate(HomeLayout.ARCADE, restored = false))
        assertEquals(HomeLayout.ARCADE, arcadeGate(HomeLayout.ARCADE, restored = true))
        assertEquals(HomeLayout.BLADES, arcadeGate(HomeLayout.BLADES, restored = false))
        assertTrue(HomeLayout.ARCADE in HomeLayout.pickable)
    }

    /** Each profile goes round counter-clockwise, is closed and as tall as its machine, with a screen, marquee and deck. */
    @Test fun profilesAreSound() {
        for (m in builds) {
            assertEquals(m.pts.size, m.roles.size)
            var area = 0f
            for (i in m.pts.indices) { val a = m.pts[i]; val b = m.pts[(i + 1) % m.pts.size]; area += a.x * b.y - b.x * a.y }
            assertTrue("${m.build} counter-clockwise", area > 0f)
            assertEquals("${m.build} height", m.h, m.pts.maxOf { it.y }, 0.001f)
            assertTrue(m.pts.maxOf { it.x } <= m.d + 0.001f)
            for (r in listOf(CabRole.SCREEN, CabRole.MARQUEE, CabRole.DECK)) assertTrue("${m.build} $r", m.edge(r) >= 0)
        }
        // The bartop has no coin door or kick plate (it stands on a counter).
        val bar = cabModel(CabBuild.BARTOP)
        assertFalse(CabRole.DOOR in bar.roles); assertFalse(CabRole.KICK in bar.roles)
        assertTrue(CabRole.DOOR in cabModel(CabBuild.UPRIGHT).roles)
    }

    /** The four builds don't share a silhouette: their shapes (width / height, screen tilt, deck height) all differ. */
    @Test fun buildsHaveTheirOwnSilhouettes() {
        val front = CabView(0f, 0f)
        val aspects = builds.map { cabAspect(it, front) }
        for (i in aspects.indices) for (j in aspects.indices) if (i < j) assertTrue("${builds[i].build} vs ${builds[j].build}", abs(aspects[i] - aspects[j]) > 0.03f)
        fun tilt(m: CabModel): Float { val i = m.edge(CabRole.SCREEN); val a = m.pts[i]; val b = m.pts[i + 1]; return Math.toDegrees(kotlin.math.atan2((a.x - b.x).toDouble(), (b.y - a.y).toDouble())).toFloat() }
        val tilts = builds.associate { it.build to tilt(it) }
        assertTrue(tilts.getValue(CabBuild.CANDY) > tilts.getValue(CabBuild.UPRIGHT))
        assertTrue(tilts.getValue(CabBuild.NEO) < tilts.getValue(CabBuild.UPRIGHT))
    }

    /** Seen from the right, the right side panel shows; the screen and deck face you; the frame fits its box. */
    @Test fun viewsAndFit() {
        for (m in builds) {
            val f = CabFrame(m, CabView(20f, 9f), Size(400f, 800f))
            assertEquals(m.w, f.nearU, 0.001f)
            assertTrue(f.faces(m.edge(CabRole.SCREEN))); assertTrue(f.faces(m.edge(CabRole.DECK)))
            val b = f.bounds()
            assertTrue(b.left >= -0.5f && b.right <= 400.5f && b.top >= -0.5f && b.bottom <= 800.5f)
            assertEquals(800f, b.bottom, 1f)
            assertEquals(0f, CabFrame(m, CabView(-20f, 9f), Size(400f, 800f)).nearU, 0.001f)
        }
    }

    /** The turn–stretch–turn found for a face puts a picture exactly on it. */
    @Test fun affineSplitsExactly() {
        for ((a, b, c, d) in listOf(floatArrayOf(0.9f, 0.2f, -0.15f, 0.7f), floatArrayOf(1f, 0f, 0.3f, 0.8f), floatArrayOf(0.8f, -0.1f, 0.2f, 1.1f))) {
            val r = decompose2(a, b, c, d)
            val t1 = Math.toRadians(r[0].toDouble()); val t2 = Math.toRadians(r[3].toDouble())
            // R(t2)·S·R(t1)
            val m00 = (cos(t2) * r[1] * cos(t1) - sin(t2) * r[2] * sin(t1)).toFloat()
            val m01 = (-cos(t2) * r[1] * sin(t1) - sin(t2) * r[2] * cos(t1)).toFloat()
            val m10 = (sin(t2) * r[1] * cos(t1) + cos(t2) * r[2] * sin(t1)).toFloat()
            val m11 = (-sin(t2) * r[1] * sin(t1) + cos(t2) * r[2] * cos(t1)).toFloat()
            assertEquals(a, m00, 1e-4f); assertEquals(b, m01, 1e-4f); assertEquals(c, m10, 1e-4f); assertEquals(d, m11, 1e-4f)
        }
    }

    /** Mixed: handhelds on bartops, arcade boards in candy cabinets; a picked build is used for every game. */
    @Test fun mixedPicksByConsole() {
        assertEquals(CabBuild.BARTOP, cabBuildFor(Features.ArCab.MIXED, "GBA"))
        assertEquals(CabBuild.CANDY, cabBuildFor(Features.ArCab.MIXED, "MAME"))
        assertEquals(CabBuild.NEO, cabBuildFor(Features.ArCab.MIXED, "PS2"))
        assertEquals(CabBuild.UPRIGHT, cabBuildFor(Features.ArCab.MIXED, "SNES"))
        assertEquals(CabBuild.CANDY, cabBuildFor(Features.ArCab.CANDY, "GBA"))
        assertNotEquals(cabBuildFor(Features.ArCab.MIXED, "GBA"), cabBuildFor(Features.ArCab.MIXED, "MAME"))
    }

    /** The Insert Coin screen switches on line first, then opens; off goes back the same way. */
    @Test fun tubeOnOff() {
        assertEquals(0f to 0f, arcadeTube(0f)); assertEquals(1f to 1f, arcadeTube(1f))
        val (l, o) = arcadeTube(0.2f); assertTrue(l > 0f && o == 0f)
    }
}
