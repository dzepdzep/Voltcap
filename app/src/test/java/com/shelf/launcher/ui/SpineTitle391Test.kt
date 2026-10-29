package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 391: a custom spine's title never slides and never ends in "…": [SpineTitleFit] wraps it onto as many lines as
 * the spine's thickness allows (a word wider than the strip is broken where it must be) and shrinks until it fits.
 */
class SpineTitle391Test {
    /** 10 units per character, so "along" is a character budget. */
    private val m: (String) -> Float = { it.length * 10f }

    @Test fun shortNameStaysOneLine() {
        assertEquals(listOf("God of War"), SpineTitleFit.wrap(m, "God of War", 200f, 1))
    }

    @Test fun longNameWrapsOntoSecondLineWhenAllowed() {
        val w = SpineTitleFit.wrap(m, "The Legend of Zelda Ocarina", 150f, 2)
        assertNotNull(w)
        assertEquals("The Legend of Zelda Ocarina", w!!.joinToString(" "))
        assertTrue(w.all { m(it) <= 150f })
    }

    @Test fun doesNotFitIsNullNotCut() {
        assertNull(SpineTitleFit.wrap(m, "The Legend of Zelda Ocarina of Time", 100f, 2))
    }

    @Test fun wordWiderThanTheStripIsBrokenNotDropped() {
        val w = SpineTitleFit.wrap(m, "Supercalifragilistic", 80f, 4)
        assertNotNull(w)
        assertEquals("Supercalifragilistic", w!!.joinToString(""))
        assertTrue(w.all { m(it) <= 80f })
    }

    @Test fun resultNeverContainsEllipsis() {
        for (t in listOf("A", "Mario & Sonic at the Olympic Games Tokyo 2020 Special Edition", "Pokemon Mystery Dungeon Explorers of Sky")) {
            val w = SpineTitleFit.wrap(m, t, 120f, 6)
            assertNotNull(w)
            assertFalse(w!!.any { it.contains("…") || it.contains("...") })
            assertEquals(t.replace(" ", ""), w.joinToString("").replace(" ", ""))
        }
    }
}
