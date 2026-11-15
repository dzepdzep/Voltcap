package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 672a (Flip 2 x1.3 popup gate f672e, shipped as known in v0.6.72): TV room's "Full details" cut in an equal third of the strip;
 * Record crate's facts box down to a 2 px sliver with More open; Record crate's PLAY smaller than SLEEVE NOTES.
 */
class CompactX13Fixes672Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    // Every word fits an equal share: three equal keys, exactly the look as before.
    @Test fun equalKeysWhileEveryWordFitsItsThird() =
        assertEquals(listOf(listOf(0 to 1f, 1 to 1f, 2 to 1f)), tvKeyRows(listOf(108, 120, 119), 556, 16))

    // The f672e numbers (x1.3, density x1.15): Play 108 / Full details 227 / Back 119 px in a 556 px strip. An equal third
    // (174 px) cut "Full details"; weighted by their words every key gets at least its word, all on one row.
    @Test fun wordWeightsWhenAThirdIsTooSmallButTheStripIsNot() {
        val need = listOf(108, 227, 119); val w = 556; val gap = 16
        val rows = tvKeyRows(need, w, gap)
        assertEquals(1, rows.size)
        val inner = w - gap * 2
        val total = rows[0].sumOf { it.second.toDouble() }
        rows[0].forEach { (i, wt) -> assertTrue("key $i", inner * wt / total >= need[i]) }
    }

    // Too narrow for the three words in one row: Play | Back over a whole-width Full details, Play first, nothing cut.
    @Test fun twoRowsPlayFirstWhenTheStripIsTooNarrow() {
        val rows = tvKeyRows(listOf(108, 227, 119), 340, 16)
        assertEquals(listOf(listOf(0, 2), listOf(1)), rows.map { r -> r.map { it.first } })
        assertEquals(0, rows[0][0].first)
    }

    @Test fun noKeysNoRows() = assertTrue(tvKeyRows(emptyList(), 300, 6).isEmpty())

    @Test fun theTvStripUsesTheWordWidths() {
        val s = src("GameWindow.kt")
        val body = s.substring(s.indexOf("private fun TvControls("), s.indexOf("private fun TvControls(") + 4000)
        assertTrue(body.contains("val rows = tvKeyRows(need, constraints.maxWidth, gapPx)"))
        assertTrue(body.contains("Modifier.weight(wt)"))
        assertTrue(!body.contains("Modifier.weight(1f).then(if (isPlay)"))
    }

    @Test fun crateTrackListNeverASliverAndNoRepeatedFacts() {
        val s = src("WinScenesMore.kt")
        val body = s.substring(s.indexOf("internal fun RecordSleeve("), s.indexOf("// ───────────────────────────── Card binder"))
        // the console and year are printed under the name: the list leaves them out
        assertTrue(body.contains("shownFacts(ctx, setOf(\"Console\", \"Year\"))"))
        assertTrue(!body.contains("sceneFacts(ctx).forEachIndexed"))
        // under one whole row's room the list gives its room away
        assertTrue(body.contains("if (c.hasBoundedHeight && c.maxHeight < minRow) layout(c.minWidth, c.minHeight) {}"))
        // Play at least as wide as Sleeve notes
        assertTrue(body.contains("minWidth = if (a.primary) playMin else Dp.Unspecified"))
        assertTrue(body.contains("keys.firstOrNull { PopupLab.isDetails(it) }?.let { sceneKeyWidth(it, 11.sp, null, upper = true) }"))
    }

    @Test fun shownFactsLeavesOutWhatTheSleeveSays() {
        val facts = listOf("Console" to "PlayStation 2", "Year" to "2003", "Played" to "Never played", "Trophies" to "8 / 10")
        assertEquals(listOf("Played" to "Never played", "Trophies" to "8 / 10"), shownFacts(facts, setOf("Console", "Year")))
    }
}
