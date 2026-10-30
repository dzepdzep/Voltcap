package com.shelf.launcher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 406 / 410: the FULL build (the main release) declares the notification listener; the optional compat build removes it
 * and must not carry anything else Play Protect's sideload protection blocks. Both keep the same package id and signing config.
 */
class PlayProtect406Test {
    private val main = File("src/main/AndroidManifest.xml").readText().replace(Regex("(?s)<!--.*?-->"), "")
    private val compat = File("src/compat/AndroidManifest.xml").readText().replace(Regex("(?s)<!--.*?-->"), "")
    private val gradle = File("build.gradle.kts").readText()
    private val banned = listOf("BIND_ACCESSIBILITY_SERVICE", "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "AccessibilityService")

    @Test fun fullManifestDeclaresTheListenerAndNothingElseBlocked() {
        assertTrue(main.contains("BIND_NOTIFICATION_LISTENER_SERVICE"))
        assertTrue(main.contains(".data.ShelfNotificationListener"))
        banned.forEach { assertFalse("main manifest declares $it", main.contains(it)) }
    }

    @Test fun compatManifestRemovesTheListener() {
        assertTrue(compat.contains(".data.ShelfNotificationListener"))
        assertTrue(compat.contains("tools:node=\"remove\""))
        assertFalse(compat.contains("BIND_NOTIFICATION_LISTENER_SERVICE"))
    }

    @Test fun compatBuildTypeSharesTheReleaseIdentity() {
        val block = gradle.substringAfter("create(\"compat\")").substringBefore("}")
        assertTrue("compat starts from release (same signing config)", block.contains("initWith(getByName(\"release\"))"))
        assertFalse("no applicationIdSuffix: same package id", gradle.contains("applicationIdSuffix"))
        assertFalse("the old notify build type is gone", gradle.contains("create(\"notify\")"))
    }

    /** Update 407: in the compat build nobody can grant notification access, so no screen may just say "Needs notification access". */
    @Test fun screensDoNotAskForAnAccessTheCompatBuildCannotGive() {
        val ui = File("src/main/java/com/shelf/launcher/ui")
        val bad = ui.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) ->
                l.contains("Needs notification access") && !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*")
            }.map { "${f.name}:${it.index + 1}" }
        }.toList()
        val allowed = bad.filter { it.startsWith("PerfSettingsCard") }
        assertTrue("bare 'Needs notification access' in $bad", (bad - allowed.toSet()).isEmpty())
    }

    @Test fun mediaSessionReadsStayBehindTheListenerCheck() {
        val src = File("src/main/java/com/shelf/launcher/data/ShelfNotificationListener.kt").readText()
        assertTrue(src.contains("fun requirementNote"))
        val body = src.substringAfter("fun anyBusy(context: Context): Boolean {").substringBefore("return runCatching")
        assertTrue("anyBusy must return early without access", body.contains("if (!hasAccess(context)) return false"))
    }
}
