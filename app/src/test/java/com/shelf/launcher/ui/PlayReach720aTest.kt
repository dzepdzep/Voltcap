package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 720a (owner 23 Nov 22:43, Task 1 item 3): the QA path for the real Edit Home D-pad move of the Voltcap row's added Play part
 * (715a). The `reach` line must carry what tools/qa/tools/play720.py grades, the Edit Home mark must say when D-pad keys may be
 * pressed, and the line must come from the home's own prepareReach (the same decision a real ▲ / ▼ uses), not a copy.
 */
class PlayReach720aTest {
    private fun src(rel: String): String =
        listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    @Test fun lineCarriesWhatTheGraderReads() {
        val l = PlayReach720.line(
            "CLASSIC", "MIDDLE", headerOn = false, added = true, savedY = 0.54f, liveY = null, drawnY = 0.5412f,
            up = listOf(HomeReach.Zone.MUSIC), down = listOf(HomeReach.Zone.HEADER, HomeReach.Zone.SHORTCUTS), buttons = 1,
            zone = HomeReach.Zone.HEADER, edit = false
        )
        assertEquals(
            "CLASSIC dock=MIDDLE header=off added=yes saved=0.540 live=none drawn=0.541 above=false up=MUSIC " +
                "down=HEADER,SHORTCUTS buttons=1 zone=HEADER edit=off", l
        )
        val m = PlayReach720.line("CLASSIC", "TOP", true, false, null, 0.44f, null, emptyList(), emptyList(), 0, null, true)
        assertEquals("CLASSIC dock=TOP header=on added=no saved=none live=0.440 drawn=none above=false up=- down=- buttons=0 zone=none edit=on", m)
        // the grader's pattern matches the line (one regex for both sides)
        val re = Regex("""reach (\S+) dock=(\S+) header=(\S+) added=(\S+) saved=(\S+) live=(\S+) drawn=(\S+) above=(\S+) up=(\S+) down=(\S+) buttons=(\d+) zone=(\S+) edit=(\S+)""")
        assertTrue(re.containsMatchIn("123 reach $l") && re.containsMatchIn("123 reach $m"))
    }

    @Test fun editMarkNamesThePartOnlyWhileEditing() {
        assertEquals("EDIT CLASSIC:h_play", PlayReach720.editMark(true, PlayReach720.playId))
        assertEquals("HOME", PlayReach720.editMark(false, PlayReach720.playId))
    }

    @Test fun homeWritesTheLineFromItsOwnReachDecision() {
        val screen = src("ui/LauncherScreen.kt")
        val at = screen.indexOf("val reachAsk = TestHooks.reach.intValue")
        assertTrue("the reach hook is read on the home", at > 0)
        val block = screen.substring(at, at + 1600)
        assertTrue("it runs the home's prepareReach first", block.indexOf("prepareReach()") in 0 until block.indexOf("PlayReach720.line("))
        assertTrue("only with QA logging", block.contains("QaTrace.on"))
        assertTrue("the Edit Home mark is written", screen.contains("TestHooks.markEdit(markCtx, PlayReach720.editMark(editOn, editSel))"))
        val hooks = src("ui/TestHooks.kt")
        assertTrue(hooks.contains("getBooleanExtra(\"voltcap_test_reach\", false) == true && enabled(context)"))
        val mark = hooks.substring(hooks.indexOf("fun markEdit("), hooks.indexOf("fun markEdit(") + 200)
        assertTrue("the mark is QA-only", mark.contains("if (!enabled(context)) return"))
    }
}
