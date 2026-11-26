package com.shelf.launcher.ui

import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 722a (Task 1): the Edit Home / preset preview places the row, its name and the status bar as the real Home does. */
class PreviewDock722aTest {
    @Test fun statusBarMovesToTheBottomOnlyWhereTheRealHomeMovesIt() {
        assertTrue(PreviewDock.statusAtBottom(HomeLayout.CLASSIC, DockPosition.TOP))
        assertTrue(PreviewDock.statusAtBottom(HomeLayout.TILES, DockPosition.TOP))
        assertFalse(PreviewDock.statusAtBottom(HomeLayout.CLASSIC, DockPosition.MIDDLE))
        assertFalse(PreviewDock.statusAtBottom(HomeLayout.CLASSIC, DockPosition.BOTTOM))
        assertFalse(PreviewDock.statusAtBottom(HomeLayout.GRID, DockPosition.TOP))
        assertFalse(PreviewDock.statusAtBottom(HomeLayout.TV, DockPosition.TOP))
        HomeLayout.values().filter { it.styleHome }.forEach { assertFalse(it.name, PreviewDock.statusAtBottom(it, DockPosition.TOP)) }
        // above the legend when it is shown, never inside it (legend band 0.93..1)
        assertTrue(PreviewDock.statusY(true, true) + 0.02f <= 0.93f)
        assertEquals(0.05f, PreviewDock.statusY(false, true), 0f)
    }

    @Test fun topPutsTheRowUnderTheEdgeAndTheNameUnderTheRow() {
        val ch = 0.3f
        val y = PreviewDock.rowTop(DockPosition.TOP, ch, 0.92f, PreviewDock.CLASSIC_TOP)
        assertTrue("row right under the edge (old preview 0.2)", y < 0.1f)
        val name = PreviewDock.nameY(DockPosition.TOP, y, ch, 0.1f)
        assertTrue("name under the row", name >= y + ch)
        // the status bar at the bottom stays clear of the row and its name
        assertTrue(name + 0.035f < PreviewDock.statusY(true, true))
        // Tile home: 60 dp of ~360 dp
        assertEquals(0.17f, PreviewDock.rowTop(DockPosition.TOP, 0.32f, 0.74f, PreviewDock.TILES_TOP), 0f)
    }

    @Test fun middleAndBottomUnchanged() {
        assertEquals(0.35f, PreviewDock.rowTop(DockPosition.MIDDLE, 0.3f, 0.92f, PreviewDock.CLASSIC_TOP), 1e-6f)
        assertEquals(0.62f, PreviewDock.rowTop(DockPosition.BOTTOM, 0.3f, 0.92f, PreviewDock.CLASSIC_TOP), 1e-6f)
        assertEquals(0.25f, PreviewDock.nameY(DockPosition.MIDDLE, 0.35f, 0.3f, 0.1f), 1e-6f)
        assertEquals(0.52f, PreviewDock.nameY(DockPosition.BOTTOM, 0.62f, 0.3f, 0.1f), 1e-6f)
    }

    @Test fun previewUsesTheHelperNotItsOldFixedTop() {
        val src = File("src/main/java/com/shelf/launcher/ui/PresetPreview.kt").let { if (it.exists()) it else File("app/" + it.path) }.readText()
        assertFalse(src.contains("DockPosition.TOP -> 0.2f"))
        assertTrue(src.contains("PreviewDock.statusAtBottom(layout, dock)"))
    }
}
