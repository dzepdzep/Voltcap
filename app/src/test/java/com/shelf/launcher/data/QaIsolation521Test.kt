package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Batch 521: the QA build can never become an APK that installs over the owner's Voltcap or shares its data. These read the
 * shipped build file and QA manifest (the install side is tested by tools/adb_mcp/test/guard.test.js), so weakening a
 * protection fails the unit tests.
 */
class QaIsolation521Test {
    private fun file(rel: String): File = listOf(File(rel), File("app/$rel")).first { it.exists() }
    private val gradle by lazy { file("build.gradle.kts").readText() }
    private val qaManifest by lazy { file("src/qa/AndroidManifest.xml").readText() }

    @Test fun debugIsTheQaAppByDefault() {
        assertTrue(gradle.contains("val debugIsQa = !debugIsProduction"))
        // Only the exact owner opt-out phrase turns it off; the old debugIsQa=false switch is ignored.
        assertTrue(gradle.contains("getProperty(\"debugIsProduction\")?.trim() == \"yes-install-over-my-real-voltcap\""))
        assertFalse(gradle.contains("getProperty(\"debugIsQa\")?.trim() == \"true\""))
    }

    @Test fun qaBuildsAreSignedWithTheDebugKeyNotTheReleaseKey() {
        val qaBlock = gradle.substringAfter("create(\"qa\") {").substringBefore("\n        }")
        assertTrue(qaBlock.contains("signingConfig = signingConfigs.getByName(\"debug\")"))
        assertTrue(qaBlock.contains("applicationIdSuffix = \".qa\""))
        val debugQa = gradle.substringAfter("if (debugIsQa) getByName(\"debug\") {").substringBefore("\n        }")
        assertTrue(debugQa.contains("signingConfig = signingConfigs.getByName(\"debug\")"))
        assertTrue(debugQa.contains("applicationIdSuffix = \".qa\""))
        val debug = gradle.substringAfter("getByName(\"debug\") {").substringBefore("\n        }")
        assertTrue("release key only with the owner opt-out", debug.contains("if (debugIsProduction) signingConfigs.findByName(\"release\")"))
    }

    @Test fun builtApksAreVerifiedAndDeletedWhenWrong() {
        assertTrue(gradle.contains("val verifyQaIsolation by tasks.registering"))
        assertTrue(gradle.contains("it.name == \"assembleQa\" || (debugIsQa && it.name == \"assembleDebug\")"))
        assertTrue(gradle.contains("finalizedBy(verifyQaIsolation)"))
        assertTrue(gradle.contains("apk.delete()"))
        assertTrue("strays removed", gradle.contains("apk.name !in listed"))
        assertTrue(gradle.contains("88ce4b67ae3825a04585114aec25cfaf5023e0b55cfdb61f375d30945f344197"))
    }

    @Test fun qaManifestDropsEverythingThatReachesOutside() {
        for (perm in listOf("MANAGE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "WRITE_SECURE_SETTINGS", "WRITE_SETTINGS",
            "REQUEST_INSTALL_PACKAGES", "REQUEST_DELETE_PACKAGES", "RECEIVE_BOOT_COMPLETED", "KILL_BACKGROUND_PROCESSES"))
            assertTrue(perm, qaManifest.contains("android.permission.$perm\" tools:node=\"remove\""))
        for (c in listOf(".HomeAlias", ".data.UpdatedReceiver", ".device.ChargeLimitBootReceiver", ".data.ShelfNotificationListener"))
            assertTrue(c, qaManifest.contains("android:name=\"$c\" tools:node=\"remove\""))
        assertTrue(qaManifest.contains("android:allowBackup=\"false\""))
    }

    @Test fun mainManifestSharesNothingByIdentity() {
        val main = file("src/main/AndroidManifest.xml").readText()
        assertFalse("no sharedUserId", main.contains("sharedUserId"))
        assertFalse("no signature permissions to share", Regex("<permission\\s").containsMatchIn(main))
        assertTrue("provider authority follows the package", main.contains("android:authorities=\"\${applicationId}.fileprovider\""))
    }

    @Test fun packageRule() {
        assertTrue(QaVariant.isolatedPackage("com.shelf.launcher.qa"))
        assertFalse(QaVariant.isolatedPackage("com.shelf.launcher"))
        assertFalse(QaVariant.isolatedPackage(null))
        assertEquals(".qa", QaVariant.SUFFIX)
    }
}
