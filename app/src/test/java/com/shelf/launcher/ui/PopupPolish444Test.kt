package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.6.66 update 444 popup polish: rapid A B A B on the Odin's Twin desk lost the second A (the window was gone before its release)
 * and the last B opened the category screen; with the open-again on A's press, the last B was then lost and the window stayed open
 * (the next A started the game, blocked by the QA hook). Banner's round keys had no names; Cinema's captions were 10 sp at 72 %.
 */
class PopupPolish444Test {
    private fun src(name: String): String {
        val roots = listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/")
        return roots.map { File(it + name) }.first { it.exists() }.readText().replace("\r\n", "\n")
    }

    @Test fun aClosingWindowActsOnPressesNotReleases() {
        val s = src("GameWindow.kt")
        assertTrue("A while closing asks to open again on its press",
            s.contains("HomeKeyGuard.isOpenKey(e.key) && closingFlag[0]) {\n                GamePopup.askAgain(winToken, g); HomeKeyGuard.aDown = true"))  // (update 453: via askAgain)
        assertTrue("B while closing takes the open-again back", s.contains("GamePopup.cancelAgain(winToken)\n                return@onPreviewKeyEvent true"))
    }

    @Test fun bannerRoundKeysNameTheOneYouAreOn() {
        val s = src("GameWindowBanner.kt")
        assertTrue(s.contains("label = \"bannerTip\"") && s.contains("layout(0, 0) { p.place(size.roundToPx() - p.width"))
        // (678f: 678e moved the name to WinFullText with an sp line height - StableFullText keeps an sp ratio (1.08) as it
        // shrinks, an em one fell back to 1.2. The rule stays: the two name lines sit 1.08 apart at every size.)
        assertTrue("name lines close together once it shrinks", s.contains("lineHeight = (32 * 1.08f).sp"))
        assertTrue("an em line height would lose the 1.08 ratio in StableFullText", !s.contains("lineHeight = 1.08.em"))
        assertTrue("StableFullText keeps an sp line height's ratio", src("FitText.kt").contains("val ratio = if (lineHeight.isSp && top > 0f) lineHeight.value / top else 1.2f"))
    }

    @Test fun cinemaCaptionsAreReadable() {
        val s = src("WinScenesHomes.kt")
        assertTrue(s.contains("fontSize = if (compact) 12.sp else 11.sp") && !s.contains("alpha = if (on) 1f else 0.72f), fontSize = 10.sp"))
    }

    @Test fun qaTraceReportsCadence() {
        assertTrue(src("QaTrace.kt").contains("cadence n=\$winFrames missed=\$winMissed"))
    }
}
