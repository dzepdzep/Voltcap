package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 461 popup audit: Row reveal / Shared move shade every layout whose own words stay under the window. */
class PopupShade461Test {
    @Test fun shadedLayouts() {
        for (l in listOf(HomeLayout.HERO, HomeLayout.GRID, HomeLayout.TV, HomeLayout.CUBE, HomeLayout.FILESELECT, HomeLayout.SHOP, HomeLayout.CINEMA, HomeLayout.ARCTIC))
            assertTrue(l.name, WindowShade.on(l))
        // the classic rows step aside themselves (RowRevealShade); untouched
        assertFalse(WindowShade.on(HomeLayout.CLASSIC))
        assertTrue(WindowShade.ALPHA >= 0.9f)
    }

    @Test fun bothWindowsUseIt() {
        fun src(n: String) = java.io.File(listOf("../src_copy/ui", "src/main/java/com/shelf/launcher/ui", "app/src/main/java/com/shelf/launcher/ui").first { java.io.File(it).exists() }, n).readText()
        assertTrue(src("RowRevealWindow.kt").contains("if (WindowShade.on(state.appearance.homeLayout))"))
        assertTrue(src("GlanceWindow.kt").contains("if (WindowShade.on(state.appearance.homeLayout))"))
    }
}
