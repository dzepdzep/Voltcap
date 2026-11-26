package com.shelf.launcher.ui

import com.shelf.launcher.data.QaVariant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 682aw (owner 25 Nov item 6 - "verify the QA-only hooks are absent or inaccessible in production builds"): the hooks added for the
 * 682as-682av QA (Arcade text A/B, charging light A/B, FitText search A/B, R1 Focus position, START > Categories) are read only
 * where QaVariant.isolated - the QA app's own package (".qa") - and the production package is never that.
 */
class HooksIsolated682awTest {
    private val src = File("src/main/java/com/shelf/launcher/ui/TestHooks.kt").let { if (it.exists()) it else File("app/" + it.path) }.readText()

    @Test fun productionPackageIsNeverTheIsolatedQaApp() {
        assertFalse(QaVariant.isolatedPackage("com.shelf.launcher"))
        assertFalse(QaVariant.isolatedPackage(null))
        assertTrue(QaVariant.isolatedPackage("com.shelf.launcher.qa"))
    }

    @Test fun everyNewHookIsReadOnlyInTheIsolatedQaApp() {
        for (h in listOf("voltcap_test_arattract", "voltcap_test_chargelight", "voltcap_test_fitsearch", "voltcap_test_focuspos", "voltcap_test_appcat")) {
            val lines = src.lines().filter { it.contains("getStringExtra(\"$h\")") }
            assertTrue("$h is not read at all", lines.isNotEmpty())
            for (l in lines) {
                // (the read itself, or the block it opens, is guarded by QaVariant.isolated)
                val i = src.indexOf(l)
                val block = src.substring(i, minOf(src.length, i + l.length + 220))
                assertTrue("$h read without QaVariant.isolated: $l", block.contains("QaVariant.isolated(context)"))
            }
        }
    }
}
