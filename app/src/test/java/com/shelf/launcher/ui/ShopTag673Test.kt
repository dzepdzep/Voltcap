package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 673a (D320): Rental shop's tag beside the held case (672b) is narrower - at 1.3x its three-across punched keys cut
 * CATALOGUE CARD / ACHIEVEMENTS ("CATALOGU", "ACHIEVEME" on the KONKR, "CATALOGUE" on the Flip 2; gate dumps k673b / k673c /
 * f673c, KONKR pickup recording pkk673d_13). The keys keep the tag's grid; a longer word takes two cells.
 */
class ShopTag673Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    // Every word fits its cell: one cell each, three across, the last row's empty cells kept empty - the block as designed.
    @Test fun everyWordFittingItsCellIsTheBlockAsBefore() = assertEquals(
        listOf(listOf(0 to 1, 1 to 1, 2 to 1), listOf(3 to 1, 4 to 1, -1 to 1)),
        tagKeyGrid(listOf(150, 120, 170, 160, 110), 552, 7, 3))

    // The KONKR x1.3 numbers (k673c: inner 552 px, 4 dp = 7 px, three cells of 179 px): CATALOGUE CARD 278 and ACHIEVEMENTS 231
    // take two cells each; OPTIONS / SAVE SYNC / INSPECT one. Order kept, no word cut, every row on the same three-cell grid.
    @Test fun konkrX13LongWordsTakeTwoCells() {
        val need = listOf(278, 151, 231, 177, 147)
        val rows = tagKeyGrid(need, 552, 7, 3)
        assertEquals(listOf(listOf(0 to 2, 1 to 1), listOf(2 to 2, 3 to 1), listOf(4 to 1, -1 to 2)), rows)
        assertEquals(need.indices.toList(), rows.flatten().map { it.first }.filter { it >= 0 })
        val cell = (552 - 7 * 2) / 3f
        rows.flatten().filter { it.first >= 0 }.forEach { (i, c) -> assertTrue("key $i", need[i] <= cell * c + 7 * (c - 1)) }
        rows.forEach { r -> assertEquals(3, r.sumOf { it.second }) }
    }

    // The Flip 2 x1.3 numbers (f673c: inner 1097 px, 10 px gaps, cells of 359 px): only CATALOGUE CARD is longer than a cell.
    @Test fun flip2X13OnlyCatalogueCardTakesTwoCells() = assertEquals(
        listOf(listOf(0 to 2, 1 to 1), listOf(2 to 1, 3 to 1, 4 to 1)),
        tagKeyGrid(listOf(400, 231, 346, 269, 226), 1097, 10, 3))

    // A narrow tag (two across): a word longer than its half has the row to itself.
    @Test fun twoAcrossALongWordTakesTheRow() = assertEquals(
        listOf(listOf(0 to 2), listOf(1 to 1, -1 to 1)), tagKeyGrid(listOf(300, 120), 400, 7, 2))

    // Wider than the whole row: a row of its own (the key grows to two lines in RentalTagCard), the next key on a new row.
    @Test fun aWordWiderThanTheRowHasItsOwnRow() = assertEquals(
        listOf(listOf(0 to 3), listOf(1 to 1, -1 to 2)), tagKeyGrid(listOf(900, 100), 552, 7, 3))

    @Test fun noKeysNoRows() = assertTrue(tagKeyGrid(emptyList(), 552, 7, 3).isEmpty())

    @Test fun unmeasuredWidthKeepsOneCellEach() = assertEquals(
        listOf(listOf(0 to 1, 1 to 1, -1 to 1)), tagKeyGrid(listOf(500, 500), 0, 7, 3))

    // (674e, owner brief 15 Nov: the tag's keys are one row of identical punched keys - Play first, not stretched across the
    // tag - that scrolls a key at a time; the grid above stays as a pure helper.)
    @Test fun theTagsKeysAreOneUniformRowPlayFirst() {
        val s = src("WinScenesConsole.kt")
        val i = s.indexOf("private fun RentalTagCard(")
        assertTrue(i > 0)
        val body = s.substring(i, s.indexOf("private fun tagKeyNeeds(", i))
        assertTrue(body.contains("UniformKeyRow(ctx.buttons.filter { it.primary } + rest, 4.dp) { a -> key(a, Modifier) }"))
        assertFalse("Play stretched across the tag", body.contains("key(a, Modifier.fillMaxWidth(), grow"))
        assertFalse("the grid of cells", body.contains("tagKeyGrid(need, innerPx"))
        assertFalse("the old fixed chunks", body.contains("rest.chunked(across)"))
        assertTrue(body.contains("val kh = if (h < 440.dp) 30.dp else 36.dp"))
    }
}
