package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 424: Cardboard box cases never intersect each other, the walls or the next row; resting cases are drawn at their slot. */
class BoxSolid424Test {
    private val platforms = listOf("N64", "GB", "GBC", "GBA", "NDS", "PS1", "PS2", "PSP", "3DS", "GameCube", "SNES", "Switch")
    private fun games(n: Int, mix: List<String> = platforms) = List(n) { i -> Game("Game $i", mix[(i * 7) % mix.size], 0, SourceType.ROM, "/x/g$i.rom") }

    private fun checkPack(g: List<Game>, rows: Int) {
        val cam = boxCam(1920f, 1080f, 0.5f, 1f, rows)
        val p = packBoxesFull(g, cam, 0.6f, false, bySize = false, dividers = false)
        val eps = 0.01f
        for (sl in p.slots) {
            assertTrue("inside the left wall", sl.x0 >= -cam.wMm / 2f - eps)
            assertTrue("inside the right wall", sl.x1 <= cam.wMm / 2f + eps)
            assertTrue("inside the back wall", sl.y0 >= -eps)
            assertTrue("inside the front wall", sl.y1 <= cam.dMm + eps)
            assertTrue("below the rim", sl.stand < cam.wallH)
        }
        // Same box and row: side by side, never overlapping (by their real thickness).
        for ((_, list) in p.slots.groupBy { it.box to it.row }) {
            val sorted = list.sortedBy { it.x0 }
            for (i in 1 until sorted.size) assertTrue("cases overlap: ${sorted[i - 1].x1} > ${sorted[i].x0}", sorted[i - 1].x1 <= sorted[i].x0 + eps)
        }
        // Rows of one box never reach into each other.
        for ((_, list) in p.slots.groupBy { it.box }) {
            val byRow = list.groupBy { it.row }
            for (r in 1 until rows) {
                val a = byRow[r - 1] ?: continue; val b = byRow[r] ?: continue
                assertTrue("row $r reaches into row ${r - 1}", a.maxOf { it.y1 } <= b.minOf { it.y0 } + eps)
            }
        }
    }

    @Test fun crowdedPartlyFilledAndNearlyEmptyMixedBoxes() {
        for (n in listOf(1, 3, 9, 25, 60, 140)) for (rows in 1..3) checkPack(games(n), rows)
        checkPack(games(40, listOf("N64", "GB")), 2)
        checkPack(games(40, listOf("PS2", "PSP", "NDS")), 2)
    }

    @Test fun case3DFitsItsSlot() {
        // Case3D's own camera draws a spine-on case 3.4 / (3.4 - aspect / 2) times its room: the room is that much smaller.
        for (a in listOf(0.55f, 0.71f, 1f, 1.39f)) assertEquals(1f, boxCaseFit(a) * 3.4f / (3.4f - a / 2f), 0.001f)
    }

    @Test fun noWashOverRestingCases() {
        val s = File(listOf("src/main/java/com/shelf/launcher/ui/StyleCardboardBox.kt", "app/src/main/java/com/shelf/launcher/ui/StyleCardboardBox.kt").first { File(it).exists() }).readText()
        val body = s.substring(s.indexOf("private fun BoxCase("), s.indexOf("private fun smoothUp"))
        assertTrue("no dimming of the other cases", body.contains("shadeOf = { 0f }"))
        assertTrue("no warm sheen", !body.contains("0xFFFFF3D2"))
    }
}
