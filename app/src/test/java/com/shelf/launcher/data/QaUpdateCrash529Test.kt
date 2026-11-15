package com.shelf.launcher.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 529i: the QA app (no REQUEST_INSTALL_PACKAGES) must never crash on the update question. */
class QaUpdateCrash529Test {
    private fun src(rel: String) = listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel"))
        .first { it.exists() }.readText().replace("\r\n", "\n")

    @Test fun canInstallNeverThrowsWithoutThePermission() {
        val s = src("data/AppUpdates.kt")
        val body = s.substringAfter("fun canInstall(c: Context): Boolean =").substringBefore("\n\n")
        assertTrue(body, body.contains("runCatching { c.packageManager.canRequestPackageInstalls() }.getOrDefault(false)"))
    }

    @Test fun qaManifestStillRemovesTheInstallPermission() {
        val f = listOf(File("src/qa/AndroidManifest.xml"), File("app/src/qa/AndroidManifest.xml")).first { it.exists() }
        assertTrue(f.readText().contains("REQUEST_INSTALL_PACKAGES\" tools:node=\"remove\""))
    }
}
