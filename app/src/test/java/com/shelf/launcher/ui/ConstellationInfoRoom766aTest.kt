package com.shelf.launcher.ui

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 766a (P5, Constellation): the info column of the system in the light never grows into the legend, whatever the text size;
 * the name gives way first. KONKR 960 x 640 at density 1.5 = 640 x 427 dp.
 */
class ConstellationInfoRoom766aTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText().replace("\r\n", "\n")

    private val h = 427f
    // name 24 sp per line, facts 13 sp per line, favourites 12 sp, ~1.35 em each, 4 dp spacer
    private fun need(scale: Float, nameLines: Int, factLines: Int, fav: Boolean = true, nameSp: Float = 24f) =
        (nameLines * nameSp + factLines * 13f + (if (fav) 12f else 0f)) * scale * 1.35f + 4f

    @Test fun theRoomEndsAboveTheLegend() {
        val room = constellationInfoRoom(h.dp).value
        assertEquals(h * (1f - CONST_INFO_TOP) - GEN_INFO_BOTTOM.value, room, 0.01f)
        // not squeezed at the default size: a one-line name, one facts line and favourites fit whole
        assertTrue("x1.0 ${need(1f, 1, 1)} <= $room", need(1f, 1, 1) <= room)
        // the case the old unbounded column got wrong: x1.3, a two-line name, facts wrapped, favourites - ~55 dp into the legend
        assertTrue("x1.3 ${need(1.3f, 2, 2)} > $room", need(1.3f, 2, 2) - room > 50f)
        // at x1.3 the facts (two lines) and favourites stay whole and leave the name one line at its readable minimum
        val nameMin = fitMin(24f.sp()).value
        assertTrue(need(1.3f, 1, 2, nameSp = nameMin) <= room)
    }

    @Test fun theColumnStartsUnderTheObjectInTheLight() {
        // the focused object's box: top 0.42h - 0.18h, 0.36h high -> foot at 0.60h
        assertTrue(CONST_INFO_TOP > 0.42f - 0.18f + 0.36f)
        // a very short look keeps the minimum (a name line at its minimum + a facts line)
        assertEquals(CONST_INFO_MIN.value, constellationInfoRoom(240.dp).value, 0.01f)
        assertTrue(CONST_INFO_MIN.value >= (14.4f + 13f) * 1.35f + 4f)
    }

    @Test fun theColumnIsBoundedTheNameGivesWayFirstAndTapsOpen() {
        val l = src("ui/CategoryLooks.kt")
        val look = l.substringAfter("internal fun ConstellationLook(").substringBefore("// 9. System Vault")
        assertTrue(look.contains(".offset(w * 0.58f, h * CONST_INFO_TOP).width(w * 0.36f)\n                    .heightIn(max = constellationInfoRoom(h))"))
        assertTrue(look.contains("maxLines = 2, reserve = false,\n                    modifier = Modifier.weight(1f, fill = false))"))
        // facts and favourites through FullText (no plain clipping Text left in the info column)
        assertTrue(look.contains("FullText(listOfNotNull(lookCount(t), lookFacts(t))"))
        assertTrue(!look.contains("Text(\"\${t.favorites} favourites\""))
        // touch: the object in the light and its info open the system
        assertEquals(2, Regex("""\.lookTap\(selected, selected, onFocus, onPick\)""").findAll(look).count())
    }

    private fun Float.sp() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
}
