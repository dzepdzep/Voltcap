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
}
