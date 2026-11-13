package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 524 (owner: "three games should look like three games"): the endless rows' places and their one lit place, for
 * 1-5 games, a row with exactly enough and a big library, at every scroll place round the loop (both ends, many steps).
 */
class EndlessOnceTest {
    private val sizes = listOf(1, 2, 3, 4, 5, 7, 11, 13, 40, 400)
    @After fun reset() { Features.endlessFill.value = false }

    @Test fun defaultIsEachGameOnce() {
        // (A saved choice - Fill the row - is still read from the settings; only the unsaved default changed.)
        assertFalse(Features.endlessFill.default)
    }

    @Test fun eachGameOnceGoingRound() {
        for (n in sizes) for (half in 1..6) for (base in -3 * n - 7..3 * n + 7) {
            if (n == 1 && base != 0) continue // (one game never goes round)
            val slots = loopSlots(base, n, half, loop = n > 1, fill = false)
            val games = slots.map { it.second }
            assertEquals("n=$n half=$half base=$base: a game twice $slots", games.size, games.toSet().size)
            assertTrue("n=$n half=$half base=$base: too many places", slots.size <= minOf(n, 2 * half + 1))
            // the game you're on is on the screen, at the place you're on
            assertTrue("n=$n half=$half base=$base: focus off screen $slots", slots.any { it.first == base && it.second == base.mod(n) })
            // the places stay in order, so the row doesn't jump
            assertEquals(slots.map { it.first }.sorted(), slots.map { it.first })
            // one lit
            val lit = slots.count { (slot, g) -> loopLit(n > 1, false, slot, base, g, base.mod(n)) }
            assertEquals("n=$n half=$half base=$base lit", 1, lit)
        }
    }

    @Test fun fillTheRowStillLightsOne() {
        for (n in sizes) for (half in 1..6) for (base in -3 * n - 7..3 * n + 7) {
            if (n == 1 && base != 0) continue
            val slots = loopSlots(base, n, half, loop = n > 1, fill = true)
            val lit = slots.count { (slot, g) -> loopLit(n > 1, true, slot, base, g, base.mod(n)) }
            assertEquals("fill n=$n half=$half base=$base lit $slots", 1, lit)
        }
    }

    @Test fun drawnOnceKeepsOneCopy() {
        Features.endlessFill.value = false
        for (n in sizes) for (before in 0 until n) {
            val window = (-3 * n..3 * n)
            val kept = window.filter { drawnOnce(it, n, before) }
            assertEquals("n=$n before=$before", n, kept.size)
            assertEquals("n=$n before=$before games", n, kept.map { it.mod(n) }.toSet().size)
            assertTrue(0 in kept)
        }
    }

    @Test fun withoutEndlessNothingWraps() {
        for (n in sizes) for (base in 0 until n) {
            val slots = loopSlots(base, n, 3, loop = false)
            assertTrue(slots.all { it.first == it.second && it.first in 0 until n })
        }
    }
}
