package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 751a (CLAUDE.md rule 11, QA R18 / matrix P13): the save inspector's hint and object name, the save-sync badge and
 * status line, the Steam import rows and the RomM browser's tiles / dialog title show their texts whole (FullText: wrap,
 * readable size, then slide). Fails on the 750a sources (8 Ellipsis uses in these four files).
 */
class SaveImportFullText751aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    private val files = listOf("SaveInspect.kt", "SaveSyncUi.kt", "SteamImportDialog.kt", "RommBrowser.kt")

    @Test fun noEllipsis() {
        for (f in files) {
            val bad = source(f).lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
            assertEquals("$f unjustified ellipses: $bad", 0, bad.size)
        }
    }

    @Test fun textsUseFullText() {
        val inspect = source("SaveInspect.kt")
        assertTrue("inspect hint", Regex("""FullText\(\s*"Drag: turn""").containsMatchIn(inspect))
        assertTrue("inspect object name", Regex("""FullText\(\s*\(obj\?\.label""").containsMatchIn(inspect))
        val sync = source("SaveSyncUi.kt")
        assertTrue("sync badge", Regex("""FullText\(\s*syncBadgeLabel\(status\)""").containsMatchIn(sync))
        assertTrue("sync status", Regex("""FullText\(\s*when \{\s*sync\.authProblem""").containsMatchIn(sync))
        val steam = source("SteamImportDialog.kt")
        assertTrue("steam name", Regex("""FullText\(\s*c\.name""").containsMatchIn(steam))
        assertTrue("steam source", steam.contains("FullText(\n                \"\$detail · \${c.source}\""))
        val romm = source("RommBrowser.kt")
        assertTrue("romm tile keeps two lines of room", romm.contains("FullText(rom.name, color = ShelfTheme.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)"))
        assertTrue("romm title", romm.contains("FullText(title, color = ShelfTheme.colors.textPrimary, fontSize = 16.sp"))
    }
}
