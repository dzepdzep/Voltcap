package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 678k (owner request H8 / D348, CLAUDE.md rule 11): the console-style homes (Console Hub, Resume, Channel grid) never
 * end a name in "…". The installed v0.6.72 showed "Advance Wars (Rev 1) (No Glitc…" on the focused Hub card at Larger text:
 * the Continue card's name now wraps to two lines and slides (677i), and the Hub tile titles, Channel tile titles and the
 * Channel category label take FitText's slide instead of its "…" last resort. The Achievements card ticker keeps FullText
 * rows at their full height (676y, "Class Dismisse"). Fails on the 678j sources (4 FitText calls without slideWhenCut).
 */
class ConsoleHubFullText678kTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun consoleHomesNeverEndANameInAnEllipsis() {
        val s = source("StyleConsoleHomes.kt")
        val fits = Regex("""(?<![A-Za-z])FitText\(([^\n]*)""").findAll(s).map { it.groupValues[1] }.toList()
        assertTrue("the file still fits its names", fits.size >= 6)
        assertEquals("every FitText slides instead of ending in an ellipsis", emptyList<String>(), fits.filterNot { it.contains("slideWhenCut = true") })
        assertEquals("no direct ellipsis either", 0, Regex("""TextOverflow\.Ellipsis""").findAll(s).count())
        assertTrue("the focused Hub tile keeps its two lines and sizes",
            s.contains("FitText(LocalTitleOptions.current.display(g.name), Color.White, maxSize = 14.sp, minSize = 9.sp, maxLines = 2, fontWeight = FontWeight.SemiBold, slideWhenCut = true)"))
        assertTrue("the Continue card's name: two lines, then a slide (677i)",
            s.contains("if (spec.sub != null) FitText(spec.sub, Color.White.copy(alpha = 0.78f), maxSize = 11.sp, minSize = 8.sp, maxLines = 2, slideWhenCut = true)"))
        assertTrue("the Achievements ticker rows are FullText at their full height (676y)",
            s.contains("FullText(a.title.orEmpty(), color = Color.White, fontSize = 11.sp, maxLines = 1)") &&
                s.contains("wrapContentHeight(align = Alignment.Top, unbounded = true)"))
    }
}
