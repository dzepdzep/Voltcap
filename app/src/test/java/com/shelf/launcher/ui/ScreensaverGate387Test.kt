package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 387 (D84): the idle timer never runs over the profile selector (PIN pad included) or while it is about to show. */
class ScreensaverGate387Test {
    private fun run(
        dialog: Boolean = false, search: Boolean = false, saver: Boolean = false, edit: Boolean = false,
        selector: Boolean = false, pending: Boolean = false, active: Boolean = true, enabled: Boolean = true
    ) = ScreensaverGate.mayRun(active, enabled, dialog, search, saver, edit, selector, pending)

    @Test fun idleHomeMayRun() = assertTrue(run())
    @Test fun neverOverTheProfileSelector() = assertFalse(run(selector = true))
    @Test fun neverWhileTheStartupSelectorWaitsForTheIntro() = assertFalse(run(pending = true))
    @Test fun existingGuardsStillBlock() {
        assertFalse(run(dialog = true)); assertFalse(run(search = true)); assertFalse(run(saver = true))
        assertFalse(run(edit = true)); assertFalse(run(active = false)); assertFalse(run(enabled = false))
    }
    @Test fun runsAgainOnceTheSelectorCloses() {
        assertFalse(run(selector = true))
        assertTrue(run(selector = false)) // the effect re-keys on whoAsk, so the full delay restarts from here
    }
}
