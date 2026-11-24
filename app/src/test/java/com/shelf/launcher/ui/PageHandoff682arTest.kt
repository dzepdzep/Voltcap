package com.shelf.launcher.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 682ar (owner 24 Nov, safety): a confirm press kept through a page change is never handed to Play; everything else is, in order. */
@RunWith(RobolectricTestRunner::class)
class PageHandoff682arTest {
    private fun kept(onPlay: Boolean): List<String> {
        val h = PageHandoff()
        // (holding without a focus node: keep() only reads `holding` - set it through hold()'s failure path is not possible, so
        // reflectively: the holder is the window's; here only the queue is under test)
        val f = PageHandoff::class.java.getDeclaredField("holding"); f.isAccessible = true; f.setBoolean(h, true)
        listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_DPAD_UP).forEach { c ->
            h.keep(KeyEvent(KeyEvent.ACTION_DOWN, c)); h.keep(KeyEvent(KeyEvent.ACTION_UP, c))
        }
        val out = ArrayList<String>()
        h.release(onPlay) { out += "${it.keyCode}" + if (it.action == KeyEvent.ACTION_DOWN) "v" else "^" }
        return out
    }
    @Test fun confirmPressesKeptThroughATurnNeverReachPlay() = assertEquals(
        listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_DPAD_UP).flatMap { listOf("${it}v", "${it}^") }, kept(onPlay = true))
    @Test fun onThePagesOwnControlEveryKeptPressGoesThroughInOrder() = assertEquals(12, kept(onPlay = false).size)

    /** KONKR storm kst10: L1 and R1 in one frame end on the page they started from - the hand-over must still be released. */
    @Test fun everyTurnCountsEvenWhenItEndsWhereItStarted() {
        val box = HandoffPage(WinPage.TROPHIES, PageHandoff()).also { it.own = true }
        var page by box
        page = WinPage.DETAILS; page = WinPage.TROPHIES
        assertEquals(WinPage.TROPHIES, page); assertEquals(2, box.turns.intValue)
        val src = java.io.File("src/main/java/com/shelf/launcher/ui/GameWindow.kt").readText()
        org.junit.Assert.assertTrue("the hand-over effect is keyed by the turns", src.contains("LaunchedEffect(page, pageBox.turns.intValue)"))
    }
}
