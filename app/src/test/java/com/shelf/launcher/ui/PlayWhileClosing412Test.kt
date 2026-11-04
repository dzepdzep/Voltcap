package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 412: a game window that is already flying back (B) must not start the game: a quick A after a B (rapid A B A)
 * reached the Play button of the closing window and launched the game (found on the Thor, Cardboard box + Game shelf).
 */
class PlayWhileClosing412Test {
    private fun src(): String {
        val roots = listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/")
        return roots.map { File(it + "GameWindow.kt") }.first { it.exists() }.readText().replace("\r\n", "\n")
    }

    @Test fun playIsIgnoredOnceTheWindowIsClosing() {
        val s = src()
        assertTrue(s.contains("else if (closingFlag[0]) GamePopup.askAgain(winToken, g) else if (playAllowed("))  // (update 453: via askAgain)
        // v0.6.66 (D135): the release of the home screen's own A press is never a Play press.
        assertTrue(s.contains("val orphan = HomeKeyGuard.takeOrphanRelease()"))
        // Update 413: the home screen routes the window's close through GamePopup.closed so a pending A reopens it
        val ls = java.io.File(listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/").map { java.io.File(it + "LauncherScreen.kt") }.first { it.exists() }.path).readText()
        assertTrue(ls.contains("onClose = { GamePopup.closed(g) }"))
        assertTrue(ls.contains("GamePopup.reopenGame?.let"))
        assertTrue(s.contains("val close: () -> Unit = {"))
        // v0.6.66 (D133): a second close while it flies back takes a pending re-open back, then marks it closing.
        assertTrue(s.contains("if (closingFlag[0]) GamePopup.reopenRequested = false\n        closingFlag[0] = true"))
        // v0.6.66 (D133): a B the home screen gets while the window flies out closes it; an orphan B release is ignored.
        assertTrue(ls.contains("GamePopup.requestClose?.invoke()"))
        assertTrue(ls.contains("if (!HomeKeyGuard.bDown) return@onKeyEvent true"))
        // v0.6.66 (D132): the row-move windows take no button press while closing, and swallow the re-opening A's release.
        val rr = java.io.File(listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/").map { java.io.File(it + "RowRevealWindow.kt") }.first { it.exists() }.path).readText()
        assertTrue(rr.contains("live { RowReveal.reset(); onPlay() }"))
        assertTrue(rr.contains("swallowUp = e.key; reopen()"))
        // v0.6.66 (D135): the Shared move window (GlanceWindow) follows the same rules.
        val gw = java.io.File(listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/").map { java.io.File(it + "GlanceWindow.kt") }.first { it.exists() }.path).readText()
        assertTrue(gw.contains("live { RowReveal.reset(); onPlay() }"))
        assertTrue(gw.contains("swallowUp = e.key; reopen()"))
    }
}
