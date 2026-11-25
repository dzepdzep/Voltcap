package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 752a (CLAUDE.md rule 11, QA R18 / matrix P13): the quick-settings tile card, the Arch stacks' name and storage line,
 * Twin showcase's category name and game name and the cartridge shell picker's names show their texts whole (FullText /
 * StableFullText: wrap, readable size, then slide). Fails on the 751a sources (8 Ellipsis uses in these four files).
 */
class ShowcaseFullText752aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    private val files = listOf("QsTilesCard.kt", "StacksArch.kt", "TwinShowcase.kt", "CartShellPicker.kt")

    @Test fun noEllipsis() {
        for (f in files) {
            val bad = source(f).lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
            assertEquals("$f unjustified ellipses: $bad", 0, bad.size)
        }
    }

    @Test fun textsUseFullText() {
        val qs = source("QsTilesCard.kt")
        assertTrue("tile label", qs.contains("FullText(t.label, color = ShelfTheme.colors.textPrimary"))
        assertTrue("in the menu", qs.contains("FullText(\"✓ In the menu\""))
        val arch = source("StacksArch.kt")
        assertTrue("arch name sized in its own pass", Regex("""StableFullText\(\s*titles\.display\(shownGame\.name\)""").containsMatchIn(arch))
        assertTrue("arch storage line", Regex("""FullText\(\s*listOfNotNull\(storage""").containsMatchIn(arch))
        val twin = source("TwinShowcase.kt")
        assertTrue("twin category", Regex("""StableFullText\(\s*// [^\n]*\n\s*c\.categoryLabel""").containsMatchIn(twin))
        assertTrue("twin name", Regex("""StableFullText\(\s*titles\.display\(g\.name\)""").containsMatchIn(twin))
        val cart = source("CartShellPicker.kt")
        assertTrue("shell name", Regex("""FullText\(\s*\(if \(id == original\)""").containsMatchIn(cart))
        assertTrue("edition name", cart.contains("FullText(e.name, color = ShelfTheme.colors.textPrimary, fontSize = 11.sp"))
    }
}
