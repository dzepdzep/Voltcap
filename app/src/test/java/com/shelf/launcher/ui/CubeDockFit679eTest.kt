package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 679e (KONKR x1.3 k679p13): the Cube dock label never clips its "L2 · R2" row - it goes to one line when the height is short. */
class CubeDockFit679eTest {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun shortDockGoesCompactInsteadOfClipping() {
        val s = src("StyleCube.kt")
        assertTrue(s.contains("val compact = cs.hasBoundedHeight && (tall.maxOfOrNull { it.height } ?: 0) > cs.maxHeight"))
        assertTrue(s.contains("if (compact && multi) {\n            Row(verticalAlignment = Alignment.CenterVertically) { count(); Spacer(Modifier.width(10.dp)); turn() }"))
        // the probe is measured only and never read out / dumped
        assertTrue(s.contains("clearAndSetSemantics { statusProbe = true }"))
    }
}
