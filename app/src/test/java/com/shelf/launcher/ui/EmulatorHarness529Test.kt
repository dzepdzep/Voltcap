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

    /** 675k (run 43811840328): the hook also answers the Inspect / Wobble question, without changing the mode. */
    @Test fun firstRunHookAnswersTheInspectQuestion() {
        val s = file("app/src/main/java/com/shelf/launcher/ui/TestHooks.kt")
        val i = s.indexOf("getBooleanExtra(\"voltcap_test_firstrun_done\""); val j = s.indexOf("firstrun done (QA hook)", i)
        assertTrue(i > 0 && j > i)
        val block = s.substring(i, j)
        assertTrue("marks the question answered", block.contains("Features.inspectModeAsked.value = true"))
        assertTrue("never picks a mode for the owner", !block.contains("inspectMode.value ="))
        // The dialog's own gate is still "asked" - the hook's flag is what keeps it off the emulator's home.
        val c = file("app/src/main/java/com/shelf/launcher/ui/InspectChoice.kt")
        assertTrue(c.contains("!Features.inspectModeAsked.value"))
    }

    /** 675k (run 43811840328): another app's ANR dialog over the picker is closed, not counted as the QA app's state. */
    @Test fun trialClosesForeignAnrDialogs() {
        val s = file("tools/qa/emulator/run_trial.sh")
        assertTrue(s.contains("settings put global hide_error_dialogs 1"))
        val pre = s.indexOf("precheck() {"); val anr = s.indexOf("CLOSE_SYSTEM_DIALOGS", pre)
        assertTrue("closed inside precheck's loop", anr > pre && anr < s.indexOf("PRECONDITION \$1 FAILED", pre))
        assertTrue("bounded: counts as a try", s.substring(anr, s.indexOf("continue", anr)).contains("tries=\$((tries + 1))"))
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
