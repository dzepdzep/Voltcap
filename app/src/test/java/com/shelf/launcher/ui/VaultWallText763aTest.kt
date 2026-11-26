package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 763a (P5 polish, System Vault + Wall). Vault: the reader's screen is ~200 dp wide on the KONKR (640 x 427 dp); in monospace
 * at x1.3 its "READING" header and facts line need more than one line and only slid - they wrap onto a reserved second line
 * first, and the covers between them keep a usable height. Wall: the game count was a plain one-line Text in an editable box
 * (cut when the box was made smaller) - it goes through FullText like the name. Source half fails on 762a.
 */
class VaultWallText763aTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText().replace("\r\n", "\n")

    @Test fun vaultHeaderAndFactsWrapBeforeTheySlide() {
        val l = src("ui/CategoryLooks.kt")
        assertEquals(2, VAULT_SCREEN_LINES)
        assertTrue(l.contains("FullText(\"READING  ·  \${t.label.uppercase()}\", color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = VAULT_SCREEN_LINES)"))
        assertTrue(l.contains("fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = VAULT_SCREEN_LINES)"))
        // the KONKR reader screen's text width and the header at x1.3 (monospace ~0.6 em): one line is too short, two fit
        val readerW = 640f - (48f + 640f * 0.36f + 640f * 0.08f) - 48f
        val textW = readerW - 36f - 24f
        val header = "READING  ·  PLAYSTATION PORTABLE".length * 11f * 1.3f * 0.6f
        assertTrue("$header > $textW", header > textW)
        assertTrue("$header <= 2 x $textW", header <= 2 * textW)
    }

    @Test fun theCoversKeepARoomWithBothLinesReserved() {
        assertTrue("KONKR x1.3 ${vaultCoverRoom(427f, 1.3f)}", vaultCoverRoom(427f, 1.3f) >= 80f)
        assertTrue("KONKR x1.0 ${vaultCoverRoom(427f, 1f)}", vaultCoverRoom(427f, 1f) >= 100f)
        // a 20:9 phone in landscape (360 dp high) still leaves the covers a strip at x1.3
        assertTrue("phone x1.3 ${vaultCoverRoom(360f, 1.3f)}", vaultCoverRoom(360f, 1.3f) >= 30f)
        // the arithmetic follows the look's own geometry
        val l = src("ui/CategoryLooks.kt")
        assertTrue(l.contains("val top = 78.dp; val bottom = 56.dp\n        val slots = 5"))
        assertTrue(l.contains("val modH = slotH - 14.dp"))
        assertTrue(l.contains("padding(start = 18.dp, end = 18.dp, top = modH + 38.dp, bottom = 16.dp)"))
    }

    @Test fun wallCountGoesThroughFullText() {
        val s = src("ui/PlatformBrowser.kt")
        assertTrue(s.contains("FullText(countText(t), color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, reserve = false)"))
        assertFalse(Regex("""(?<![A-Za-z])Text\(countText\(t\), color = accentColor""").containsMatchIn(s))
    }
}
