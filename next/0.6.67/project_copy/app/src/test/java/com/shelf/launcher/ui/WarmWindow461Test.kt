package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 461 (D137): the case layouts decode a few cases ahead in the direction you move and let far ones go. */
class WarmWindow461Test {
    private val games = (0 until 40).map { Game("G$it", "gba", 0, SourceType.ROM, romPath = "/r/$it.gba") }

    @Test fun aheadInTheDirectionMoved() {
        val w = WarmWindow()
        assertEquals((7..13).toList(), w.indices(10, 6, 40, false)) // first: half either side
        assertEquals((10..17).toList(), w.indices(11, 6, 40, false)) // moved right: one behind, six ahead
        assertEquals((4..11).toList(), w.indices(10, 6, 40, false)) // moved left
        val l = WarmWindow()
        l.indices(39, 6, 40, true)
        assertEquals(listOf(39, 0, 1, 2, 3, 4, 5, 6), l.indices(0, 6, 40, true)) // right across the end of a looping row
        assertEquals(listOf(33, 34, 35, 36, 37, 38, 39, 0), l.indices(39, 6, 40, true)) // and back left across it
        assertTrue(w.indices(0, 6, 3, false).all { it in 0..2 })
    }

    @Test fun reachIsCapped() {
        assertEquals(6, WARM_REACH_MAX)
        val src = java.io.File(listOf("../src_copy/ui", "src/main/java/com/shelf/launcher/ui", "app/src/main/java/com/shelf/launcher/ui").first { java.io.File(it).exists() }, "RealMedia.kt").readText()
        assertTrue(src.contains("reach.coerceAtMost(WARM_REACH_MAX)"))
        assertTrue(src.contains("warmCase3d(context, models.map { it.second }, backs = false"))
    }

    @Test fun farPicturesAreLetGo() {
        val w = WarmWindow()
        w.remember("/r/1.gba", listOf("a1", "s1"))
        w.remember("/r/30.gba", listOf("a30", "shared"))
        w.remember("/r/31.gba", listOf("shared"))
        val keep = w.keepKeys(30, 20, games, false)
        assertEquals(listOf("a1", "s1"), w.release(keep))
        assertEquals(setOf("/r/30.gba", "/r/31.gba"), w.warmedGames())
        // a key a kept game still uses is never released
        val keep2 = w.keepKeys(31, 0, games, false)
        assertEquals(listOf("a30"), w.release(keep2))
    }
}
