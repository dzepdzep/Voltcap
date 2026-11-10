package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Batch 523c: Endless with few games / fast presses (Night menu, Airy, rows) and Cinema's console mark. */
class Batch523cTest {
    @Test fun fastStepsAreAllCounted() {
        // two games, two ▼ in one frame: from 0, focus back on 0 -> both steps count (no bounce back)
        assertEquals(2, loopSteps(0, 0, 2, 1, 2))
        // three games, two ▲ in one frame: 0 -> 1 (= 0 - 2 mod 3)
        assertEquals(2, loopSteps(0, 1, 3, -1, 2))
        // a tick that doesn't explain the focus (a tap in between): one step, the jump logic takes the rest
        assertEquals(1, loopSteps(0, 2, 5, 1, 3))
        assertEquals(1, loopSteps(0, 1, 5, 1, 1))
    }

    @Test fun shortListsDrawEachGameOnce() {
        for (n in 1..6) for (base in -7..7) {
            val s = loopSlots(base, n, 4, loop = true)
            assertEquals("n=$n base=$base", n, s.size)                       // fewer games than 9 bars: each once
            assertEquals(n, s.map { it.second }.toSet().size)
            assertTrue(s.all { it.first.mod(n) == it.second })                  // drawn at one of its own places
            assertTrue(s.all { kotlin.math.abs(it.first - base) <= n / 2 + 1 }) // near the scroll place
            assertTrue(s.any { it.first == base })                              // the one you're on is where the list is
        }
        assertEquals(9, loopSlots(0, 20, 4, loop = true).size)                  // a long list: every bar, no repeats on screen
        assertEquals(9, loopSlots(0, 20, 4, loop = true).map { it.second }.toSet().size)
        assertEquals(9, loopSlots(0, 3, 4, loop = true, fill = true).size)      // Endless › Fill the row: the owner's choice kept
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2), loopSlots(0, 3, 4, loop = false)) // Endless off: real places only
        assertTrue(loopSlots(0, 0, 4, loop = true).isEmpty())
    }
}

class Batch523cAppArtTest {
    private fun hsv(argb: Int): FloatArray { val r = (argb shr 16) and 255; val g = (argb shr 8) and 255; val b = argb and 255
        val max = maxOf(r, g, b) / 255f; val min = minOf(r, g, b) / 255f; return floatArrayOf(if (max == 0f) 0f else (max - min) / max, max) }

    @Test fun appArtGroundIsRestrainedAndReadable() {
        for (c in listOf(0xFFFF0000.toInt(), 0xFF00FF66.toInt(), 0xFF0A0A0A.toInt(), 0xFFFFFFFF.toInt(), 0xFF3355AA.toInt())) {
            val (top, bottom) = appArtTones(c)
            val t = hsv(top); val b = hsv(bottom)
            assertTrue(t[0] <= 0.63f)                     // no neon
            assertTrue(t[1] in 0.33f..0.45f)              // white text readable
            assertTrue(b[1] < t[1])                       // darker at the foot
        }
        // a red icon gives a red ground
        val (top, _) = appArtTones(0xFFE53935.toInt())
        assertTrue(((top shr 16) and 255) > ((top shr 8) and 255))
    }

    @Test fun appIconKeepsAReadableSizeNeverBlownUp() {
        assertEquals(46f, appArtIconSide(100f, 100f, wide = false), 0.01f)
        assertEquals(160f, appArtIconSide(600f, 600f, wide = false), 0.01f)   // a hero tile: capped, not blown up
        assertEquals(28f, appArtIconSide(40f, 40f, wide = false), 0.01f)     // tiny tile: still readable
        assertEquals(52f, appArtIconSide(300f, 100f, wide = true), 0.01f)
    }
}

class Batch523cStorageTest {
    @Test fun storageMenuHasAHierarchyAndAnEvenGrid() {
        assertEquals(2, storageKeysPerRow(520f))   // beside the case
        assertEquals(3, storageKeysPerRow(860f))
        val src = java.io.File("src/main/java/com/shelf/launcher/ui/WinScenes.kt").readText()
        val storage = src.substring(src.indexOf("private fun StorageRecord"), src.indexOf("internal fun storageKeysPerRow"))
        assertTrue(storage.contains("primary.forEach { a -> StorageKey(a, red, big = true"))   // Play alone, first, bigger
        assertTrue(!storage.contains("maxItemsInEachRow"))                                     // no ragged FlowRow
        assertTrue(!storage.contains("minFontSize = 8.sp"))                                    // no per-key shrinking to cut text
    }
}
