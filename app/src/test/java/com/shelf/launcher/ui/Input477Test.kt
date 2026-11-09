package com.shelf.launcher.ui

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 477 (D252): the first D-pad press after a touch is not eaten — the root leaves touch mode in the pre-IME step. */
class Input477Test {
    @Test fun aDpadPressInTouchModeLeavesItFirst() {
        for (k in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN))
            assertTrue(leavesTouchModeFirst(KeyEvent.ACTION_DOWN, k, inTouchMode = true))
    }

    @Test fun notOutsideTouchModeNotOnKeyUpNotForFaceButtons() {
        assertFalse(leavesTouchModeFirst(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, inTouchMode = false))
        assertFalse(leavesTouchModeFirst(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, inTouchMode = true))
        assertFalse(leavesTouchModeFirst(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, inTouchMode = true))   // (MainActivity, update 467)
    }

    @Test fun theActivityUsesTheKeyRoot() {
        val f = listOf("src/main/java/com/shelf/launcher/MainActivity.kt", "app/src/main/java/com/shelf/launcher/MainActivity.kt").map(::File).first { it.exists() }
        assertTrue(f.readText().contains("setKeyRootContent {"))
    }
}
