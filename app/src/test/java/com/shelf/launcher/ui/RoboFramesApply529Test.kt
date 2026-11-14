package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.6.72 RC (CI run 43632198774, 91 Linux-only failures): Robolectric tests that drive Compose frame by frame with the manual
 * clock must send the global snapshot's apply notifications each frame. Without it a click's state change reaches the screen
 * only if the shared GlobalSnapshotManager still works in that JVM, which a looper reset between classes can stop - so the
 * result depended on test-class order (Windows alphabetical, Linux directory order). See ShelfTextRoboTest.frames.
 */
class RoboFramesApply529Test {
    @Test fun everyManualFrameSendsApplyNotifications() {
        val dir = listOf("src/test/java/com/shelf/launcher/ui", "app/src/test/java/com/shelf/launcher/ui").map(::File).first { it.isDirectory }
        val bad = dir.listFiles { f -> f.name.endsWith(".kt") && f.name != "RoboFramesApply529Test.kt" }.orEmpty().flatMap { f ->
            f.readLines().withIndex().filter { (_, l) -> "mainClock.advanceTimeByFrame()" in l && "Snapshot.sendApplyNotifications()" !in l }
                .map { (i, l) -> "${f.name}:${i + 1}: ${l.trim()}" }
        }
        assertTrue("frame steps without Snapshot.sendApplyNotifications():\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
