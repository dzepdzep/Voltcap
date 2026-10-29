package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 401: the Pocket ribbon stops at its first / last game (no Endless wraparound); other layouts keep theirs. */
class Ribbon401Test {
    @Test fun ribbonHasNoEndless() {
        assertFalse(endlessApplies(HomeLayout.POCKET_RIBBON))
    }
    @Test fun otherLayoutsKeepEndless() {
        assertTrue(endlessApplies(HomeLayout.SHELF))
        assertTrue(endlessApplies(HomeLayout.POCKET_CLASSIC))
        assertFalse(endlessApplies(HomeLayout.HERO_TILES))
    }
}
