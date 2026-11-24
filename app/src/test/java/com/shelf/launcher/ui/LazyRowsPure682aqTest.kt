package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 682aq: the per-row marks the lazy pages work out up front give what the old running counters gave, row by row. */
class LazyRowsPure682aqTest {
    private val earned = listOf(true, true, false, false, false, true, false)
    @Test fun crateMarksMatchTheRunningCounters() {
        var a = 0; var b = 0; var side: Boolean? = null
        val old = earned.map { e -> val h = side != e; val f = side == null; side = e; Triple(h, h && f, crateTrackNo(e, if (e) a++ else b++)) }
        assertEquals(old, crateMarks(earned).map { Triple(it.heading, it.first, it.no) })
        assertEquals(listOf("A1", "A2", "B1", "B2", "B3", "A3", "B4"), crateMarks(earned).map { it.no })
    }
    @Test fun discoverRunsRestartAtEachShelf() = assertEquals(listOf(0, 1, 0, 1, 2, 0, 0), discoverRuns(earned))
    @Test fun dockLinesKeepTheOrderKeyFirstAndFillTheWidth() {
        assertEquals(listOf(listOf(-1, 0, 1), listOf(2, 3, 4), listOf(5)), dockLines(6, 3))
        assertEquals(listOf(listOf(-1)), dockLines(0, 5))
        assertEquals(121, dockLines(120, 7).sumOf { it.size })
    }
}
