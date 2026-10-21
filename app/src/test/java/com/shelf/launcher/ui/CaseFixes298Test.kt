package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 298: the doubled-spine check (isTwinSpine). */
class CaseFixes298Test {
    /** A 16 × 64 sample: [left] for the left half's columns, [right] for the right half's. */
    private fun sample(left: (Int, Int) -> Int, right: (Int, Int) -> Int) =
        IntArray(16 * 64) { i -> val x = i % 16; val y = i / 16; if (x < 8) left(x, y) else right(x - 8, y) }

    private val strip: (Int, Int) -> Int = { x, y -> if (x in 3..4 && y % 6 < 4) 230 else 10 }

    @Test fun twoCopiesOfOneStripAreTwin() = assertTrue(isTwinSpine(sample(strip, strip)))

    @Test fun oneRealSpineIsNotTwin() =
        assertFalse(isTwinSpine(sample({ x, y -> if (y < 20) 240 else 30 + x * 10 }, { x, y -> if (y > 40) 200 else 90 - x * 5 })))

    @Test fun aPlainColourIsNotTwin() = assertFalse(isTwinSpine(IntArray(16 * 64) { 120 }))

    @Test fun wrongSizeIsNotTwin() = assertFalse(isTwinSpine(IntArray(10)))
}
