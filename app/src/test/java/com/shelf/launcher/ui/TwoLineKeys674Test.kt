package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 674c (directive 15 Nov, item 4): on a narrow window at x1.3 a uniform row showed one key at a time (Record crate, Flip 2
 * f673k: PLAY alone between the arrows, SLEEVE NOTES setting every key's width). Where the keys' words can take two lines
 * the row uses the two-line width - more keys at once, still one row, every key the same, nothing cut.
 */
class TwoLineKeys674Test {
    @Test fun aRowThatFitsKeepsItsOneLineKeys() {
        val g = uniformRowPick(n = 3, need = 150, need2 = 90, width = 600, gap = 10, gutter = 16)
        assertFalse(g.scrolls)
        assertEquals(uniformRowGeom(3, 150, 600, 10, 16), g)
    }

    @Test fun theCrateCaseShowsMoreKeysWithTwoLineWords() {
        // f673k Crate: a 1030 px sleeve, SLEEVE NOTES 760 px on one line - one key at a time
        val one = uniformRowGeom(5, 760, 1030, 14, 36)
        assertEquals(1, one.visible)
        val g = uniformRowPick(5, 760, 470, 1030, 14, 36)
        assertTrue(g.visible >= 2)
        assertTrue(g.keyW >= 470)                    // never narrower than the two-line words need
    }

    @Test fun twoLineKeysThatFitAllStopTheScroll() {
        val g = uniformRowPick(n = 3, need = 300, need2 = 180, width = 640, gap = 10, gutter = 16)
        assertFalse(g.scrolls)
        assertTrue(g.keyW >= 180)
    }

    @Test fun noTwoLineWidthOrNoGainKeepsTheOneLineRow() {
        val one = uniformRowGeom(4, 400, 700, 10, 16)
        assertEquals(one, uniformRowPick(4, 400, 0, 700, 10, 16))     // a key can't wrap
        assertEquals(one, uniformRowPick(4, 400, 400, 700, 10, 16))   // nothing gained
        assertEquals(one, uniformRowPick(4, 400, 390, 700, 10, 16))   // still one at a time
    }

    @Test fun twoLineWidthIsTheLongestWordOrABalancedSplit() {
        assertEquals(300, twoLineKeyWidth(minW = 300, maxW = 400))   // one long word decides
        assertEquals(248, twoLineKeyWidth(minW = 150, maxW = 400))   // two equal words: about 62 %
        assertEquals(120, twoLineKeyWidth(minW = 120, maxW = 120))   // one word: its own width
    }
}
