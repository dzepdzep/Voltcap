package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 453 (Thor, Tile home preview): the pill category bar still listed "Other apps", a tile the owner took off the
 * category screen — L2 / R2 and the category screen skip it (20 stops = 20 tiles), the bar showed 21. The bar now takes the
 * same list (data/CategoryStops.kt). Behaviour of the list itself: CategoryStopsTest.
 */
class CategoryBar453Test {
    private fun src(name: String): String {
        val roots = listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/")
        return roots.map { File(it + name) }.first { it.exists() }.readText().replace("\r\n", "\n")
    }

    @Test fun pillBarUsesTheCategoryStops() {
        val ls = src("LauncherScreen.kt")
        val call = ls.substring(ls.indexOf("CategoryBar.FULL -> PlatformFilterRow("), ls.indexOf("CategoryBar.FULL -> PlatformFilterRow(") + 700)
        assertTrue(call.contains("com.shelf.launcher.data.CategoryStops.keys(state)"))
        assertTrue("re-worked when a tile is taken off / put back", call.contains("CategoryTileOrder.version.value"))
        val hc = src("HomeChrome.kt")
        assertTrue(hc.contains("keys?.distinct() ?: buildList {"))
    }
}

/** Update 453 (Thor): the app drawer starts on its first app and the app you're on shows its whole name (rule 11). */
class DrawerFocus453Test {
    @Test fun drawerStartsOnTheFirstAppAndShowsWholeNameOnFocus() {
        val roots = listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/")
        val s = roots.map { File(it + "AppDrawer.kt") }.first { it.exists() }.readText().replace("\r\n", "\n")
        assertTrue(s.contains("if (app === sorted.first()) Modifier.initialFocus() else Modifier"))
        assertTrue(s.contains("if (focused) FullText(\n            app.name"))
    }
}
