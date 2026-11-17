package com.shelf.launcher.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 674e, owner decision 15 Nov (Record crate at x1.3, option A): a deliberate vertical menu where the row showed one key at a time. */
class CrateVertical674Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun aSleeveThatHoldsTwoKeysKeepsItsRow() =
        assertFalse(crateKeysVertical(widest = 150.dp, row = 428.dp, gap = 6.dp, gutter = 16.dp))   // x1.0: 306 <= 396

    @Test fun aSleeveThatHoldsOneKeyStandsItsKeysUp() =
        assertTrue(crateKeysVertical(widest = 190.dp, row = 323.dp, gap = 6.dp, gutter = 16.dp))    // x1.3: 386 > 291

    @Test fun wholeKeysLeaveTheTrackListTwoRows() {
        assertEquals(3, crateVisibleKeys(region = 220.dp, keyH = 34.dp, gap = 6.dp, facts = 46.dp, n = 5))   // room for three
        assertEquals(2, crateVisibleKeys(region = 140.dp, keyH = 34.dp, gap = 6.dp, facts = 46.dp, n = 5))   // KONKR x1.3: two
        assertEquals(2, crateVisibleKeys(region = 60.dp, keyH = 34.dp, gap = 6.dp, facts = 46.dp, n = 5))    // never fewer than two
        assertEquals(1, crateVisibleKeys(region = 220.dp, keyH = 34.dp, gap = 6.dp, facts = 46.dp, n = 1))
    }

    @Test fun onlyTheCrateSleeveTurnsVertical() {
        val s = src("WinScenesMore.kt")
        val body = s.substring(s.indexOf("internal fun RecordSleeve("), s.indexOf("internal fun crateKeysVertical("))
        assertTrue(body.contains("if (crateKeysVertical(widest, side - 32.dp, 6.dp, KEY_ROW_GUTTER))"))
        assertTrue(body.contains("UniformKeyColumn(actions, 6.dp, fill = true)"))
        assertTrue(body.contains("else Box(Modifier.sceneFadeEdges(keyScroll).verticalScroll(keyScroll))"))
        // no other scene calls the crate's rule
        val all = File(File(listOf("src/main/java/com/shelf/launcher/ui", "app/src/main/java/com/shelf/launcher/ui").first { File(it).exists() }).path)
            .listFiles()!!.filter { it.name.endsWith(".kt") }.sumOf { f -> Regex("crateKeysVertical\\(").findAll(f.readText()).count() }
        assertTrue("crateKeysVertical used outside Record crate", all == 2)  // its definition and the sleeve
    }
}
