package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Update 678p (D333, KONKR traces k678c2 / k678tv): rapid A B A B left the TV room / Record crate window open ~1 round in 10. The
 * second A reached the closing window ("win reopen on press"), it finished closing with reopen=true and the game went to a NEW
 * window a frame later - the last B fell between the two dialogs and reached neither. A window that reaches the end of its
 * close with an A pending now opens again IN PLACE (the same dialog keeps the keys): no hand-over to a new window, its opening
 * replayed (openSeq), its Play guard restarted. Fails on 678o (no takeReopenInPlace; the effects run once).
 */
class ReopenInPlace678pTest {
    private val g = Game(name = "Shadow of the Colossus", platform = "PS2", hue = 1, sourceType = SourceType.ROM, romPath = "/r/sotc.iso")

    @Before fun reset() { GamePopup.game = null; GamePopup.reopenGame = null; GamePopup.reopenRequested = false; GamePopup.current = null }

    @Test fun aWhileClosingOpensTheSameWindowAgain() {
        val w = Any(); GamePopup.game = g; GamePopup.opened(w)
        GamePopup.askAgain(w, g)                          // A while it flies back
        assertTrue(GamePopup.takeReopenInPlace(w))         // the end of the close: open again here
        assertFalse("taken once", GamePopup.takeReopenInPlace(w))
        assertEquals("the game stays in the same window", g, GamePopup.game)
        assertNull("no hand-over to a new window", GamePopup.reopenGame)
    }

    @Test fun theLastBStillCancelsAndTheWindowCloses() {
        val w = Any(); GamePopup.game = g; GamePopup.opened(w)
        GamePopup.askAgain(w, g)                          // A
        GamePopup.cancelAgain(w)                          // the last B, still on the same (closing) window
        assertFalse(GamePopup.takeReopenInPlace(w))
        GamePopup.closed(g)
        assertNull(GamePopup.game); assertNull(GamePopup.reopenGame)
    }

    @Test fun anOldWindowNeverReopensOverANewOne() {
        val old = Any(); GamePopup.game = g; GamePopup.opened(old); GamePopup.askAgain(old, g)
        val now = Any(); GamePopup.opened(now)              // a newer window took over
        assertFalse(GamePopup.takeReopenInPlace(old))
    }

    private fun src(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun bothClosePathsReopenInPlaceBeforeHandingBack() {
        val s = src("GameWindow.kt")
        val c = s.substring(s.indexOf("    val close: () -> Unit = {"), s.indexOf("    val closeLatest by rememberUpdatedState(close)"))
        assertEquals("the scene trip and the piece's trip both reopen in place", 2, Regex("""if \(GamePopup\.takeReopenInPlace\(winToken\)\)""").findAll(c).count())
        assertTrue("before the hand-back / close", c.indexOf("takeReopenInPlace") < c.indexOf("pickupHandBack()"))
        assertTrue("the Play guard restarts", Regex("""openedAt\[0\] = android\.os\.SystemClock\.uptimeMillis\(\)""").findAll(c).count() == 2)
        assertTrue(c.contains("motion.started = false") && c.contains("motion.closing = false"))
        for (k in listOf("LaunchedEffect(target != null, openSeq)", "LaunchedEffect(slot != null, motion, openSeq)", "LaunchedEffect(openSeq) { if (!anim && !tvFlight)"))
            assertTrue("the opening replays on openSeq: $k", s.contains(k))
    }
}
