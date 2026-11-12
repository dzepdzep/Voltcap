package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 525 (the original Shelf's custom-spine flicker): the learnt spine shape lays out every slot of the shelf, so it
 * may only change when it really moves - browsing (the same spines loading again as they come back into view, a few new
 * ones with a slightly different shape) never re-lays the shelf.
 */
class SpineShapeStable525Test {
    @Test fun smallDriftKeepsTheShelf() {
        val start = settledSpineShape(null, listOf(0.080f, 0.082f, 0.081f))!!
        assertEquals(0.081f, start, 1e-6f)
        // more spines a hair apart: kept
        assertNull(settledSpineShape(start, listOf(0.080f, 0.082f, 0.081f, 0.083f, 0.0835f)))
        // a really different shape (PS1 slim spines arrive): moves once
        val moved = settledSpineShape(start, listOf(0.080f, 0.082f, 0.081f, 0.060f, 0.059f, 0.058f, 0.061f))
        assertTrue(moved != null && moved < start * (1f - SPINE_SHAPE_STEP))
    }

    @Test fun browsingAShelfChangesTheShapeAFewTimesAtMost() {
        // 40 spines of one console loading in random order, each loading again and again (shelves coming back into view):
        // with the same picture counted once and the step, the shelf is laid out again only a handful of times.
        val rnd = java.util.Random(7)
        val shapes = List(40) { 0.07f + rnd.nextFloat() * 0.03f }
        var now: Float? = null
        var changes = 0
        val seen = LinkedHashMap<Int, Float>()
        repeat(400) {
            val i = rnd.nextInt(40)
            if (seen.containsKey(i)) return@repeat // (counted once - RealSpineShape keys by picture)
            seen[i] = shapes[i]
            if (seen.size >= 3) settledSpineShape(now, seen.values.toList())?.let { now = it; changes++ }
        }
        assertTrue("re-laid $changes times", changes <= 4)
    }

    @Test fun geometryRemembered() {
        SpineGeom.put("a", 0.07f, true)
        assertEquals(0.07f, SpineGeom.shape("a")!!, 0f)
        assertEquals(true, SpineGeom.lying("a"))
        assertNull(SpineGeom.shape("b"))
    }
}
