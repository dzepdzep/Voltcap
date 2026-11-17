package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 678o (live KONKR session, QA rule R26 touch size): the Banner game window's save-sync chip ("Save sync is not set up")
 * and its L1 / R1 hints are tap targets, and on the KONKR (240 dpi, x1.0) they were 27 / 25 px tall = 18 / 17 dp - under the
 * gate's 24 dp (popclip kb678n10, COVERDETAILS_BANNER geometry FAIL). Each now has a touch box at least 24 dp tall with the
 * drawn pill (background / border) inside it at its old size, as the scenes' status lines (676x). Fails on the 678n sources
 * (no heightIn in either function; the pill was drawn on the clickable itself).
 */
class BannerTouch678oTest {
    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    /** The body of `private fun NAME(` up to the next top-level declaration. */
    private fun body(s: String, name: String): String {
        val i = s.indexOf("private fun $name(")
        assertTrue("$name exists", i >= 0)
        val next = Regex("""\n(@Composable|/\*\*|private fun|internal fun|fun )""").find(s, i + 1)
        return s.substring(i, next?.range?.first ?: s.length)
    }

    @Test fun syncChipTouchBoxIsAtLeast24dp() {
        val b = body(source("GameWindowBanner.kt"), "BannerSyncChip")
        val touch = b.indexOf("heightIn(min = 24.dp)")
        val click = b.indexOf(".clickable(")
        val pill = b.indexOf(".background(")
        assertTrue("the chip's touch box has a 24 dp minimum height", touch >= 0)
        assertTrue("the minimum is on the clickable box (before .clickable)", click > touch)
        assertTrue("the pill is drawn inside the touch box (its background after the clickable)", pill > click)
        assertTrue("the pill keeps its own small padding", b.indexOf(".padding(horizontal = 8.dp, vertical = 2.dp)") > click)
    }

    @Test fun shoulderHintsTouchBoxIsAtLeast24dp() {
        val b = body(source("GameWindowBanner.kt"), "BannerHint")
        val touch = b.indexOf("heightIn(min = 24.dp)")
        val click = b.indexOf(".clickable")
        val pill = b.indexOf(".border(")
        assertTrue("the hint's touch box has a 24 dp minimum height", touch >= 0)
        assertTrue("the minimum is on the clickable box (before .clickable)", click > touch)
        assertTrue("the bordered pill is drawn inside the touch box", pill > click)
        assertTrue("the hint still never takes the D-pad focus", b.contains("canFocus = false"))
    }
}
