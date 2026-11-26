package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 756a (CLAUDE.md rule 11, QA R18 / matrix P13): a game's state badge, the Cube hub's console label, Cross menu's
 * achievement progress line, the Volt tab names, the second-screen showcase name and the second-screen details name show
 * their texts whole (FullText: wrap, readable size, then slide). Fails on the 755a sources (one Ellipsis in each file).
 */
class LookTabsFullText756aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    private val files = listOf("GameLookUi.kt", "CubeHub.kt", "StyleCrossMenu.kt", "VoltUi.kt", "SecondShowcase.kt", "SecondScreen.kt")

    @Test fun noEllipsis() {
        for (f in files) {
            val bad = source(f).lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
            assertEquals("$f unjustified ellipses: $bad", 0, bad.size)
        }
    }

    @Test fun textsUseFullText() {
        assertTrue("state badge", Regex("""FullText\([^\n]*\n\s*state\.label,""").containsMatchIn(source("GameLookUi.kt")))
        assertTrue("cube label", source("CubeHub.kt").contains("FullText(st.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)"))
        assertTrue("cross progress", Regex("""FullText\([^\n]*\n\s*p\?\.let \{ \(e, t\) -> "\${'$'}e / \${'$'}t achievements""").containsMatchIn(source("StyleCrossMenu.kt")))
        assertTrue("volt tab", Regex("""FullText\([^\n]*\n\s*label,\n\s*color = if \(on\) ShelfTheme\.accent""").containsMatchIn(source("VoltUi.kt")))
        assertTrue("showcase name", Regex("""FullText\([^\n]*\n\s*gameDisplayName\(game\.name\), color = Color\.White, fontSize = 30\.sp""").containsMatchIn(source("SecondShowcase.kt")))
        assertTrue("second-screen name", Regex("""FullText\([^\n]*\n\s*game\.name, color = ShelfTheme\.colors\.textPrimary, fontSize = 24\.sp""").containsMatchIn(source("SecondScreen.kt")))
    }
}
