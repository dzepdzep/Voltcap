package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/**
 * 682al (D386, KONKR atrace kt10: a native page change spent 48 ms recomposing and 90 ms laying out the new page - each
 * WholeWordsText a BoxWithConstraints of its own). Fails on 682ak: the subcomposition was there, the auto size did not exist.
 */
class PageChange682alTest {
    @Test fun wholeWordsTextHasNoSubcomposition() {
        val s = File("src/main/java/com/shelf/launcher/ui/FitText.kt").readText()
        val body = s.substringAfter("internal fun WholeWordsText(").substringBefore("internal data class WholeWordsAutoSize")
            .lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/**") }.joinToString("\n")
        assertFalse(body.contains("BoxWithConstraints"))
        assertFalse(body.contains("rememberTextMeasurer"))
    }

    @Test fun autoSizeKeysOnItsInputs() {
        // remembered per size and longest word - a new word or size is a new search, the same ones reuse it
        assertEquals(WholeWordsAutoSize(13f, 10f, "ACHIEVEMENTS"), WholeWordsAutoSize(13f, 10f, "ACHIEVEMENTS"))
        assertNotEquals(WholeWordsAutoSize(13f, 10f, "ACHIEVEMENTS"), WholeWordsAutoSize(13f, 10f, "RELEASED"))
    }

    @Test fun achievementRowsArriveFirstTenThenGrowAndNeverShrink() {
        // 682am (D389): fails on 682al - every row was composed in one frame (1031 ms for 120 rows in Cinema on the KONKR)
        assertEquals(10, nativeRowsShown(0, 120))
        assertEquals(4, nativeRowsShown(0, 4))
        assertEquals(40, nativeRowsShown(40, 120))   // a refresh or re-sort keeps the rows already shown
        assertEquals(30, nativeRowsShown(40, 30))    // never more than there are
        assertEquals(0, nativeRowsShown(0, 0))
    }
}
