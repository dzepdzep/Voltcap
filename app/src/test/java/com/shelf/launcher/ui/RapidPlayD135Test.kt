package com.shelf.launcher.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v0.6.66 (D135): on the GT78, Cross menu + Shared move, A B A B at ~250 ms started the game. The home screen took the A press
 * that opened the window; its release reached the window ~0.6 s later (past the 450 ms Play guard) and clicked ▶ Play.
 * Key ownership: a Play click while the home screen still holds an A press is that release, never a Play press.
 */
class RapidPlayD135Test {
    @Before fun reset() { HomeKeyGuard.aDown = false }

    @Test fun theReleaseOfTheOpeningAIsNotAPlayPress() {
        HomeKeyGuard.trackA(Key.ButtonA, KeyEventType.KeyDown)    // home: A opens the window
        assertTrue(HomeKeyGuard.takeOrphanRelease())              // window: the release clicks Play → ignored
        assertFalse(HomeKeyGuard.takeOrphanRelease())             // taken once
    }

    @Test fun anAPressedInsideTheWindowPlays() {
        HomeKeyGuard.trackA(Key.ButtonA, KeyEventType.KeyDown)
        HomeKeyGuard.aDown = false                                // window saw its own A press (dialog preview)
        assertFalse(HomeKeyGuard.takeOrphanRelease())
    }

    @Test fun aReleaseTheHomeScreenGotItselfLeavesNothingBehind() {
        HomeKeyGuard.trackA(Key.ButtonA, KeyEventType.KeyDown)
        HomeKeyGuard.trackA(Key.ButtonA, KeyEventType.KeyUp)
        assertFalse(HomeKeyGuard.takeOrphanRelease())
    }

    @Test fun rapidABABSequenceNeverPlays() {
        // A (home press → window opens), its release lands in the window, B closes, A again from home, release in the window, B.
        var plays = 0
        repeat(2) {
            HomeKeyGuard.trackA(Key.ButtonA, KeyEventType.KeyDown)
            if (!HomeKeyGuard.takeOrphanRelease()) plays++        // the release clicking Play
            HomeKeyGuard.trackA(Key.ButtonB, KeyEventType.KeyDown) // B is not an open key
        }
        assertTrue(plays == 0)
    }

    @Test fun otherKeysAreNotTracked() {
        HomeKeyGuard.trackA(Key.DirectionRight, KeyEventType.KeyDown)
        assertFalse(HomeKeyGuard.aDown)
        assertTrue(HomeKeyGuard.isOpenKey(Key.Enter) && HomeKeyGuard.isOpenKey(Key.DirectionCenter))
    }
}
