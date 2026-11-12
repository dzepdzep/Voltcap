package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.6.67 "Who's playing?" looks: picture sizes for 1–9 choices on the GT78 (640×427 dp) and the Odin 2 Portal (834×469 dp),
 * D-pad steps, sample profiles that never touch the real ones, the looks list, and that switching / storage stay as they were.
 */
class ProfilePicker445Test {
    private fun src(name: String): String {
        val roots = listOf("src_copy/", "../src_copy/", "app/src/main/java/com/shelf/launcher/", "src/main/java/com/shelf/launcher/")
        return roots.map { File(it + name) }.first { it.exists() }.readText().replace("\r\n", "\n")
    }

    private val screens = listOf(Triple(640f, 427f, false), Triple(834f, 469f, false), Triple(480f, 320f, true), Triple(1280f, 720f, false))

    @Test fun picturesNeverTinyAndNamesNeverOverlap() {
        for ((w, h, compact) in screens) for (n in 1..9) {
            val p = PickerGrid.plan(n, w, h, compact)
            val min = if (compact) 64f else 76f
            assertTrue("$w×$h n=$n avatar ${p.avatar}", p.avatar >= min - 0.01f || p.avatar >= h * 0.34f - 0.01f)
            assertTrue("name slot wider than the picture", p.slot > p.avatar)
            // Either the whole row fits between the side margins, or it scrolls.
            assertTrue("$w×$h n=$n fits or scrolls", p.scroll || p.rowWidth <= w - 2 * PickerGrid.SIDE + 0.5f)
            assertTrue("picture fits the height", p.avatar <= h * 0.34f + 0.01f)
        }
    }

    @Test fun fewProfilesGetBigPictures() {
        val p = PickerGrid.plan(2, 834f, 469f, false)
        assertEquals(128f, p.avatar, 0.01f)
        assertTrue(!p.scroll)
        assertTrue("8 profiles + Add on the GT78 scroll instead of shrinking", PickerGrid.plan(9, 640f, 427f, false).scroll)
    }

    @Test fun dpadSteps() {
        assertEquals(0, PickerGrid.step(0, 5, 5, -1, 0))
        assertEquals(4, PickerGrid.step(4, 5, 5, 1, 0))
        assertEquals(3, PickerGrid.step(2, 5, 5, 1, 0))
        assertEquals(2, PickerGrid.step(2, 5, 5, 0, 1)) // one row: ▼ stays
        assertEquals(4, PickerGrid.step(1, 5, 3, 0, 1)) // two rows: same column, clamped to the shorter row
    }

    @Test fun sampleProfilesAreSeparateFromYours() {
        val (list, active) = TestHooks.demoProfiles("8:long:locked:active=2")
        assertEquals(8, list.size)
        assertTrue(list.all { it.id >= 900 })
        assertEquals(902, active)
        assertTrue(list[1].locked && !list[0].locked)
        assertEquals(3, TestHooks.demoProfiles("x").first.size)
        assertEquals(1, TestHooks.demoProfiles("0").first.size)
    }

    @Test fun sixLooksIncludingTodaysMidnightBlue() {
        // (Connected Ignition, owner-approved: Ignition is the 7th look, added at the end so every saved look keeps its name.)
        val names = Features.PickerLook.values().map { it.name }
        assertEquals(listOf("AURORA", "CONSOLE", "NEON", "OLED", "THEME", "MIDNIGHT", "IGNITION"), names)
        assertEquals("picker_look", Features.pickerLook.key)
    }

    @Test fun existingInstallsKeepMidnightBlue() {
        // Update 447 (owner): existing installs keep today's look, new ones start on Aurora glass; a saved one is never overwritten.
        assertEquals(Features.PickerLook.MIDNIGHT, Features.firstPickerLook(existingInstall = true))
        assertEquals(Features.PickerLook.AURORA, Features.firstPickerLook(existingInstall = false))
        assertEquals("unsaved default before init", Features.PickerLook.MIDNIGHT, Features.pickerLook.default)
        val f = src("data/Features.kt")
        assertTrue(f.contains("if (!p.contains(\"picker_look\")) pickerLook.value = firstPickerLook(FirstRun.isDone("))
    }

    @Test fun pickerTellsQaWhenItIsUp() {
        // D144: QA reads the app's own mark, not the picker's colours.
        val s = src("ui/ProfileSelect.kt")
        assertTrue(s.contains("TestHooks.markScreen(markCtx, \"PICKER\")") && s.contains("TestHooks.markScreen(markCtx, \"HOME\")"))
        assertTrue(src("ui/TestHooks.kt").contains("if (!enabled(context)) return"))
    }

    @Test fun colourPresetsKeepASavedColour() {
        val s = src("ui/ProfileSelect.kt")
        assertTrue("a saved hue that is no preset is offered as it is", s.contains("else listOf(hue) + PickerGrid.PRESETS"))
        assertTrue(PickerGrid.PRESETS.size == 12 && PickerGrid.PRESETS.all { it in 0f..360f })
    }

    @Test fun switchingAndStorageUnchanged() {
        // Profile storage keeps its fields (no new ones): id, name, pic, hue, ra, pin.
        val p = src("data/Profiles.kt")
        assertTrue(p.contains("put(\"id\", p.id).put(\"name\", p.name).put(\"pic\", p.picture.name).put(\"hue\", p.hue.toDouble())"))
        // The pick still only hands the id to the caller (LauncherScreen switches the profile and starts the rollout).
        val s = src("ui/ProfileSelect.kt")
        assertTrue(s.contains("onPick(id)") && !s.contains("Profiles.setActive("))
        // Previews never save: Add, picture, PIN and remove are skipped in demo.
        assertTrue(s.contains("if (demo) return") && s.contains("if (!demo) Profiles.update(context, np)") && s.contains("if (!demo) Profiles.remove(context, p.id)"))
        val l = src("ui/LauncherScreen.kt")
        assertTrue(l.contains("viewModel.switchProfile(id)"))
    }

    @Test fun longNamesBreakBetweenWordsOnly() {
        assertEquals(listOf("Maximilian-", "Alexander"), PickerGrid.words("Maximilian-Alexander"))
        assertEquals(listOf("The", "Very", "Long"), PickerGrid.words("The Very Long"))
        assertEquals(listOf("Toni"), PickerGrid.words("Toni"))
        assertEquals("Anastasia-\u200BKatharina", PickerGrid.breakable("Anastasia-Katharina"))
        assertEquals("End-", PickerGrid.breakable("End-"))
    }

    @Test fun namesNeverEndInEllipsis() {
        val s = src("ui/ProfileSelect.kt")
        val bubble = s.substring(s.indexOf("private fun ProfileBubble("), s.indexOf("private fun ProfileBubble(") + 6000)
        assertTrue(!bubble.contains("TextOverflow.Ellipsis"))
    }
}
