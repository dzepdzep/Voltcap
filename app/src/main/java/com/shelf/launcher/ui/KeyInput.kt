package com.shelf.launcher.ui

/**
 * Update 233 (#120): whether the last thing you did was a button press (not a touch). After a touch Android is in "touch
 * mode": a window opened with A then showed no highlight, the first ◀ / ▶ only lit Play (Android eats that press to
 * leave touch mode) — so A, ▶, A started the game instead of opening Performance. Windows opened by a button leave touch
 * mode as they open ([leaveTouchMode]) so the first press already moves.
 */
internal object KeyInput {
    @Volatile var lastWasKey: Boolean = false

    /** Opened by a button press while Android is in touch mode: leave it, so the window's buttons can take the focus now. */
    fun leaveTouchMode(view: android.view.View) {
        if (lastWasKey && view.isInTouchMode) runCatching { view.requestFocusFromTouch() }
    }
}
