package com.shelf.launcher.ui

/**
 * Update 387 (D84): when the idle timer may run. The profile selector ("Who's playing?", its PIN pad and the hand-over after a
 * pick or skip) was in none of the guards, so the screensaver could start over it. Every input there is also handled in the
 * selector, so the timer ran from the last home-screen press.
 */
internal object ScreensaverGate {
    fun mayRun(
        active: Boolean, enabled: Boolean, dialogOpen: Boolean, searchOpen: Boolean, saverOn: Boolean, editingHome: Boolean,
        profileSelectorUp: Boolean, profileSelectorPending: Boolean
    ): Boolean = active && enabled && !dialogOpen && !searchOpen && !saverOn && !editingHome &&
        !profileSelectorUp && !profileSelectorPending
}
