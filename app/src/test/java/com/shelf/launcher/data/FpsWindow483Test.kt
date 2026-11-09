package com.shelf.launcher.data

import com.shelf.launcher.data.perf.SurfaceFlingerFpsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** D4 (update 483): the SurfaceFlinger FPS reading follows the last second, not the whole 128-frame buffer. */
class FpsWindow483Test {
    private val ms = 1_000_000L

    @Test fun stepFrom30To60ReadsSixty() {
        // 98 frames at 30 fps (3.27 s), then 1 s at 60 fps: the old whole-buffer average said ~37.
        val t = ArrayList<Long>()
        var now = 0L
        repeat(98) { now += 33 * ms + 333_333; t += now }
        repeat(60) { now += 16 * ms + 666_667; t += now }
        val r = SurfaceFlingerFpsSource.fpsFrom(t.takeLast(128), now)!!
        assertEquals(60f, r.fps, 1.5f)
    }

    @Test fun steadyRateUnchanged() {
        val t = (1..128).map { it * 16_666_667L }
        assertEquals(60f, SurfaceFlingerFpsSource.fpsFrom(t, t.last())!!.fps, 0.5f)
    }

    @Test fun fewRecentFramesFallBackToTheBuffer() {
        // 2 fps: fewer than 3 frames in the last second → the whole buffer.
        val t = (1..10).map { it * 500 * ms }
        assertEquals(2f, SurfaceFlingerFpsSource.fpsFrom(t, t.last())!!.fps, 0.1f)
    }

    @Test fun staleIsNull() {
        val t = (1..128).map { it * 16_666_667L }
        assertNull(SurfaceFlingerFpsSource.fpsFrom(t, t.last() + 3_000 * ms))
    }
}
