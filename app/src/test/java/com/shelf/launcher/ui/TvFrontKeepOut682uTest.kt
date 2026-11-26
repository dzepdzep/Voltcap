package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 682u (owner 18 Nov 21:28, permanent visual rule R27): a foreground character never covers a popup, its text or its keys.
 * The TV room's figure stands in front of the set's screen (TvRoomArt.front over TvRoomArt.screen): the open window keeps its
 * text and keys out of the figure's corner, the panel under the set starts past it, and the figure is drawn in front where it
 * stands - never moved, hidden or cut by the window.
 */
class TvFrontKeepOut682uTest {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    private fun rel(scale: Float): Pair<Rect, Rect> {
        val s = TvRoomArt.screen; val f = TvRoomArt.front
        return Rect(0f, 0f, s.width * scale, s.height * scale) to
            Rect((f.left - s.left) * scale, (f.top - s.top) * scale, (f.right - s.left) * scale, (f.bottom - s.top) * scale)
    }

    @Test fun theWindowKeepsOutOfTheFiguresCorner() {
        for (scale in listOf(0.46f, 0.66f, 1f, 1.4f)) {
            val (scr, fr) = rel(scale)
            val k = tvFrontKeepOut(scr.width, scr.height, fr, 6f)
            // the text and keys start past the figure's right edge, the figure reaching into the screen
            assertTrue("x$scale: window text starts at ${k.screenStart}, the figure ends at ${fr.right}", k.screenStart >= fr.right)
            assertTrue("x$scale: no more than 30 % of the screen", k.screenStart <= scr.width * 0.3f + 0.01f)
            // the figure reaches below the screen: the panel under the set starts past it too
            assertTrue(fr.bottom > scr.height)
            assertTrue("x$scale: panel starts at ${k.panelStart}", k.panelStart >= fr.right)
        }
    }

    @Test fun aFigureOutsideTheScreenChangesNothing() {
        val k = tvFrontKeepOut(400f, 300f, Rect(-120f, 50f, -60f, 200f), 6f)
        assertEquals(0f, k.screenStart); assertEquals(-6f, k.panelStart)
    }

    @Test fun theOpenWindowDrawsTheFigureInFrontAndKeepsOut() {
        val w = src("GameWindow.kt")
        assertTrue(w.contains("TvScreen.front?.let { tvFrontKeepOut(tvRect.width, tvRect.height, it,"))
        assertTrue(w.contains("Modifier.fillMaxSize().padding(start = (keep?.screenStart ?: 0f).toDp())"))
        assertTrue(w.contains("TvFrontFigure(Rect(tvRect.left + fr.left"))
        // the home tells the window where the figure stands, relative to the screen
        assertTrue(src("TvRoomHome.kt").contains("TvScreen.front = androidx.compose.ui.geometry.Rect(fr.left - scr.left"))
    }
}
