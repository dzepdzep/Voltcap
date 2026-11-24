package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 682aq: the per-row marks the lazy pages work out up front give what the old running counters gave, row by row. */
class LazyRowsPure682aqTest {
    private val earned = listOf(true, true, false, false, false, true, false)
    @Test fun discoverRunsRestartAtEachShelf() = assertEquals(listOf(0, 1, 0, 1, 2, 0, 0), discoverRuns(earned))
    @Test fun dockLinesKeepTheOrderKeyFirstAndFillTheWidth() {
        assertEquals(listOf(listOf(-1, 0, 1), listOf(2, 3, 4), listOf(5)), dockLines(6, 3))
        assertEquals(listOf(listOf(-1)), dockLines(0, 5))
        assertEquals(121, dockLines(120, 7).sumOf { it.size })
    }
}
