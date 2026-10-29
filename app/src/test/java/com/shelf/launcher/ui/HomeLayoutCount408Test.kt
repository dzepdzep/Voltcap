package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Update 408: the actual HomeLayout count (53 vs 54 reconciliation) and the Channel grid's own Wii window. */
class HomeLayoutCount408Test {
    @Test fun layoutCountAndNames() {
        println("HOMELAYOUTS ${HomeLayout.values().size}: " + HomeLayout.values().joinToString(" ") { it.name })
        assertEquals(53, HomeLayout.values().size)
        assertEquals(HomeLayout.values().size, HomeLayout.values().map { it.name }.toSet().size)
    }

    @Test fun channelGridHasItsOwnWiiWindow() {
        assertEquals(WinScene.WII_PREVIEW, winSceneFor(HomeLayout.CHAN))
    }
}
