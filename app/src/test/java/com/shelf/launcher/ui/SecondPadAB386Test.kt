package com.shelf.launcher.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 386: SecondPad.key swallows the key-up of every code in keysDown at the top of the function. A and B act on the
 * key-up, so recording their key-down made both dead (A never pressed the lit control, B never left the second screen).
 * Source-level check: the A and B branches must not add to keysDown (the handler needs android.view.KeyEvent, not available
 * in a plain JVM test).
 */
class SecondPadAB386Test {
    private val src: String = run {
        val rel = "src/main/java/com/shelf/launcher/ui/SecondPad.kt"
        listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    private fun branch(startMarker: String): String {
        val a = src.indexOf(startMarker)
        assertTrue("$startMarker not found", a >= 0)
        val end = Regex("true\\s*\\}").find(src, a)!!.range.first
        return src.substring(a, end)
    }

    @Test fun aActsOnKeyUpWithoutRecordingTheDown() {
        val b = branch("Key.ButtonA, Key.Enter, Key.DirectionCenter ->")
        assertTrue(b.contains("if (up)"))
        assertTrue("A records keysDown: its key-up is swallowed", !b.contains("keysDown"))
    }

    @Test fun bActsOnKeyUpWithoutRecordingTheDown() {
        val b = branch("Key.ButtonB, Key.Back ->")
        assertTrue(b.contains("leave()"))
        assertTrue("B records keysDown: its key-up is swallowed", !b.contains("keysDown"))
    }
}
