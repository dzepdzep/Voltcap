package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 414: the Cardboard box shows the cases of a box together (no dark waiting strips, no cases spawning one by one),
 * bounded, composed a few per frame, with the front / back pictures only decoded for the case you are on and its neighbours.
 */
class BoxReveal414Test {
    private fun src(name: String): String {
        val roots = listOf("src_copy/ui", "app/src/main/java/com/shelf/launcher/ui", "../src_copy/ui", "src/main/java/com/shelf/launcher/ui")
        return roots.map { File(it, name) }.first { it.exists() }.readText()
    }

    @Test fun revealGateIsBounded() {
        assertTrue(BOX_REVEAL_MAX_MS in 300L..1500L)
        assertTrue(BOX_REVEAL_FADE_MS in 0..200)
        assertTrue(BOX_COMPOSE_PER_FRAME in 1..8)
    }

    @Test fun boxCasesGoThroughTheGate() {
        val s = src("StyleCardboardBox.kt")
        assertTrue(s.contains("BoxReveal(inBox, animate, composed = upto >= inBox.size)"))
        // Update 416: a warm (seen before) case is never drawn before its spine is whole, and a warm box is composed in slices too
        assertTrue(s.contains("var whole by remember(k) { mutableStateOf(false) }"))
        assertTrue(s.contains("mutableIntStateOf(BOX_COMPOSE_FIRST)"))
        assertTrue(!s.contains("mutableStateOf(k in BoxReady.warm)"))
        assertTrue(s.contains("readyNeedsFront = false, faces = faces"))
        assertTrue(s.contains("rank[i] >= upto"))
        // (still the real Case3D, one object at rest and lifted)
        assertTrue(s.contains("Case3D(g, cover, physical"))
    }

    @Test fun case3dCanWaitForItsSpineAndSkipFaces() {
        val s = src("Case3D.kt")
        assertTrue(s.contains("onReady: (() -> Unit)? = null"))
        assertTrue(s.contains("spine !== SpineCache.WAITING"))
        assertTrue(s.contains("faces: Boolean = true"))
    }
}
