package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 757a (CLAUDE.md rule 11, QA R18 / matrix P13): the classic row's game name, the badge wall's percentage / points line
 * and Pocket Quest's Play key show their text whole (FullText: wrap, readable size, then slide); the status bar's live progress
 * counters keep a justified, documented exception (whole in the Background work dialog). Fails on the 756a sources.
 */
class DockBadgeFullText757aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun noUnjustifiedEllipsis() {
        for (f in listOf("GameDock.kt", "BadgeWall.kt", "StylePocketQuest.kt", "HomeChrome.kt")) {
            val bad = source(f).lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
            assertEquals("$f unjustified ellipses: $bad", 0, bad.size)
        }
    }

    @Test fun textsUseFullText() {
        val dock = source("GameDock.kt")
        assertTrue("dock name", Regex("""FullText\(\n\s*name,\n\s*color = androidx\.compose\.ui\.graphics\.Color\.White""").containsMatchIn(dock))
        // Same size as before and no reserved second line: nothing around the name moves while the selection moves.
        assertTrue("dock name keeps its layout", dock.contains("style = shadowStyle, autoFit = false, reserve = false,"))
        assertTrue("badge wall line", Regex("""FullText\(\n\s*sub,""").containsMatchIn(source("BadgeWall.kt")))
        assertTrue("pocket quest play", Regex("""FullText\(\n\s*if \(resumed\) "Continue" else "Play Game"""").containsMatchIn(source("StylePocketQuest.kt")))
        assertTrue("status readouts exception names the dialog", source("HomeChrome.kt").contains("qa-ok: TRUNCATE (live counters in the status bar's leftover room; whole in the Background work dialog"))
    }
}
