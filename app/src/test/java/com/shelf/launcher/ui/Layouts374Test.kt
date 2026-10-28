package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 374: the two new layouts are registered once and reuse the existing pieces; the status labels carry no fixed cream palette. */
class Layouts374Test {
    private fun src(name: String): String {
        val roots = listOf("src/main/java/com/shelf/launcher/", "app/src/main/java/com/shelf/launcher/")
        return roots.map { File(it + name) }.first { it.exists() }.readText()
    }

    @Test fun newLayoutsAreStyleHomesAndPickable() {
        for (l in listOf(HomeLayout.COVERDETAILS, HomeLayout.ARTLIB)) {
            assertTrue(l.name, l.styleHome)
            assertTrue(l.name, l in HomeLayout.pickable)
        }
        assertEquals(HomeLayout.values().size, HomeLayout.values().map { it.name }.toSet().size)
    }

    @Test fun coverflowReusesTheExistingCaseAndSpinePieces() {
        val s = src("ui/StyleCoverDetails.kt")
        assertTrue(s.contains("Case3D(") && s.contains("GameSpine(") && s.contains("case3dAspect(") && s.contains("shelfSpineAspect("))
        assertTrue(s.contains("PickedFacing") && s.contains("rememberLoopFocus") && s.contains("rememberGlidePos"))
        // No second 3D engine, no new legend.
        assertFalse(s.contains("rotationX") || s.contains("Legend"))
    }

    @Test fun artLibraryLaunchesThroughTheExistingActionsAndShowsNoLegend() {
        val s = src("ui/StyleArtLibrary.kt")
        assertTrue(s.contains("c.actions.play(") && s.contains("c.actions.open()"))
        assertFalse(s.contains("GameLauncher") || s.contains("Legend"))
        assertFalse(s.contains("TextOverflow.Ellipsis"))
    }

    @Test fun shelfAndStacksLabelsComeFromTheTheme() {
        val s = src("ui/StatusLooks.kt")
        assertFalse(s.contains("0xEBE9E2D2") || s.contains("0xF2EFE6D2") || s.contains("0xF2F6F8FB"))
        val shelf = s.substring(s.indexOf("HomeLayout.SHELF, HomeLayout.STACKS ->"), s.indexOf("// Console stack: a console's front panel"))
        assertTrue(shelf.contains("themedLabel("))
    }
}
