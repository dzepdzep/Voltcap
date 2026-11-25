package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 750a (CLAUDE.md rule 11, QA R18 / matrix P13): the new-arrival card, the glance window's title and facts line, the
 * launch animation's title and console line and the companion screen's title / console show their texts whole (FullText:
 * wrap, readable size, then slide). Fails on the 749a sources (9 Ellipsis uses in these four files).
 */
class ArrivalLaunchFullText750aTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    private val files = listOf("NewArrival.kt", "GlanceWindow.kt", "LaunchAnimation.kt", "CompanionActivity.kt")

    @Test fun noEllipsis() {
        for (f in files) {
            val bad = source(f).lines().filter { it.contains("TextOverflow.Ellipsis") && !it.contains("qa-ok: TRUNCATE") }
            assertEquals("$f unjustified ellipses: $bad", 0, bad.size)
        }
    }

    @Test fun textsUseFullText() {
        val arrival = source("NewArrival.kt")
        assertTrue("arrival name keeps two lines of room", arrival.contains("color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2\n"))
        assertTrue("arrival tail", arrival.contains("FullText(tail, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)"))
        val glance = source("GlanceWindow.kt")
        assertTrue("glance title (its size places the art: no reserve)", Regex("""FullText\(\s*titles\.display\(g\.name\)[^)]*\)?[^\n]*\n[^\n]*reserve = false""").containsMatchIn(glance))
        assertTrue("glance facts", glance.contains("maxLines = if (showAll) 4 else 1, reserve = false)"))
        val launch = source("LaunchAnimation.kt")
        assertTrue("launch title centred", launch.contains("maxLines = 2, textAlign = TextAlign.Center, reserve = false"))
        assertTrue("launch subtitle", Regex("""FullText\(\s*subtitle\.uppercase\(\)""").containsMatchIn(launch))
        val comp = source("CompanionActivity.kt")
        assertEquals("companion titles", 2, Regex("""FullText\(\s*gameDisplayName\(info\.title\)""").findAll(comp).count())
        assertTrue("companion console", comp.contains("FullText(com.shelf.launcher.data.ConsoleNames.display(info.platform)"))
    }
}
