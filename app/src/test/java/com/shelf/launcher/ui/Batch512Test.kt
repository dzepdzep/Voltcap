package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 512 (staging only): release identity, test hooks off in release builds. (512's Ken Burns steps were reverted in 513 —
 * see Batch513Test.)
 */
class Batch512Test {
    private fun src(rel: String): String = listOf(File("../src_copy/$rel"), File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText()
    private fun gradle(): String = listOf(File("build.gradle.kts"), File("app/build.gradle.kts"), File("../project_copy/app/build.gradle.kts"))
        .first { it.exists() }.readText()

    // (Batch 517: the security hotfix 0.6.69 / 76 — higher than the published 0.6.68 / 75 so it updates over it.)
    // (12 Nov: the published release is 0.6.70 / 77 — the bump left this test at 0.6.69 / 76, failing on v0.6.70 itself.)
    // (Update 529, v0.6.71 / 78: it failed again on the bump. It now checks what it is for — name and code move together
    // (0.6.N goes with code N + 7, as every release since 0.6.68 / 75) and never fall below the last published 0.6.70 / 77.)
    @Test fun releaseIdentityNameAndCodeMoveTogether() {
        val g = gradle()
        // (QA builds may rename themselves with a suffix; the code is the release's.)
        val code = Regex("versionCode = (\\d+)\\b").find(g)!!.groupValues[1].toInt()
        val patch = Regex("versionName = \"0\\.6\\.(\\d+)").find(g)!!.groupValues[1].toInt()
        assertEquals(patch + 7, code)
        assertTrue(code >= 77)
        assertTrue(g.contains("namespace = \"com.shelf.launcher\""))
    }

    @Test fun hooksFileCountsOnlyInDebuggableBuilds() {
        assertFalse(TestHooks.hooksAllowedIn(0))
        assertTrue(TestHooks.hooksAllowedIn(android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE))
        val t = src("ui/TestHooks.kt")
        // One reading of the hooks file, behind the build check; no other hook checks the file on its own.
        assertEquals(1, Regex("\"voltcap_test_hooks\"\\)").findAll(t).count())
        assertTrue(t.contains("hooksAllowedIn(context.applicationContext.applicationInfo.flags) &&"))
        // The launch block and QA logging follow TestHooks.enabled (QaTrace.on) or the isolated QA app only.
        assertTrue(src("ui/QaTrace.kt").contains("on = TestHooks.enabled(activity)"))
        assertTrue(t.contains("fun launchBlocked(): Boolean = com.shelf.launcher.data.QaVariant.isolatedNow || !QaClip.allowLaunchFile()"))
        assertTrue(t.contains("if (!QaTrace.on) return false"))
    }
}
