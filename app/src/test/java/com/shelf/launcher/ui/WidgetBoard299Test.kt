package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 299: the Widget Board's saved sizes and reordering. */
class WidgetBoard299Test {
    @Test
    fun emptySizesMeanNormal() {
        assertTrue(WidgetBoardItems.parseSizes("").isEmpty())
    }

    @Test
    fun sizesRoundTrip() {
        val m = mapOf("builtin:VOLUME_MIXER" to WidgetSize.EXPANDED, "app:12" to WidgetSize.COMPACT)
        assertEquals(m, WidgetBoardItems.parseSizes(WidgetBoardItems.encodeSizes(m)))
    }

    @Test
    fun normalIsNotStored() {
        assertEquals("", WidgetBoardItems.encodeSizes(mapOf("builtin:NOW_PLAYING" to WidgetSize.NORMAL)))
    }

    @Test
    fun junkIsSkipped() {
        assertEquals(mapOf("app:3" to WidgetSize.COMPACT), WidgetBoardItems.parseSizes("app:3=COMPACT;bad;=EXPANDED;x=HUGE"))
    }

    @Test
    fun moveToKeepsEveryItem() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), WidgetBoardItems.moveTo(l, "a", 2))
        assertEquals(listOf("d", "a", "b", "c"), WidgetBoardItems.moveTo(l, "d", 0))
        assertEquals(l, WidgetBoardItems.moveTo(l, "z", 1))
        assertEquals(listOf("a", "b", "d", "c"), WidgetBoardItems.moveTo(l, "c", 99))
    }

    @Test
    fun sizeCycleStaysInAllowed() {
        val all = WidgetSize.entries
        assertEquals(WidgetSize.EXPANDED, WidgetSize.NORMAL.next(all))
        assertEquals(WidgetSize.COMPACT, WidgetSize.EXPANDED.next(all))
        assertEquals(WidgetSize.NORMAL, WidgetSize.NORMAL.next(listOf(WidgetSize.NORMAL)))
        assertEquals(WidgetSize.EXPANDED, WidgetSize.NORMAL.next(listOf(WidgetSize.NORMAL, WidgetSize.EXPANDED)))
    }
}
