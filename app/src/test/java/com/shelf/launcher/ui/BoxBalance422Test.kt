package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 422: a partly filled last box shares its cases over its rows (owner screenshot: 20 in the back row, 1 in front). */
class BoxBalance422Test {
    private val platforms = listOf("GBC", "GBA", "NDS", "PS2", "N64", "PSP", "3DS", "GameCube")
    private fun games(n: Int) = List(n) { i -> Game("Game $i", platforms[i % platforms.size], 0, SourceType.ROM, "/x/g$i.rom") }

    @Test fun lastBoxRowsAreBalanced() {
        for (n in listOf(5, 12, 21)) {
            val g = games(n); val cam = boxCam(1920f, 1080f, 0.5f, 1f, 2)
            val p = packBoxesFull(g, cam, 0.6f, false, bySize = false, dividers = false)
            val last = p.slots.maxOf { it.box }
            val inLast = p.slots.filter { it.box == last }
            val perRow = inLast.groupBy { it.row }.mapValues { it.value.size }
            if (inLast.size >= 6) {
                assertTrue("n=$n rows $perRow", perRow.size == 2)
                val counts = perRow.values.sorted()
                assertTrue("n=$n rows $perRow", counts.last() - counts.first() <= maxOf(2, inLast.size / 4))
            }
        }
    }
}
