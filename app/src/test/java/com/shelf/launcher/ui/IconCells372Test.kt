package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 372: small icon cells show the plain icon (not card art), and a baked tint is not re-made on every frame of an accent glide. */
class IconCells372Test {
    private fun src(name: String): String {
        val roots = listOf("src/main/java/com/shelf/launcher/ui/", "app/src/main/java/com/shelf/launcher/ui/")
        return roots.map { File(it + name) }.first { it.exists() }.readText()
    }

    @Test fun secondScreenAppGridAndDockOwnColoursUseThePlainIcon() {
        val grid = src("SecondScreenHome.kt")
        assertTrue(grid.contains("PlainAppIcon(app.packageName, Modifier.size(48.dp))"))
        assertFalse(grid.contains("AppIconArt(app.packageName"))
        val dock = src("SecondDockShortcuts.kt")
        assertTrue(dock.contains("PlainAppIcon(id, modifier)"))
        assertFalse(dock.contains("AppIconArt("))
    }

    @Test fun themedIconTintFollowsTheSettledAccentNotTheGlide() {
        val icons = src("AppIcons.kt")
        val themed = icons.substring(icons.indexOf("fun rememberThemedAppIcon("), icons.indexOf("internal fun themedStyle("))
        assertTrue(themed.contains("rememberSettledAccent()"))
        assertFalse(themed.contains("ShelfTheme.accent"))
        val settled = icons.substring(icons.indexOf("fun rememberSettledAccent("), icons.indexOf("private const val SETTLE_MS"))
        assertTrue(settled.contains("snapshotFlow") && settled.contains("debounce"))
        assertTrue(settled.contains("ShelfTheme.live"))
    }
}
