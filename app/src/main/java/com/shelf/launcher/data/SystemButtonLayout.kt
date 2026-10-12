package com.shelf.launcher.data

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which way the system has the face buttons set up (the handheld's own "Xbox / Nintendo layout"
 * switch), learned from the controller itself — no vendor setting has to be readable.
 *
 * Every key press carries two codes: the hardware scan code (where the button physically is: the
 * Linux driver reports the bottom button as BTN_SOUTH, the right one as BTN_EAST…) and the Android key
 * code after the system's mapping. In Xbox layout the bottom button arrives as A; when the system is
 * switched to Nintendo layout it arrives as B. Comparing the two on any real face-button press tells
 * which layout is active, so the legend follows a change in the system settings by the next press.
 *
 * Some devices swap the buttons inside the driver itself (the scan code changes too); that can't be
 * seen from an app, which is why Settings › Controls also has a manual Xbox / Nintendo choice.
 */
object SystemButtonLayout {
    private const val PREFS = "system_button_layout"
    // Linux input codes (linux/input-event-codes.h).
    private const val BTN_SOUTH = 0x130
    private const val BTN_EAST = 0x131
    private const val BTN_X = 0x133
    private const val BTN_Y = 0x134

    /** A and B come from swapped buttons (Nintendo layout). Null until a face button was pressed. */
    var abSwapped by mutableStateOf<Boolean?>(null)
        private set
    /** X and Y come from swapped buttons. Null until X or Y was pressed. */
    var xySwapped by mutableStateOf<Boolean?>(null)
        private set

    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.contains("ab")) abSwapped = p.getBoolean("ab", false)
        if (p.contains("xy")) xySwapped = p.getBoolean("xy", false)
    }

    /** Called for every key event the activity sees (cheap: a few comparisons). */
    fun observe(context: Context, event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return
        val scan = event.scanCode
        if (scan == 0) return // made-up presses (web remote, accessibility): no physical position
        val ab: Boolean? = when (scan) {
            BTN_SOUTH -> when (event.keyCode) { KeyEvent.KEYCODE_BUTTON_A -> false; KeyEvent.KEYCODE_BUTTON_B -> true; else -> null }
            BTN_EAST -> when (event.keyCode) { KeyEvent.KEYCODE_BUTTON_B -> false; KeyEvent.KEYCODE_BUTTON_A -> true; else -> null }
            else -> null
        }
        // X / Y: Android's standard key layout turns scan code 0x133 into X and 0x134 into Y (which of the
        // two is "top" differs between drivers, so only the swap against that default is read here).
        val xy: Boolean? = when (scan) {
            BTN_X -> when (event.keyCode) { KeyEvent.KEYCODE_BUTTON_X -> false; KeyEvent.KEYCODE_BUTTON_Y -> true; else -> null }
            BTN_Y -> when (event.keyCode) { KeyEvent.KEYCODE_BUTTON_Y -> false; KeyEvent.KEYCODE_BUTTON_X -> true; else -> null }
            else -> null
        }
        if (ab != null && ab != abSwapped) {
            abSwapped = ab
            save(context, "ab", ab)
        }
        if (xy != null && xy != xySwapped) {
            xySwapped = xy
            save(context, "xy", xy)
        }
    }

    private fun save(context: Context, key: String, value: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
    }
}
