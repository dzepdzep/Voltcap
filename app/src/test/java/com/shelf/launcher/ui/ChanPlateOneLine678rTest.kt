package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 678r (D350): the Channel grid's name plate stays one line (slides) and reports nothing into the badge lane. */
class ChanPlateOneLine678rTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map(::File).first { it.exists() }.readText()

    @Test fun plateNeverTakesAnExtraLine() {
        val chan = src("StyleConsoleHomes.kt").substringAfter("private fun ChanTile(").substringBefore("\n@Composable")
        assertTrue(chan.contains("slideWhenCut = true, extraLine = false"))
        assertFalse("678o's lane reporting made Compose never idle", chan.contains("laneContentAt"))
    }

    @Test fun fitTextHonoursExtraLine() {
        val fit = src("FitText.kt")
        assertTrue(fit.contains("if (extraLine && !fits(s, lines)) lines = maxLines + 1"))
        assertTrue(fit.contains("lineHeightRatio, extraLine) {"))
    }
}
