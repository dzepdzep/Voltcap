package com.shelf.launcher.data

import android.view.KeyEvent

/** What a music button does. */
enum class MusicAction(val label: String) {
    PLAY_PAUSE("Play / pause"),
    NEXT("Next song"),
    PREVIOUS("Previous song"),
    SEEK_BACK("Back 10 s"),
    SEEK_FORWARD("Ahead 10 s")
}

/**
 * Buttons that can run a music action from the home screen. "Select + …"
 * means hold Select and press the other button — Select on its own still
 * opens the menu when it's let go without a combo.
 */
enum class MusicButton(val label: String, val keyCode: Int, val withSelect: Boolean) {
    NONE("Off", KeyEvent.KEYCODE_UNKNOWN, false),
    SELECT_A("Select + A", KeyEvent.KEYCODE_BUTTON_A, true),
    SELECT_B("Select + B", KeyEvent.KEYCODE_BUTTON_B, true),
    SELECT_X("Select + X", KeyEvent.KEYCODE_BUTTON_X, true),
    SELECT_Y("Select + Y", KeyEvent.KEYCODE_BUTTON_Y, true),
    SELECT_LEFT("Select + ◀", KeyEvent.KEYCODE_DPAD_LEFT, true),
    SELECT_RIGHT("Select + ▶", KeyEvent.KEYCODE_DPAD_RIGHT, true),
    SELECT_UP("Select + ▲", KeyEvent.KEYCODE_DPAD_UP, true),
    SELECT_DOWN("Select + ▼", KeyEvent.KEYCODE_DPAD_DOWN, true),
    SELECT_L1("Select + L1", KeyEvent.KEYCODE_BUTTON_L1, true),
    SELECT_R1("Select + R1", KeyEvent.KEYCODE_BUTTON_R1, true),
    L3("L3 (left stick press)", KeyEvent.KEYCODE_BUTTON_THUMBL, false),
    R3("R3 (right stick press)", KeyEvent.KEYCODE_BUTTON_THUMBR, false),
    /**
     * The back buttons. AYN handhelds (Odin 2 / Portal / Mini, Thor) send M1 as "Z" and M2 as "C";
     * on other devices map the paddles to Z and C in the device's own button settings. (The names
     * stay BACK_Z / BACK_C so saved settings keep working.)
     */
    BACK_Z("M1 (back button)", KeyEvent.KEYCODE_BUTTON_Z, false),
    BACK_C("M2 (back button)", KeyEvent.KEYCODE_BUTTON_C, false),
    SELECT_START("Select + Start", KeyEvent.KEYCODE_BUTTON_START, true),
    SELECT_L2("Select + L2", KeyEvent.KEYCODE_BUTTON_L2, true),
    SELECT_R2("Select + R2", KeyEvent.KEYCODE_BUTTON_R2, true),
    /** Holding Select and pressing M1 / M2. */
    SELECT_M1("Select + M1", KeyEvent.KEYCODE_BUTTON_Z, true),
    SELECT_M2("Select + M2", KeyEvent.KEYCODE_BUTTON_C, true)
}

val DEFAULT_MUSIC_BUTTONS: Map<MusicAction, MusicButton> = mapOf(
    MusicAction.PLAY_PAUSE to MusicButton.SELECT_A,
    MusicAction.NEXT to MusicButton.SELECT_RIGHT,
    MusicAction.PREVIOUS to MusicButton.SELECT_LEFT,
    MusicAction.SEEK_BACK to MusicButton.NONE,
    MusicAction.SEEK_FORWARD to MusicButton.NONE
)

/** "PLAY_PAUSE=SELECT_A;NEXT=…" ⇄ map, for storing with the appearance settings. */
fun encodeMusicButtons(map: Map<MusicAction, MusicButton>): String =
    map.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }

fun decodeMusicButtons(saved: String?): Map<MusicAction, MusicButton> {
    if (saved.isNullOrBlank()) return DEFAULT_MUSIC_BUTTONS
    val parsed = saved.split(';').mapNotNull { part ->
        val (a, b) = part.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        val action = runCatching { MusicAction.valueOf(a) }.getOrNull() ?: return@mapNotNull null
        val button = runCatching { MusicButton.valueOf(b) }.getOrNull() ?: return@mapNotNull null
        action to button
    }.toMap()
    return DEFAULT_MUSIC_BUTTONS + parsed
}

/** The action bound to [keyCode] (with or without Select held), if any. */
fun Map<MusicAction, MusicButton>.actionFor(keyCode: Int, selectHeld: Boolean): MusicAction? =
    entries.firstOrNull { (_, b) -> b != MusicButton.NONE && b.keyCode == keyCode && b.withSelect == selectHeld }?.key
