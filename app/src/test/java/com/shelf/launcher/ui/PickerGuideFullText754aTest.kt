package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 754a (CLAUDE.md rule 11, QA R18 / matrix P13): the emulator picker's subtitle, the first-run "Connected" mark, the
 * who-is-playing name, a saved preset's name, the manual reader's title / page, the guide's title on the second screen, a
 * game-look option and the second screen's console label show their texts whole (FullText: wrap, readable size, then slide).
 * Fails on the 753a sources (one Ellipsis in each of these eight files).
 */
class PickerGuideFullText754aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    private val files = listOf(
        "EmulatorPickerDialog.kt", "FirstRunDialog.kt", "WhoIsPlaying.kt", "PresetsCard.kt", "ManualReader.kt",
        "GuideSecondScreen.kt", "GameLookPicker.kt", "SecondScreenDetails.kt"
    )

    @Test fun noEllipsis() {
        for (f in files) {
            val bad = source(f).lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
            assertEquals("$f unjustified ellipses: $bad", 0, bad.size)
        }
    }

    @Test fun textsUseFullText() {
        assertTrue("picker subtitle", source("EmulatorPickerDialog.kt").contains("FullText(subtitle, color = ShelfTheme.textMuted, fontSize = 11.sp, maxLines = 2)"))
        assertTrue("connected mark", source("FirstRunDialog.kt").contains("FullText(\"✓ Connected\""))
        assertTrue("player name", source("WhoIsPlaying.kt").contains("FullText(name, color = Color.White, fontSize = 17.sp"))
        assertTrue("preset name", source("PresetsCard.kt").contains("FullText(c.name, color = ShelfTheme.colors.textPrimary, fontSize = 14.sp"))
        assertTrue("manual title", Regex("""FullText\(\s*if \(count > 0\) "\${'$'}title""").containsMatchIn(source("ManualReader.kt")))
        assertTrue("guide title", source("GuideSecondScreen.kt").contains("FullText(pos.guide.title, color = ShelfTheme.textMuted, fontSize = 11.sp, maxLines = 2)"))
        assertTrue("look option", Regex("""FullText\(\s*\(if \(on\) "✓ " else ""\) \+ label""").containsMatchIn(source("GameLookPicker.kt")))
        assertTrue("console label", Regex("""FullText\(\s*com\.shelf\.launcher\.data\.ConsoleNames\.label\(game\.platform\)\.uppercase\(\)""").containsMatchIn(source("SecondScreenDetails.kt")))
    }
}
