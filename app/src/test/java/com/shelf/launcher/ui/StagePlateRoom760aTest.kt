package com.shelf.launcher.ui

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 760a (P5, System Stage): the museum plate on a plinth never rises over the plinth's top face and the object standing on it,
 * whatever the text size. KONKR 960 x 640 at density 1.5 = 640 x 427 dp; the plinth is 26 % of the height.
 */
class StagePlateRoom760aTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText().replace("\r\n", "\n")

    private val plH = 427f * 0.26f
    private val topFace = plH * 0.18f
    // A 14 sp / 10 sp line at ~1.35 em, the plate's 2 x 8 dp padding and the 3 dp gap.
    private fun need(scale: Float, nameLines: Int, factLines: Int) = 16f + 3f + nameLines * 14f * scale * 1.35f + factLines * 10f * scale * 1.35f

    @Test fun theRoomEndsAtTheObjectsFoot() {
        val room = stagePlateRoom(plH.dp, topFace.dp).value
        val foot = plH - topFace * STAGE_FOOT_SINK          // the object's bottom, above the box's bottom
        assertEquals(foot, room + plH * STAGE_PLATE_BOTTOM, 0.01f)
        // the plate is not squeezed at the default size: a one-line name and two lines of facts fit whole
        assertTrue("x1.0 ${need(1f, 1, 2)} <= $room", need(1f, 1, 2) <= room)
        // the case the old unbounded plate got wrong: x1.3, a two-line name over two lines of facts
        assertTrue("x1.3 ${need(1.3f, 2, 2)} > $room", need(1.3f, 2, 2) > room)
        // the facts keep their two lines and still leave the name a line at its readable minimum (10 sp)
        assertTrue(room - (16f + 3f + 2 * 10f * 1.3f * 1.35f) >= 10f * 1.3f * 1.35f)
    }

    @Test fun thePlateIsBoundedAndTheNameGivesWayFirst() {
        val l = src("ui/CategoryLooks.kt")
        assertTrue(l.contains(".width(plW * 0.80f)\n                        .heightIn(max = stagePlateRoom(plH, topFace))"))
        assertTrue(l.contains("maxLines = 2, reserve = false, modifier = Modifier.weight(1f, fill = false))"))
        assertTrue(l.contains(".padding(bottom = plH - topFace * STAGE_FOOT_SINK).size(objW, objH), stand = true)"))
        // the facts keep their two lines and no weight (measured first)
        assertTrue(l.contains("letterSpacing = 1.sp,\n                        maxLines = 2, reserve = false\n                    )"))
    }
}
