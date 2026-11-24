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
    /** 682ar (D391): how much longer the screensaver waits - the idle time counted both from when the wait began (the home's
     *  own input restarts it) and from the last input of any kind, a game window's keys included ([HomeIdle.sinceAnyInputMs]). */
    fun waitMs(idleMs: Long, sinceStartMs: Long, sinceAnyInputMs: Long): Long = maxOf(idleMs - sinceStartMs, idleMs - sinceAnyInputMs)
}
