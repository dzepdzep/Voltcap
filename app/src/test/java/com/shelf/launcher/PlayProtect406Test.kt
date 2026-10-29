package com.shelf.launcher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 406: the standard manifest must never carry what Play Protect's sideload fraud protection blocks. */
class PlayProtect406Test {
    private val main = File("src/main/AndroidManifest.xml").readText()
    private val notify = File("src/notify/AndroidManifest.xml").readText()
    private val banned = listOf(
        "BIND_NOTIFICATION_LISTENER_SERVICE", "BIND_ACCESSIBILITY_SERVICE",
        "android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "AccessibilityService"
    )

    @Test fun standardManifestHasNoBlockedCapability() {
        // comments may mention the names; strip them before checking
        val code = main.replace(Regex("(?s)<!--.*?-->"), "")
        banned.forEach { assertFalse("main manifest declares $it", code.contains(it)) }
    }

    @Test fun listenerOnlyInTheOptionalNotifyManifest() {
        assertTrue(notify.contains("BIND_NOTIFICATION_LISTENER_SERVICE"))
        assertTrue(notify.contains("com.shelf.launcher.data.ShelfNotificationListener"))
    }
    /** Update 407: in the standard build nobody can grant notification access, so no screen may just say "Needs notification access". */
    @Test fun screensDoNotAskForAnAccessTheStandardBuildCannotGive() {
        val ui = File("src/main/java/com/shelf/launcher/ui")
        val bad = ui.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) ->
                l.contains("Needs notification access") && !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*")
            }.map { "${f.name}:${it.index + 1}" }
        }.toList()
        // PerfSettingsCard keeps the sentence only on the isBuiltIn branch (same line as the check); anything else must use requirementNote()
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
