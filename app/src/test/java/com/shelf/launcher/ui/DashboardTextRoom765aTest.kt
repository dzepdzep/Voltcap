package com.shelf.launcher.ui

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 765a (P5, Pocket Dashboard): the preview's text column (facts, name, counts, last played) stays inside the rounded preview
 * panel whatever the text size. KONKR 960 x 640 at density 1.5 = 640 x 427 dp; the preview is 46 % of the height.
 */
class DashboardTextRoom765aTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText().replace("\r\n", "\n")

    private val previewH = 427f * 0.46f
    // A one-line text at ~1.35 em (trimmed line height); the two 10 dp spacers; the counts are a 22 sp over an 11 sp line.
    private fun others(scale: Float) = 11f * scale * 1.35f + 10f + (22f + 11f) * scale * 1.35f + 10f + 12f * scale * 1.35f
    private fun name(scale: Float, lines: Int, sp: Float) = sp * scale * 1.35f + (lines - 1) * sp * scale * 1.133f

    @Test fun theRoomIsThePreviewLessItsPadding() {
        val room = dashboardTextRoom(previewH.dp).value
        assertEquals(previewH - 2 * DASH_PAD_V, room, 0.01f)
        // the case the old 26 dp padding, fixed 30 sp name and unbounded column got wrong: a two-line name at x1.0 (and
        // any name at x1.3) overflowed the 144 dp it had
        val oldRoom = previewH - 2 * 26f
        assertTrue("old x1.0 ${others(1f) + name(1f, 2, 30f)} > $oldRoom", others(1f) + name(1f, 2, 30f) > oldRoom)
        assertTrue("old x1.3 ${others(1.3f) + name(1.3f, 1, 30f)} > $oldRoom", others(1.3f) + name(1.3f, 1, 30f) > oldRoom)
    }

    @Test fun theNameSizeLeavesEveryLineWhole() {
        val room = dashboardTextRoom(previewH.dp)
        for (scale in listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f)) {
            val sp = dashboardNameSp(room, scale)
            // even a name that slides along one line (at this size) is whole in height beside the other lines
            if (sp > 18f) assertTrue("x$scale $sp sp: ${others(scale) + name(scale, 1, sp)} <= ${room.value}", others(scale) + name(scale, 1, sp) <= room.value + 0.01f)
            assertTrue(sp in 18f..30f)
        }
        // the default size is untouched where there is room: KONKR x1.0, and the Thor's top screen (~960 x 540 dp) at x1.3
        assertEquals(30f, dashboardNameSp(room, 1f), 0.001f)
        assertEquals(30f, dashboardNameSp(dashboardTextRoom((540f * 0.46f).dp), 1.3f), 0.001f)
        // KONKR x1.3 gets a smaller name, not the readable minimum
        assertTrue(dashboardNameSp(room, 1.3f) in 22f..27f)
    }

    @Test fun theColumnIsBoundedAndTheNameGivesWayFirst() {
        val l = src("ui/CategoryLooks.kt")
        assertTrue(l.contains("Row(Modifier.fillMaxSize().padding(horizontal = DASH_PAD_H.dp, vertical = DASH_PAD_V.dp), verticalAlignment = Alignment.CenterVertically) {\n                Column(Modifier.weight(1f).heightIn(max = dashboardTextRoom(previewH))) {"))
        // the name: weighted (measured last), line height follows the shrinking size
        assertTrue(l.contains("fontWeight = FontWeight.Black, lineHeight = 1.133.em, maxLines = 2, modifier = Modifier.weight(1f, fill = false))"))
        // the facts keep one line (FullText: they slide rather than grow the column)
        assertTrue(l.contains("FullText(lookFacts(t)?.uppercase() ?: if (t.platform == null) \"LIST\" else \"\", color = accent, fontSize = 11.sp, letterSpacing = 2.sp)"))
        assertTrue(l.contains("FullText(t.label, color = Color.White, fontSize = nameSp.sp,"))
        assertTrue(!l.contains("Row(Modifier.fillMaxSize().padding(26.dp), verticalAlignment = Alignment.CenterVertically)"))
    }
}
