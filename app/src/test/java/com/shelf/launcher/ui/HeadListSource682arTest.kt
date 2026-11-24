package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 682ar (D393, safety): a native page whose first focus - its order key, the window's listFocus - sits INSIDE its lazy list must
 * mark the list `head = true` (back to its head when the page is turned to again or re-sorted). Without it the key isn't built
 * after a scroll or a re-sort, the window's request for it falls back to Play and an A pressed during a turn reached Play
 * (Record crate, Dock). Every page drawn from now on is held to it here - the cloud keeps adding native pages.
 */
class HeadListSource682arTest {
    private fun block(s: String, open: Int): String {
        var d = 0; var i = open
        while (i < s.length) { when (s[i]) { '{' -> d++; '}' -> { d--; if (d == 0) return s.substring(open, i) } }; i++ }
        return s.substring(open)
    }
    @Test fun aListHoldingThePagesFirstFocusIsAHeadList() {
        val bad = mutableListOf<String>()
        File("src/main/java/com/shelf/launcher/ui").listFiles { f -> f.name.startsWith("WinPages") && f.name.endsWith(".kt") }!!.forEach { f ->
            val s = f.readText()
            var at = s.indexOf("NativeRows(")
            while (at >= 0) {
                var i = at + "NativeRows(".length; var d = 1
                while (d > 0 && i < s.length) { if (s[i] == '(') d++ else if (s[i] == ')') d--; i++ }
                val header = s.substring(at, i)
                val body = block(s, s.indexOf('{', i))
                val holdsFocus = Regex("""focus = focus\b""").containsMatchIn(body) || Regex("""\bstamp\(\)""").containsMatchIn(body)
                if (holdsFocus && !header.contains("head = true")) bad += "${f.name}: NativeRows at ${s.substring(0, at).count { it == '\n' } + 1}"
                at = s.indexOf("NativeRows(", i)
            }
        }
        assertEquals("lazy lists holding the page's first focus without head = true", emptyList<String>(), bad)
    }
}
