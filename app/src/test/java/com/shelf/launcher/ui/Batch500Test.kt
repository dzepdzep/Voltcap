package com.shelf.launcher.ui

import com.shelf.launcher.data.QaVariant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 500 (staging only): the isolated QA app "Voltcap QA" (D295 permanent fix). */
class Batch500Test {
    private fun app(): File = listOf(File("src"), File("app/src")).first { File(it, "main").exists() }.absoluteFile.parentFile
    private fun src(rel: String): String = File(app(), "src/main/java/com/shelf/launcher/$rel").readText()

    @Test fun onlyTheQaPackageIsIsolated() {
        assertTrue(QaVariant.isolatedPackage("com.shelf.launcher.qa"))
        assertFalse(QaVariant.isolatedPackage("com.shelf.launcher"))
        assertFalse(QaVariant.isolatedPackage(null))
        assertFalse(QaVariant.isolatedNow) // nothing asked with a context in a JVM test: the ordinary app's behaviour
    }

    @Test fun qaManifestDropsEverythingThatReachesOutside() {
        val m = File(app(), "src/qa/AndroidManifest.xml").readText()
        for (s in listOf("MANAGE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "WRITE_SECURE_SETTINGS", "WRITE_SETTINGS",
            "REQUEST_INSTALL_PACKAGES", "REQUEST_DELETE_PACKAGES", "RECEIVE_BOOT_COMPLETED", ".HomeAlias", ".data.UpdatedReceiver",
            ".device.ChargeLimitBootReceiver", ".data.ShelfNotificationListener"))
            assertTrue(s, Regex("""android:name="(android\.permission\.)?${Regex.escape(s)}"\s+tools:node="remove"""").containsMatchIn(m))
        assertTrue(File(app(), "src/qa/res/values/strings.xml").readText().contains(">Voltcap QA<"))
        val g = File(app(), "build.gradle.kts").readText()
        assertTrue(g.contains("applicationIdSuffix = \".qa\""))
        // debug is only the QA app with the opt-in file; otherwise unchanged.
        assertTrue(g.contains("if (debugIsQa) getByName(\"debug\")"))
    }

    @Test fun isolatedAppNeverSyncsLaunchesOrChargeLimits() {
        assertTrue(src("data/CloudSync.kt").contains("(QaVariant.isolated(context) || runCatching { com.shelf.launcher.ui.TestHooks.enabled"))
        val h = src("ui/TestHooks.kt")
        val i = h.indexOf("fun interceptLaunch(")
        assertTrue(i > 0 && h.indexOf("QaVariant.isolatedNow", i) in i..(i + 300))
        assertTrue(h.contains("fun launchBlocked(): Boolean = com.shelf.launcher.data.QaVariant.isolatedNow ||"))
        assertTrue(src("device/ChargeLimiter.kt").contains("if (registered || com.shelf.launcher.data.QaVariant.isolated(context)) return"))
        assertTrue(src("data/HomeLauncher.kt").contains("if (QaVariant.isolated(context)) return"))
        val main = src("MainActivity.kt")
        assertTrue(main.indexOf("QaVariant.isolated(this)") in 0 until main.indexOf("StartupPrewarm.start(this)"))
    }

    @Test fun fixtureLibraryOnlyInTheQaApp() {
        val h = src("ui/TestHooks.kt")
        assertTrue(h.contains("getBooleanExtra(\"voltcap_test_fixture_roms\", false) == true && com.shelf.launcher.data.QaVariant.isolated(context)"))
        assertTrue(h.contains("context.getExternalFilesDir(\"roms\")"))
    }

    @Test fun isolatedAppNeverUsesTheVendorRootServiceOrStickLights() {
        assertTrue(src("device/RootShell.kt").contains("private fun lookup(): IBinder? = if (com.shelf.launcher.data.QaVariant.isolatedNow) null else"))
        assertTrue(src("device/VendorAdapter.kt").contains("if (com.shelf.launcher.data.QaVariant.isolatedNow) null else runCatching {"))
        assertTrue(src("data/QaVariant.kt").contains("android.app.Application.getProcessName()"))
    }
}
