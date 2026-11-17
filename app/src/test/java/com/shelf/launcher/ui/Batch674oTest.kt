package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 674o: KONKR x1.3 k674n Library home "R2" 6 px high in its chip; emulator build ABI kept out of handheld builds. */
class Batch674oTest {
    private fun file(vararg c: String): String = c.map { File(it) }.first { it.exists() }.readText()
    private fun src(name: String) = file("../src_copy/ui/$name", "src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")

    @Test fun libraryHintsCentreTheirInk() {
        val s = src("StyleLibrary.kt")
        val hint = s.substring(s.indexOf("private fun LibraryHint"), s.indexOf("private fun LibraryPanel"))
        assertTrue(hint.contains("InkGlyph(text"))
        assertTrue(!hint.contains("Text(text"))
    }

    @Test fun emulatorAbiOnlyWithTheDiscordStub() {
        val g = file("build.gradle.kts", "app/build.gradle.kts")
        assertTrue(g.contains("if (voltcapCiWithoutDiscord && providers.gradleProperty(\"voltcapEmulatorAbi\").orNull == \"true\") abiFilters += \"x86_64\""))
        // the release guard still refuses the stub for any release task
        assertTrue(g.contains("CI-only Discord stub must never be used for release builds."))
    }
}
