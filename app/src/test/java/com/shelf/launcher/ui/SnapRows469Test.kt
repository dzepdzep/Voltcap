package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 469 (D221): Twin hinge's facts box paged every 2.8 s and changed its height with each page, so the window and its
 * keys jumped and a tap on More / Play closed the window (KONKR). Every page now fits the first page's height, and the box
 * is laid out at that height.
 */
class SnapRows469Test {
    @Test fun everyPageFitsTheFirstPagesHeight() {
        val b = SnapPaging.bounds(listOf(20, 20, 20, 20, 10), 0, 0)
        for (avail in listOf(25, 45, 50, 70, 200)) {
            val boxH = SnapPaging.window(b, 0, avail)
            var top = 0
            repeat(10) {
                assertTrue("page at $top fits $boxH (avail $avail)", SnapPaging.window(b, top, boxH) <= boxH)
                top = SnapPaging.next(b, top, boxH) ?: return@repeat
            }
        }
    }

    @Test fun theBoxKeepsOneHeight() {
        val f = listOf("src/main/java/com/shelf/launcher/ui/WinSceneTwin.kt", "app/src/main/java/com/shelf/launcher/ui/WinSceneTwin.kt")
            .map(::File).first { it.exists() }
        val body = f.readText().substringAfter("fun SnapRows(").substringBefore("internal object SnapPaging")
        assertTrue(body.contains("val boxH = SnapPaging.window(bounds, 0, avail)"))
        assertTrue(body.contains("layout(c.maxWidth, boxH."))
    }
}
