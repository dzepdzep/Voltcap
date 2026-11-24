package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.ui.RowColumnFit.keepUnderBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/**
 * 716a (owner Task 1, Row-position collisions - profile / status): with Row Middle (or Bottom) the Voltcap row's column was
 * centred on (stood on) the whole screen, so a column taller than the room the status bar leaves (game header + info +
 * chips at x1.3) started under the clock / battery / music player. It now never starts above the bar; when it fits nothing
 * moves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h400dp-land-mdpi")
class RowColumnUnderBar716aTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun src(rel: String): String =
        listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    @Test fun fittingColumnStaysExactlyWhereItWas() {
        // 1000 high, column 400, bar 60, legend 24: centred 300 / bottom 600 / top 0 - unchanged.
        assertEquals(300, RowColumnFit.top(DockPosition.MIDDLE, 1000, 400, 60, 24))
        assertEquals(600, RowColumnFit.top(DockPosition.BOTTOM, 1000, 400, 60, 24))
        assertEquals(0, RowColumnFit.top(DockPosition.TOP, 1000, 400, 60, 24))
        // No bar, no legend: plain centring.
        assertEquals(100, RowColumnFit.top(DockPosition.MIDDLE, 1000, 800, 0, 0))
    }

    @Test fun tallColumnNeverStartsUnderTheBar() {
        // Middle, column 900 in 1000 with a 60 px bar: centred it started at 50 (under the bar) - now right below it.
        assertEquals(60, RowColumnFit.top(DockPosition.MIDDLE, 1000, 900, 60, 24))
        // Middle, too tall for bar + legend: starts at the bar, spills at the bottom (the top stays readable).
        assertEquals(60, RowColumnFit.top(DockPosition.MIDDLE, 1000, 960, 60, 24))
        // Middle, column fills the whole box (Column is capped at the box): was 0.
        assertEquals(60, RowColumnFit.top(DockPosition.MIDDLE, 1000, 1000, 60, 24))
        // Bottom, column 980: stood on the bottom edge it started at 20, under the bar - now 60.
        assertEquals(60, RowColumnFit.top(DockPosition.BOTTOM, 1000, 980, 60, 24))
        // Middle with the centred column clear of the bar (10) but over the legend band: lifted clear of it (20 -> 16).
        assertEquals(16, RowColumnFit.top(DockPosition.MIDDLE, 1000, 960, 10, 24))
        // Clear of both: unchanged.
        assertEquals(50, RowColumnFit.top(DockPosition.MIDDLE, 1000, 900, 40, 24))
    }

    @Test fun modifierPlacesTheAlignedColumnBelowTheBar() {
        var top = -1f
        var fitTop = -1f
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                // 400 dp screen, 380 dp column centred: starts at 10 dp - under a 40 dp bar (the old placement).
                Column(Modifier.align(Alignment.Center).onGloballyPositioned { top = it.positionInRoot().y }) {
                    Spacer(Modifier.width(10.dp).height(380.dp))
                }
                Column(Modifier.align(Alignment.Center).keepUnderBar(DockPosition.MIDDLE, topSafe = 40, bottomSafe = 0)
                    .onGloballyPositioned { fitTop = it.positionInRoot().y }) {
                    Spacer(Modifier.width(10.dp).height(380.dp))
                }
            }
        }
        repeat(3) { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }
        compose.waitForIdle()
        assertEquals(10f, top, 0.5f)       // the old centring: under the bar
        assertEquals(40f, fitTop, 0.5f)    // 716a: right below it
    }

    @Test fun voltcapRowColumnUsesIt() {
        val s = src("ui/LauncherScreen.kt")
        val at = s.indexOf("Modifier.keepUnderBar(")
        assertTrue("the Voltcap row column keeps clear of the status bar", at > 0)
        // Applied between the Row-place alignment and the row's measured top (rowTopPx sees the real spot).
        val align = s.lastIndexOf("com.shelf.launcher.data.DockPosition.BOTTOM -> Alignment.BottomCenter", at)
        val measured = s.indexOf("rowTopPx = it.positionInRoot().y", at)
        assertTrue(align in (at - 800) until at && measured in at until at + 900)
    }
}
