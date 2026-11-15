package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/** 529p (#16 6868819840): the emulator trial starts from a known first-run state and stops on a failed precondition. */
class EmulatorHarness529Test {
    private fun file(p: String) = listOf(java.io.File(p), java.io.File("../$p")).first { it.exists() }.readText()

    @Test fun firstRunHookIsQaOnly() {
        val s = file("app/src/main/java/com/shelf/launcher/ui/TestHooks.kt")
        val i = s.indexOf("getBooleanExtra(\"voltcap_test_firstrun_done\""); assertTrue(i > 0)
        val line = s.substring(i, s.indexOf('\n', i))
        assertTrue("gated by the QA hooks", line.contains("enabled(context)"))
    }

    @Test fun trialChecksPreconditionsBeforeEverySequence() {
        val s = file("tools/qa/emulator/run_trial.sh")
        assertTrue(s.contains("voltcap_test_firstrun_done true") && s.contains("am force-stop \$P"))
        for (seq in listOf("e529a", "e529b", "e529c")) {
            val pre = s.indexOf("precheck $seq || finish"); assertTrue("precheck before $seq", pre > 0)
            assertTrue("before it runs", pre < s.indexOf(if (seq == "e529c") "shelfpages529.sh run e529c" else "run_pop $seq"))
        }
        assertTrue(s.contains("PRECONDITION \$1 FAILED") && s.contains("NOT TESTED"))
        val d = file("tools/qa/device/shelfpages529.sh")
        assertTrue(d.contains("the game window did not open") && d.contains("PRECONDITION FAILED"))
    }
}
