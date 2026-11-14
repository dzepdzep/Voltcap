package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.6.72 RC gate k672c (KONKR, Cardboard box, A B A B at 250 ms): the A while the window flew back reopened it the box's way
 * (the case pulled again, the window a frame or more later); a B in that gap went to the box's own Back and the window then
 * opened over the console list and stayed. The home screen's B now takes such a pull back.
 */
class BoxRapidB0672Test {
    @Test fun bTakesAPullWhoseWindowIsNotUpYet() {
        assertTrue(homeBTakesBoxPull(windowUp = false, pull = "game"))
        assertEquals(false, homeBTakesBoxPull(windowUp = true, pull = "game"))   // the window takes B itself (requestClose)
        assertEquals(false, homeBTakesBoxPull(windowUp = false, pull = null))    // nothing on its way: the box's own Back
    }

    @Test fun homeKeyHandlerUsesItBeforeTheBoxsOwnBack() {
        val f = listOf("src/main/java/com/shelf/launcher/ui/LauncherScreen.kt", "app/src/main/java/com/shelf/launcher/ui/LauncherScreen.kt")
            .map(::File).first { it.isFile }
        val s = f.readText().replace("\r\n", "\n")
        val take = s.indexOf("if (homeBTakesBoxPull(GamePopup.game != null, BoxNav.pull)")
        val reopen = s.indexOf("cancels reopen\")")
        val guard = s.indexOf("if (event.type == KeyEventType.KeyDown) HomeKeyGuard.bDown = true")
        assertTrue("box pull check missing", take > 0)
        assertTrue("it must come after the pending-reopen check and before the home's own B", take in (reopen + 1) until guard)
        assertTrue(s.substring(take, guard).contains("BoxNav.pull = null"))
    }
}
