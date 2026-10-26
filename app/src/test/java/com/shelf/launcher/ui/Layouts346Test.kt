package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 346: the polish of the update 344 layouts — the functional fixes that can be checked without a screen. */
class Layouts346Test {
    private data class F(val key: String, val name: String, val ext: String)

    private fun aliases(vararg f: F) = termAliases(f.toList(), { it.key }, { it.name }, { it.ext })

    /** Every file of a listing gets its own 8.3 name; a name that fits keeps itself. */
    @Test fun terminalAliasesAreUnique() {
        val files = listOf(
            F("a", "Asterix & Obelix XXL", "PS2"), F("b", "Asterix & Obelix XXL 2", "PS2"), F("c", "Asterix & Obelix XXL 3", "PS2"),
            F("d", "Aladdin", "SNE"), F("e", "Aladdin (Europe)", "SNE"), F("f", "Baldur's Gate Dark Alliance", "PS2"),
            F("g", "Baldur's Gate Dark Alliance II", "PS2")
        )
        val m = termAliases(files, { it.key }, { it.name }, { it.ext })
        assertEquals(files.size, m.size)
        val full = files.map { m.getValue(it.key) + "." + it.ext }
        assertEquals("unique", full.size, full.toSet().size)
        assertTrue(m.values.all { it.length <= 8 && it.all { ch -> ch.isLetterOrDigit() || ch == '~' } })
        assertEquals("ASTERI~1", m["a"]); assertEquals("ASTERI~2", m["b"]); assertEquals("ASTERI~3", m["c"])
        assertEquals("ALADDIN", m["d"]); assertEquals("ALADDI~1", m["e"])
    }

    /** The same name on two consoles is two different files (name + extension), both keep the plain alias. */
    @Test fun terminalAliasesPerExtension() {
        val m = aliases(F("n64", "Mario Kart", "N64"), F("snes", "Mario Kart", "SNE"))
        assertEquals("MARIOK~1", m["n64"]); assertEquals("MARIOK~1", m["snes"])
    }

    /** Deterministic: the list's order doesn't change who gets which alias. */
    @Test fun terminalAliasesKeepTheirName() {
        val a = F("1", "Call of Duty 2", "PS2"); val b = F("2", "Call of Duty 3", "PS2"); val c = F("3", "Call of Duty", "PS2")
        assertEquals(aliases(a, b, c), aliases(c, b, a))
        // More than nine: the tenth on loses one letter for "~10".
        val many = (1..12).map { F("k$it", "Super Robot Wars $it", "GBA") }
        val m = termAliases(many, { it.key }, { it.name }, { it.ext })
        assertEquals(12, m.values.toSet().size)
        assertTrue(m.values.any { it == "SUPER~10" })
    }

    /** A listing line keeps its width; a cut title says so with "…", the time played stays at the right. */
    @Test fun terminalLinesFit() {
        val wide = termGameLine("ASTERI~1", "PS2", "ASTERIX & OBELIX XXL 2: MISSION LAS VEGUM", "1h05", 50)
        assertEquals(50, wide.length)
        assertTrue(wide.startsWith("ASTERI~1 PS2  ASTERIX"))
        assertTrue(wide.contains('…')); assertTrue(wide.endsWith("1h05"))
        val narrow = termGameLine("ASTERI~1", "PS2", "ASTERIX", "--", 30)
        assertTrue("no 8.3 name on a narrow listing", narrow.startsWith("ASTERIX"))
        assertEquals(30, narrow.length)
        assertEquals("AB…", termFit("ABCDEF", 3)); assertEquals("AB ", termFit("AB", 3))
    }

    /** The magazine's score box says whose score it is (or that it's your time) — never a verdict made up from it. */
    @Test fun magazineScoreBoxIsFactual() {
        assertEquals("METASCORE", magScoreTitle(metacritic = true, score = true))
        assertEquals("CRITICS", magScoreTitle(metacritic = false, score = true))
        assertEquals("YOUR TIME", magScoreTitle(metacritic = false, score = false))
        for (sc in listOf(true, false)) for (pl in listOf(true, false)) {
            val l = magScoreLine(sc, pl)
            assertTrue(l, listOf("must", "recommend", "fans", "worth", "classic").none { l.lowercase().contains(it) })
        }
    }

    /** The popups never double a word the window's look already put in: "CONTINUE!!", "RENT & RENT & PLAY". */
    @Test fun popupLabelsAreNotDoubled() {
        assertEquals("PLAY!", magBurstLabel("Play"))
        assertEquals("CONTINUE!", magBurstLabel("CONTINUE!"))
        assertEquals("RENT & PLAY", rentalKeyLabel("RENT & PLAY", primary = true))
        assertEquals("RENT & PLAY", rentalKeyLabel("Play", primary = true))
        assertEquals("OPTIONS", rentalKeyLabel("Options", primary = false))
    }

    /** Update 347: a press of Play right as the window opens is ignored (a double press of A started the game). */
    @Test fun playWaitsForTheWindow() {
        assertTrue(!playAllowed(0)); assertTrue(!playAllowed(200)); assertTrue(playAllowed(PLAY_GUARD_MS)); assertTrue(playAllowed(2000))
    }

    /** Update 347: the DOS box draws its frame first, then its text; the camera screen moves in whole steps. */
    @Test fun popupAnimationSteps() {
        val half = termFrame(0.3f)
        assertEquals(1f, half.top, 0.001f); assertEquals(0f, half.text, 0.001f)
        assertEquals(1f, termFrame(1f).text, 0.001f); assertEquals(0f, termFrame(0f).top, 0.001f)
        for (i in 0..100) { val k = i / 100f; val q = cameraStep(k); assertEquals(q * 12f, Math.round(q * 12f).toFloat(), 0.001f); assertTrue(q <= k) }
        assertEquals(0f, tagSwing(1f), 0.001f); assertEquals(1f, tagSwing(0f), 0.001f)
    }

    /** Past 14 pages the bubbles show a window of 11 dots round the page you're on, always inside the pages. */
    @Test fun bubblePageDots() {
        assertEquals(0 to 4, bubbleDotWindow(2, 5))
        assertEquals(0 to 13, bubbleDotWindow(13, 14))
        for (pages in listOf(15, 40, 158)) for (p in 0 until pages) {
            val (a, b) = bubbleDotWindow(p, pages)
            assertTrue(p in a..b); assertEquals(10, b - a); assertTrue(a >= 0 && b <= pages - 1)
        }
    }
}
