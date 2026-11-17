package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 675m (CLAUDE.md rule 11, QA R18 / matrix P13): the notifications panel never cuts an app name, notification title
 * or text, background-task label, date or charge-limit pill with "…"; they wrap, shrink to a readable size, then slide
 * (FullText). Fails on the 675l sources (6 TextOverflow.Ellipsis uses, no FullText).
 */
class NotificationsFullText675mTest {
    private fun source(): String {
        val rel = "src/main/java/com/shelf/launcher/ui/NotificationsPanel.kt"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun notificationsPanelDoesNotEllipsize() {
        assertTrue("NotificationsPanel still uses TextOverflow.Ellipsis", !source().contains("TextOverflow.Ellipsis"))
    }

    @Test fun notificationTextsUseFullTextWithoutReservedLines() {
        // reserve = false: a one-line title in a two-line slot keeps today's row height (no new gaps in the list).
        val s = source()
        assertEquals("FullText uses (6 new + the 2 now-playing lines that already used it)", 8, Regex("""FullText\(""").findAll(s).count())
        assertEquals("reserve = false", 6, Regex("""reserve = false""").findAll(s).count())
    }
}
