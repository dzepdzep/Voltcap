package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 398: no artwork / text spawning — warmed backdrops are in place at once, an instant swap drops the old content in the same frame. */
class Spawning398Test {
    @Test
    fun fullScreenBackdropsShareOneRequestWithTheirWarmUp() {
        for (f in listOf("StyleArtLibrary.kt", "StyleCoverDetails.kt", "StyleCinema.kt", "StyleArctic.kt", "StyleDashboard.kt")) {
            val s = src("ui/$f") ?: continue
            assertTrue("$f: HeldImage backdrop must be shared = true", s.contains("shared = true"))
        }
        for (f in listOf("StyleArtLibrary.kt", "StyleCoverDetails.kt")) {
            val s = src("ui/$f") ?: continue
            assertTrue("$f must warm the neighbours with warmHeld", s.contains("warmHeld("))
        }
    }

    @Test
    fun aCachedPictureNeverFadesIn() {
        val m = src("ui/Motion.kt") ?: return
        assertTrue(m.contains("!l.quick"))
        assertTrue(m.contains("instant = { m -> shared && m is String && heldCached(context, m) }"))
    }

    @Test
    fun categorySwapsDropTheOldContentInTheSameFrame() {
        val h = src("ui/HomeExtras.kt") ?: return
        val body = h.substringAfter("fun <S> androidx.compose.animation.AnimatedContentTransitionScope<S>.categoryQuiet()").substringBefore("/**")
        assertTrue("categoryQuiet must use quietSwap (a None/None keeps the old content one frame)", body.contains("quietSwap()"))
        assertTrue(h.contains("fun quietSwap()") && h.contains("fadeOut(androidx.compose.animation.core.snap())"))
    }

    @Test
    fun spineTopFaceIsPartOfTheCoverShelf() {
        val s = src("ui/StyleCoverDetails.kt") ?: return
        assertTrue(s.contains("drawCaseTop("))
        assertTrue((src("ui/SpineDepth.kt") ?: return).contains("fun DrawScope.drawCaseTop"))
    }

    private fun src(rel: String): String? {
        val roots = listOf("src_copy", "../src_copy", "../../src_copy", "app/src/main/java/com/shelf/launcher", "src/main/java/com/shelf/launcher")
        val root = roots.map { File(it) }.firstOrNull { File(it, "ui/Motion.kt").exists() } ?: return null
        return File(root, rel).takeIf { it.exists() }?.readText()
    }
}
