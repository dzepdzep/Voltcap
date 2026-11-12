package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 527: Tile home's rows as one ordered stack (TileRows). */
class TileRowsTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText()
    private val all = TileRows.ALL.toSet()

    @Test fun anUntouchedHomeKeepsTheOrderItsSettingsGive() {
        // Middle / top: chips, tiles, facts, buttons under. Bottom: buttons above. "Under the row": chips under the tiles.
        assertEquals(listOf("cats", "tiles", "info", "buttons"), TileRows.defaultOrder(buttonsAbove = false, catsBelow = false))
        assertEquals(listOf("buttons", "cats", "tiles", "info"), TileRows.defaultOrder(buttonsAbove = true, catsBelow = false))
        assertEquals(listOf("tiles", "info", "cats", "buttons"), TileRows.defaultOrder(buttonsAbove = false, catsBelow = true))
        val d = TileRows.defaultOrder(false, false)
        assertEquals(d, TileRows.resolve("", d))
        // The gaps the old fixed layout had, for the default order.
        assertEquals(2f, TileRows.gapBefore("cats", "tiles", top = false, tileScale = 1f), 0f)
        assertEquals(18f, TileRows.gapBefore("tiles", "info", top = false, tileScale = 1f), 0f)
        assertEquals(8f, TileRows.gapBefore("info", "buttons", top = false, tileScale = 1f), 0f)
        assertEquals(2f, TileRows.gapBefore("tiles", "buttons", top = true, tileScale = 1f), 0f)
        assertEquals(4f, TileRows.gapBefore("info", "cats", top = false, tileScale = 1f), 0f)
        assertEquals(0f, TileRows.gapBefore("buttons", "cats", top = false, tileScale = 1f), 0f)
        assertEquals(0f, TileRows.gapBefore(null, "tiles", top = false, tileScale = 1f), 0f)
        assertEquals("buttons above, chips hidden: the name's room only", 0f, TileRows.gapBefore("buttons", "tiles", top = false, tileScale = 1f), 0f)
    }

    @Test fun aSavedOrderIsWholeAndStable() {
        val d = TileRows.defaultOrder(false, false)
        assertEquals(listOf("buttons", "tiles", "cats", "info"), TileRows.resolve("buttons,tiles,cats,info", d))
        // Unknown ids dropped, repeats once, a missing row at its default place (after the row it follows by default).
        assertEquals(listOf("tiles", "info", "cats", "buttons"), TileRows.resolve("tiles,bogus,cats,tiles,buttons", d))
        assertEquals(listOf("cats", "tiles", "info", "buttons"), TileRows.resolve(",,", d))
    }

    @Test fun movingARowSwapsItWithItsShownNeighbour() {
        val o = listOf("cats", "tiles", "info", "buttons")
        assertEquals(listOf("tiles", "cats", "info", "buttons"), TileRows.move(o, "tiles", -1, all))
        assertEquals(o, TileRows.move(o, "cats", -1, all)) // at the top: unchanged
        assertEquals(o, TileRows.move(o, "buttons", 1, all)) // at the bottom: unchanged
        // A hidden row between is stepped over (and keeps its place relative to the others).
        val shown = setOf("cats", "tiles", "buttons")
        assertEquals(listOf("cats", "info", "buttons", "tiles"), TileRows.move(o, "tiles", 1, shown))
        assertEquals(listOf("cats", "tiles", "buttons", "info"), TileRows.move(o, "buttons", -1, all))
    }

    @Test fun aDropLandsAtTheInsertionLine() {
        val spans = listOf(Triple("cats", 100f, 140f), Triple("tiles", 150f, 400f), Triple("info", 420f, 460f))
        assertEquals("cats", TileRows.dropBefore(spans, "buttons", 50f))
        assertEquals("tiles", TileRows.dropBefore(spans, "buttons", 200f))
        assertEquals("info", TileRows.dropBefore(spans, "buttons", 430f))
        assertNull(TileRows.dropBefore(spans, "buttons", 900f))
        val o = listOf("cats", "tiles", "info", "buttons")
        assertEquals(listOf("cats", "buttons", "tiles", "info"), TileRows.insertBefore(o, "buttons", "tiles"))
        assertEquals(listOf("cats", "tiles", "info", "buttons"), TileRows.insertBefore(o, "buttons", null))
        assertTrue(TileRows.buttonsAbove("buttons,cats,tiles,info", dockBottom = false, catsBelow = false))
        assertFalse(TileRows.buttonsAbove("", dockBottom = false, catsBelow = false))
        assertTrue("bottom dock: buttons above by default", TileRows.buttonsAbove("", dockBottom = true, catsBelow = false))
    }

    @Test fun editHomeMovesRowsInTheOrderAndKeepsItInTheSession() {
        val edit = src("ui/HomeEdit.kt")
        assertTrue(edit.contains("if (dy != 0) TileRowEdit.movableRow(id)?.let { row -> TileRowEdit.move(row, dy); return }"))
        // The round buttons in the stack move the same way; floating ones (a place of their own) don't.
        val rows = src("ui/TileRows.kt")
        assertTrue(rows.contains("HomeWidgets.position(HomeWidgets.SHORTCUTS) == null && HomeEditState.live[id] == null"))
        assertTrue(rows.contains("TileRows.BUTTONS -> HomeWidgets.SHORTCUTS"))
        // A row's ▲ ▼ is handled before the widget's own Undo step: a press at the top / bottom adds no empty step.
        val nudge = edit.substringAfter("fun nudge(id: String")
        assertTrue(nudge.indexOf("TileRowEdit.movableRow(id)") < nudge.indexOf("HomeEditSession.push(\"move:\$id\")"))
        val session = src("ui/HomeEditSession.kt")
        assertTrue(session.contains("\"tileRows\" to Features.tileRowOrder.value"))
        assertTrue(session.contains("TileRowEdit.resetRow(id)"))
        val ls = src("ui/LauncherScreen.kt")
        // The tiles' column draws its rows in the order; the D-pad reaches the buttons the way the order puts them.
        assertTrue(ls.contains("rowsShown.forEachIndexed { idx, rowId ->"))
        assertTrue(ls.contains("val buttonsAbove = TileRows.buttonsAbove("))
        // Nothing animates outside Edit Home (rule 14).
        assertTrue(src("ui/TileRows.kt").contains("translationY = if (editing) anim.value else 0f"))
    }

    @Test fun aMovedOrderStaysClearOfTheStatusBarInTheMiddle() {
        val ls = src("ui/LauncherScreen.kt")
        assertTrue(ls.contains("val tileOrderMoved = tileOrder != tileOrderDefault"))
        assertTrue(ls.contains("val yFloor = if (tileOrderMoved) TileRows.TOP_CLEAR_DP.dp.roundToPx() else 0"))
        assertEquals(40, TileRows.TOP_CLEAR_DP)
    }

    @Test fun aRowsHeightComesFromTheOrderOnly() {
        // (Owner, Sprint 527: one section-ordering and positioning system, no arbitrary offsets.)
        val edit = src("ui/HomeEdit.kt")
        assertTrue(edit.contains("TileRowEdit.rowOf(id) != null && TileRowEdit.dragging != id) s.first to 0f else s"))
        // A finger drag of a row ends in the order (the insertion line meanwhile); the buttons' drop joins it at once.
        assertTrue(edit.contains("if (rowDrag && TileRowEdit.dragging == id) TileRowEdit.dropRow(id)"))
        assertTrue(edit.contains("if (id == HomeWidgets.SHORTCUTS) TileRowEdit.dropButtons()"))
        val rows = src("ui/TileRows.kt")
        assertTrue(rows.contains("HomeEditState.live.remove(HomeWidgets.SHORTCUTS)"))
        assertTrue(rows.contains("if (partId == HomeWidgets.SHORTCUTS && HomeEditState.layout == com.shelf.launcher.data.HomeLayout.TILES) TileRows.BUTTONS else null"))
        assertTrue(src("ui/LauncherScreen.kt").contains("TileRowEdit.rowDropLine.takeIf { HomeEditState.active && TileRowEdit.dragging != null }"))
        // The Edit Home bar keeps clear of the selected part as it moves.
        assertTrue(src("ui/HomeEditLegend.kt").contains("if (want != atTop) atTop = want"))
    }

    @Test fun inEditHomeTheFloatingButtonsSitOnTheirRow() {
        // (Flip 2 t527e: the buttons moved to the top of the order still floated at the screen's bottom in Edit Home.)
        val ls = src("ui/LauncherScreen.kt")
        assertTrue(ls.contains("defaultCentre = if (tileLayout && HomeEditState.active) TileRowEdit.buttonsCentre else null"))
        assertTrue(ls.contains("if (TileRowEdit.buttonsCentre != it) TileRowEdit.buttonsCentre = it"))
        assertTrue(src("ui/HomeEdit.kt").contains("val f = stored ?: clearOf ?: centreFrac ?: defaultFrac ?: defaultFractions(defaultAlign)"))
    }
}
