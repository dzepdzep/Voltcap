package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 759a (matrix P5): the 17 category-screen looks, one source of truth (Features.PlatformView), each drawn by the
 *  category screen, explained in Settings under the name the picker shows, and the explanation names the keys that
 *  really move it (lookNavOf). Blades and Pocket Dashboard said ▲ ▼ while only ◀ ▶ stepped them. */
class CategoryLooksInventory759aTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p")
        .map(::File).first { it.exists() }.readText().replace("\r\n", "\n")

    private val looks = Features.PlatformView.values().toList()

    @Test fun thereAreExactlySeventeenLooks() {
        assertEquals(
            listOf("TILES", "SLIDES", "WALL", "GRID", "STAGE", "BLADES", "DRAWERS", "WORLDS", "ARCHIVE", "CHANNEL",
                "GENERATIONS", "CONSTELLATION", "VAULT", "DASHBOARD", "WIICHAN", "CINEMA", "ARCTIC"),
            looks.map { it.name })
    }

    @Test fun everyLookIsDrawnByTheCategoryScreen() {
        val browser = src("ui/PlatformBrowser.kt")
        // Tiles is the screen's own grid (the dispatch's else); every other look has its branch.
        for (v in looks - Features.PlatformView.TILES)
            assertTrue("no branch for ${v.name}", browser.contains("Features.PlatformView.${v.name} -> "))
    }

    @Test fun everyNoteStartsWithTheNameThePickerShows() {
        for (v in looks) {
            val note = categoryLookNote(v)
            val name = v.label.substringBefore(" —")
            assertTrue("${v.name}: '$note' should start with '$name:'", note.startsWith("$name:"))
        }
    }

    @Test fun theArrowsInANoteAreTheKeysThatMoveTheLook() {
        for (v in looks) {
            val note = categoryLookNote(v)
            val sideways = note.contains("◀ ▶"); val upDown = note.contains("▲ ▼")
            when (lookNavOf(v)) {
                LookNav.ROW -> assertFalse("${v.name} steps with ◀ ▶ but its note says ▲ ▼", upDown)
                LookNav.COLUMN -> assertFalse("${v.name} steps with ▲ ▼ but its note says ◀ ▶", sideways)
                LookNav.GRID -> if (sideways || upDown) assertTrue("${v.name} moves both ways", sideways && upDown)
            }
        }
        assertTrue(categoryLookNote(Features.PlatformView.BLADES).endsWith("◀ ▶."))
        assertTrue(categoryLookNote(Features.PlatformView.DASHBOARD).endsWith("◀ ▶."))
    }
}
