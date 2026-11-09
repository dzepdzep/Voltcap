package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 493 (staging only): D137 — Material's colour scheme no longer changes with every game while colours follow the art. */
class Batch493Test {
    private fun src(rel: String): String = listOf(
        File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")
    ).first { it.exists() }.readText()

    @Test fun schemeIsHandedOnOnceTheArtColoursSettle() {
        val t = src("ui/theme/Theme.kt")
        assertEquals(900L, com.shelf.launcher.ui.theme.SCHEME_SETTLE_MS)
        assertTrue(t.contains("val followArt = useGame && artDriven"))
        assertTrue(t.contains("if (followArt) delay(SCHEME_SETTLE_MS)"))
        assertTrue(t.contains("val scheme = if (followArt) heldScheme else schemeNow"))
        // MaterialTheme gets the held scheme; Voltcap's own colours still glide at once.
        assertTrue(t.contains("MaterialTheme(colorScheme = scheme, typography = typography, content = content)"))
        assertTrue(t.contains("LocalShelfColors provides colors"))
    }

    /** The accent-coloured Material controls take ShelfTheme's accent, not the scheme's, so the held scheme doesn't show. */
    @Test fun accentControlsUseShelfColours() {
        val d = src("ui/DialogChrome.kt")
        assertTrue(d.contains("checkedTrackColor = ShelfTheme.accent"))
        assertTrue(d.contains("focusedBorderColor = ShelfTheme.accent"))
        val sl = src("ui/SettingsSlider.kt")
        assertTrue(sl.contains("thumbColor = ShelfTheme.accent") && sl.contains("activeTrackColor = ShelfTheme.accent"))
    }
}
