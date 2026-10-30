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
        assertTrue(s.contains("val onPlay: () -> Unit = { if (closingFlag[0]) GamePopup.reopenRequested = true else if (playAllowed("))
        // Update 413: the home screen routes the window's close through GamePopup.closed so a pending A reopens it
        val ls = java.io.File(listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/").map { java.io.File(it + "LauncherScreen.kt") }.first { it.exists() }.path).readText()
        assertTrue(ls.contains("onClose = { GamePopup.closed(g) }"))
        assertTrue(ls.contains("GamePopup.reopenGame?.let"))
        assertTrue(s.contains("val close: () -> Unit = {\n        closingFlag[0] = true"))
    }
}
