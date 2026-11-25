package com.shelf.launcher.ui

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 761a (P5, Generations): the info block of the system you're on never rises into the handheld lane, whatever the text size.
 * KONKR 960 x 640 at density 1.5 = 640 x 427 dp.
 */
class GenerationsInfoRoom761aTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText().replace("\r\n", "\n")

    private val w = 640f; private val h = 427f
    // generation line 11 sp, name 24 sp per line, facts 12 sp per line, ~1.35 em each
    private fun need(scale: Float, nameLines: Int, factLines: Int) = (11f + nameLines * 24f + factLines * 12f) * scale * 1.35f

    @Test fun theRoomEndsAtTheHandheldLanesFoot() {
        val room = generationsInfoRoom(w.dp, h.dp).value
        val objW = w * GEN_YEAR_W * 1.05f
        val handFoot = h * GEN_AXIS + 24f + (objW / 1.8f + 20f) * 1.15f
        assertEquals(h - GEN_INFO_BOTTOM.value - handFoot, room, 0.01f)
        // the focused handheld's year line at x1.3 (11 sp) fits the 20 dp allowance
        assertTrue(11f * 1.3f * 1.35f <= 20f)
        // not squeezed at the default size: a one-line name with one line of facts fits whole
        assertTrue("x1.0 ${need(1f, 1, 1)} <= $room", need(1f, 1, 1) <= room)
        // the case the old unbounded block got wrong: x1.3, a two-line name
        assertTrue("x1.3 ${need(1.3f, 2, 1)} > $room", need(1.3f, 2, 1) > room)
        // the generation line and the facts stay whole at x1.3 and leave the name a line at its readable minimum (10 sp)
        assertTrue(room - (11f + 12f) * 1.3f * 1.35f >= 10f * 1.3f * 1.35f)
    }

    @Test fun aShortScreenKeepsTheMinimum() {
        // a 20:9 phone in landscape: the lane leaves less than the minimum - the block keeps its three lines
        assertEquals(GEN_INFO_MIN.value, generationsInfoRoom(820.dp, 360.dp).value, 0.01f)
    }

    @Test fun theBlockIsBoundedAndTheNameGivesWayFirst() {
        val l = src("ui/CategoryLooks.kt")
        assertTrue(l.contains(".padding(bottom = GEN_INFO_BOTTOM).width(w * 0.6f)\n                    .heightIn(max = generationsInfoRoom(w, h)),"))
        assertTrue(l.contains("maxLines = 2, reserve = false, modifier = Modifier.weight(1f, fill = false))\n                FullText(listOfNotNull(lookFacts(t), lookCount(t))"))
        assertTrue(l.contains("val yearW = w * GEN_YEAR_W") && l.contains("val axisY = h * GEN_AXIS"))
    }
}
