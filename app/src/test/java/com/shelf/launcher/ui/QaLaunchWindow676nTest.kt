package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 676n: the supervised launch window opens only plain apps that are listed, only for a short while; games / ROMs never. */
class QaLaunchWindow676nTest {
    private val now = 1_800_000_000L
    @Test fun narrow() {
        assertTrue(QaLaunchWindow.parse("com.android.deskclock $now".replace("$now", "${now + 300}"), "vm.app", "com.android.deskclock", now))
        assertTrue(QaLaunchWindow.parse("a.b,com.android.deskclock ${now + 300}", "launcher.app", "com.android.deskclock", now))
        assertFalse(QaLaunchWindow.parse("com.android.deskclock ${now + 300}", "vm.app", "com.other", now))           // not listed
        assertFalse(QaLaunchWindow.parse("com.android.deskclock ${now - 1}", "vm.app", "com.android.deskclock", now))   // expired
        assertFalse(QaLaunchWindow.parse("com.android.deskclock ${now + 3600}", "vm.app", "com.android.deskclock", now)) // too long
        for (path in listOf("vm.launch", "vm.launchNow", "launcher.game", "startGame", "resume", "screens.app", "hero"))
            assertFalse(path, QaLaunchWindow.parse("com.android.deskclock ${now + 300}", path, "com.android.deskclock", now))
        assertFalse(QaLaunchWindow.parse("garbage", "vm.app", "com.android.deskclock", now))
    }

    @Test fun onlyInsideTheIsolatedBranchAndBeforeItsBlock() {
        val s = listOf("src/main/java/com/shelf/launcher/ui/TestHooks.kt", "app/src/main/java/com/shelf/launcher/ui/TestHooks.kt").map { File(it) }.first { it.exists() }.readText()
        val i = s.indexOf("if (com.shelf.launcher.data.QaVariant.isolatedNow) {")
        val block = s.substring(i, s.indexOf("return true", i))
        assertTrue(block.contains("QaLaunchWindow.allows(where, key, QaClip.appLaunchWindow)"))
    }
}
