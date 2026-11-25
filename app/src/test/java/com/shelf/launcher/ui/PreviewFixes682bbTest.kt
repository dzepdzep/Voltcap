package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 682bb (owner review of the v0.7.0 preview): Green core's stats never slide, Disc station's MODE reads at once, wrapped row
 *  descriptions keep a normal line spacing at x1.3. */
class PreviewFixes682bbTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText().replace("\r\n", "\n")

    @Test fun greenCoreStatShareOrStack() {
        assertTrue(gcStatOneLine(60f, 80f, 150f, 8f))
        assertFalse("x1.3: 'NEVER PLAYED' beside 'PLAY TIME' does not fit - it goes under the label", gcStatOneLine(80f, 96f, 150f, 8f))
        val s = src("StyleGreenCore.kt")
        val stat = s.substring(s.indexOf("private fun GcStat("), s.indexOf("internal fun gcStatOneLine"))
        assertFalse("no sliding value any more", stat.contains("FullText("))
        assertFalse("no BoxWithConstraints on a panel rebuilt per game change", stat.contains("BoxWithConstraints("))
        assertTrue(stat.contains("WholeWordsText(value"))
    }

    @Test fun discModeWordsAreShortAndClear() {
        assertEquals(listOf("IN ORDER", "TO GET FIRST", "EARNED FIRST", "LATEST FIRST", "TOP POINTS", "RAREST FIRST"),
            Features.TrophyOrder.values().map(::discModeWord))
        for (o in Features.TrophyOrder.values()) assertTrue(o.name, ("MODE: " + discModeWord(o)).length <= 18)
    }

    @Test fun discModeSharesTheLineOnlyWhenWhole() {
        // count 70 + gap 8 + meter 40 + gap 8 + key 120 = 246
        assertTrue(discModeOneLine(70, 120, 246, 8, 40))
        assertFalse(discModeOneLine(70, 121, 246, 8, 40))
        val p = src("WinPagesDisc.kt")
        assertTrue(p.contains("WinFullText(\"MODE: \" + discModeWord(t.order)"))
        assertFalse("the long order label is not on the deck's key", p.contains("\"MODE: \" + t.order.label"))
        assertTrue("otherwise the key gets its own line", p.contains("key.place(0, h1 + top)"))
    }

    @Test fun rowDescriptionsHaveAnOwnLineHeight() {
        val c = src("DialogChrome.kt")
        assertTrue(c.contains("internal fun RowSubtitle(text: String, fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit) =\n    Text(text, color = ShelfTheme.textMuted, fontSize = fontSize, lineHeight = lineHeight)"))
        val menuRow = c.substring(c.indexOf("internal fun MenuRow("), c.indexOf("internal fun SwitchRow("))
        assertTrue(menuRow.contains("RowSubtitle(subtitle, 12.sp, 16.sp)"))
        assertTrue(c.substring(c.indexOf("internal fun RadioLine("), c.indexOf("internal fun Chip(")).contains("RowSubtitle(blurb, 11.sp, 15.sp)"))
        // no muted row description left on the theme's 24 sp line
        assertFalse(Regex("""Text\((subtitle|blurb), color = ShelfTheme\.textMuted, fontSize = 1[1-3]\.sp\)""").containsMatchIn(c))
        // the theme trims every style, so a one-line description keeps its height (676v)
        assertTrue(src("theme/Theme.kt").contains("bodyLarge = t.bodyLarge.trim()"))
    }

    @Test fun keyRowsKeepPlayInViewAndOneSize() {
        val m = src("WinScenesMore.kt")
        // Cardboard box x1.3: a row the focus has left shows Play again
        assertTrue(m.contains("Box(Modifier.onFocusChanged { if (it.hasFocus) focused = i else if (focused == i) focused = -1 }"))
        assertEquals(0, uniformRowScrollFor(-1, 300, UniformRowGeom(keyW = 100, visible = 1, scrolls = true, pitch = 110, maxOffset = 330, viewStart = 0, viewW = 100)))
        // Pocket camera on the RP Classic: the key column is measured with the page reference key on a window's own pages
        val col = m.substring(m.indexOf("internal fun UniformKeyColumn("), m.indexOf("private fun uniformKeysPolicy("))
        assertTrue(col.contains("LocalMeasureOnly provides true) { key(PAGE_KEY_REF) }"))
        assertTrue(col.contains("val ms = if (pageRef) all.dropLast(1) else all"))
        assertTrue("the reference is never focused or touched", src("WinScenes.kt").contains("if (LocalMeasureOnly.current) {"))
    }

    @Test fun cameraPictureNeverCoversTheName() {
        val c = src("WinScenesConsole.kt")
        assertTrue(c.contains("Modifier.weight(1f, fill = false).aspectRatio(112f / 128f, matchHeightConstraintsFirst = true)"))
        assertFalse(c.contains("Modifier.weight(1f).aspectRatio(112f / 128f, matchHeightConstraintsFirst = true)"))
    }

    @Test fun discTrackLineGivesTheProgramRoom() {
        assertEquals(0, discTrackLineFit(130, 50, 80, 55, 300, 8))
        assertEquals(1, discTrackLineFit(130, 50, 80, 55, 210, 8))
        assertEquals(2, discTrackLineFit(130, 50, 80, 55, 190, 8))
        val m = src("WinScenesMore.kt")
        assertTrue(m.contains("else DiscTrackLine(discTrackLabel(p, tracks), discPageName(p).uppercase(), tracks, p, discInk)"))
        assertFalse("no calendar row of its own under the track line", m.contains("DiscCalendar(tracks, p, discInk)"))
    }
}
