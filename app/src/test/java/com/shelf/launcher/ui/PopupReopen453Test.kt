package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Update 453: rapid A B A B on Live tiles / Dashboard / Hero (RP Classic) sometimes ended with the window open — an A that
 * reached the dying window after [GamePopup.closed] left a stale re-open request, and the NEXT close opened it again.
 */
class PopupReopen453Test {
    private val g = Game(name = "Advance Wars", platform = "GBA", hue = 1, sourceType = SourceType.ROM, romPath = "/r/aw.gba")

    @Before fun reset() { GamePopup.game = null; GamePopup.reopenGame = null; GamePopup.reopenRequested = false; GamePopup.current = null }

    @Test fun lateAAfterCloseReopensOnceAndLeavesNothingStale() {
        val w1 = Any(); GamePopup.game = g; GamePopup.opened(w1)
        GamePopup.closed(g)                       // B: window gone
        GamePopup.askAgain(w1, g)                 // A reaches the dying dialog after closed()
        assertEquals(g, GamePopup.reopenGame)     // acts at once
        assertFalse("no stale request for the next close", GamePopup.reopenRequested)
        GamePopup.reopenGame = null; GamePopup.game = g
        val w2 = Any(); GamePopup.opened(w2)
        GamePopup.closed(g)                       // the final B
        assertNull("final B leaves it closed", GamePopup.reopenGame)
    }

    @Test fun staleRequestIsClearedWhenAWindowOpens() {
        GamePopup.reopenRequested = true          // left over by old code / any path
        GamePopup.game = g; GamePopup.opened(Any())
        GamePopup.closed(g)
        assertNull(GamePopup.reopenGame)
    }

    @Test fun aWhileStillClosingStillReopens() {
        val w = Any(); GamePopup.game = g; GamePopup.opened(w)
        GamePopup.askAgain(w, g)                  // D108: A while it flies back
        assertTrue(GamePopup.reopenRequested)
        GamePopup.closed(g)
        assertEquals(g, GamePopup.reopenGame)
    }

    @Test fun lateBCancelsAPendingReopen() {
        val w = Any(); GamePopup.game = g; GamePopup.opened(w)
        GamePopup.closed(g); GamePopup.askAgain(w, g)
        GamePopup.cancelAgain(w)
        assertNull(GamePopup.reopenGame)
    }

    @Test fun lateKeyOnAnOldWindowNeverTouchesTheNewOne() {
        val old = Any(); GamePopup.game = g; GamePopup.opened(old); GamePopup.closed(g)
        GamePopup.game = g; val now = Any(); GamePopup.opened(now)
        GamePopup.askAgain(old, g)
        assertFalse(GamePopup.reopenRequested); assertNull(GamePopup.reopenGame)
        GamePopup.cancelAgain(old)
        assertEquals(g, GamePopup.game)
    }
}

/** Update 453: the Classic window (VoltPanel) fits what it shows — its stripe no longer stretches it to the maximum height. */
class VoltPanelFit453Test {
    @Test fun stripeDoesNotStretchTheWindow() {
        val roots = listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/")
        val s = roots.map { java.io.File(it + "WinScenesHomes2.kt") }.first { it.exists() }.readText().replace("\r\n", "\n")
        val v = s.substring(s.indexOf("internal fun VoltPanel(ctx: WinSceneCtx)")).let { it.substring(0, it.indexOf("KeyRow(")) }
        assertFalse(v.contains("fillMaxHeight()"))
        assertTrue(v.contains(".drawBehind { drawRect(Brush.verticalGradient(listOf(a, a.copy(alpha = 0.2f)))"))
    }
}
