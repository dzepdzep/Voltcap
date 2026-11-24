package com.shelf.launcher.ui

import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P2 728a (class C): the Timeline window draws the entry's own pages along its line - not the shared page bodies. */
class NativePagesTime728aTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n")
        .map { File(it) }.first { it.exists() }.readText()

    @Test fun timelineHasItsOwnPages() {
        assertEquals(WinScene.TIMELINE_ENTRY, winSceneFor(HomeLayout.TIMELINE))
        assertTrue(nativePages(WinScene.TIMELINE_ENTRY))
        for (p in WinPage.values()) assertFalse("$p", sharedPageFor(WinScene.TIMELINE_ENTRY, p))
        val f = src("WinSceneFamilies.kt")
        val scene = f.substring(f.indexOf("internal fun TimelineHang("), f.indexOf("\n}\n", f.indexOf("internal fun TimelineHang(")))
        assertTrue(scene.contains("TimePage(ctx, pg, pageLive, a, Modifier.fillMaxSize())"))
        assertTrue(scene.contains(".nativeBumpers(ctx)") && scene.contains("slots = sceneKeySlots(ctx)"))
        // the Info page keeps the entry's facts; the shared page body is gone from the scene
        assertFalse(scene.contains("ctx.pageBody("))
        // only Performance falls back to the shared page
        assertEquals(1, src("WinPagesTime.kt").split("ctx.pageFor(").size - 1)
    }

    @Test fun pageNames() {
        assertEquals(listOf("ENTRY", "CHRONICLE", "MILESTONES", "BRANCHES"), NATIVE_TAB_ORDER.map(::timePageName))
    }

    @Test fun milestoneWordsAndPoints() {
        assertEquals("Reached 12 Mar 2024", timeMilestoneWhen(true, "12 Mar 2024"))
        assertEquals("Reached", timeMilestoneWhen(true, null)); assertEquals("Reached", timeMilestoneWhen(true, " "))
        assertEquals("Ahead", timeMilestoneWhen(false, "12 Mar 2024"))
        assertEquals("3 of 12 milestones reached", timeMilestoneCount(3, 12))
        assertEquals("0 of 1 milestone reached", timeMilestoneCount(0, 1))
        assertEquals("25 pts", timePoints(25)); assertEquals("1 pt", timePoints(1)); assertNull(timePoints(null))
    }

    @Test fun noTruncationAndNoIntrinsicsOnThePages() {
        val c = src("WinPagesTime.kt")
        assertFalse(c.contains("Ellipsis")); assertFalse(c.contains("…\""))
        // (the line is drawn behind its stops - no intrinsic measuring of the rows' texts)
        assertFalse(c.contains("IntrinsicSize"))
    }
}
