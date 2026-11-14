package com.shelf.launcher.ui

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 529o (#15 queue 1): after a tap inside the game window, the controller's next button must find a lit control again. */
class WinTouchRehome529Test {
    private val src = java.io.File("src/main/java/com/shelf/launcher/ui/GameWindow.kt").readText()

    @Test fun pageKeysAreDeliveredAfterTheFocusComesBack() {
        for (k in listOf(KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L2))
            assertTrue("page key $k", k in WIN_REHOME_DELIVER)
    }

    @Test fun aPressThatFindsNothingLitNeverClicks() {
        for (k in listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER)) {
            assertTrue("light only $k", k in WIN_REHOME_LIGHT); assertFalse("never delivered $k", k in WIN_REHOME_DELIVER)
        }
    }

    @Test fun bAndBackKeepTheirOwnPath() {
        for (k in listOf(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK))
            assertFalse("B untouched $k", k in WIN_REHOME_LIGHT || k in WIN_REHOME_DELIVER)
    }

    @Test fun rehomeOnlyWhenNothingInsideIsLitAndNotWhileClosing() {
        val i = src.indexOf("OnUnhandledKeyEventListenerCompat { v, ev ->"); assertTrue(i > 0)
        val body = src.substring(i, src.indexOf("addOnUnhandledKeyEventListener", i))
        assertTrue(body.contains("focusSeen[0]")); assertTrue(body.contains("closingFlag[0]")); assertTrue(body.contains("ev.repeatCount > 0"))
        assertTrue(body.contains("again[0]")) // no loop on its own re-delivery
        assertTrue(src.contains(".onFocusChanged { focusSeen[0] = it.hasFocus }"))
        assertTrue(src.contains("removeOnUnhandledKeyEventListener(keyView, rehome)"))
    }
}
