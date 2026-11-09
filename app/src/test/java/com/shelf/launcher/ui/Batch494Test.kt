package com.shelf.launcher.ui

import com.shelf.launcher.data.GameSyncStatus
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.SyncDirection
import com.shelf.launcher.data.SyncState
import com.shelf.launcher.data.syncLine
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 494 (staging only): D137 draw-time reads, D292 sync wording, charging battery, pinned comments, windows follow layout. */
class Batch494Test {
    private fun src(rel: String): String = listOf(
        File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")
    ).first { it.exists() }.readText()

    @Test fun carouselBoxReadsGlidingColoursOnlyWhenDrawing() {
        val c = src("ui/Carousel.kt")
        val box = c.substring(c.indexOf("val sizeK = Features.carouselSize.state.value / 100f"), c.indexOf("if (QaTrace.on) { QaTrace.count(\"CarBwc\")"))
        // No theme colour or animated tint read while composing the carousel's box.
        assertTrue(!box.contains("ShelfTheme.accent") && !box.contains("rememberUpdatedState(ShelfTheme"))
        assertTrue(!box.contains("val accent by androidx.compose.animation.animateColorAsState(stageTarget"))
        assertTrue(box.contains("val accent = if (palAcc == null && !stageA.isRunning) stageLive.value.accent else stageA.value"))
        assertTrue(box.contains("{ accentLive.value.accent }"))
    }

    private val t0 = 1_000_000L
    @Test fun syncWordingFollowsRecordedEvents() {
        // A check that moved nothing after an earlier download: not "Fetched from Drive".
        val checked = GameSyncStatus(SyncState.SYNCED, lastSuccessAt = t0 + 50, lastDirection = SyncDirection.DOWNLOAD, fileCount = 1, lastTransferAt = t0)
        assertEquals("Checked · no changes", syncLine(checked)!!.label); assertEquals(t0 + 50, syncLine(checked)!!.at)
        // The last sync itself moved files.
        assertEquals("Downloaded from Drive · 1 file", syncLine(checked.copy(lastTransferAt = t0 + 50))!!.label)
        assertEquals("Uploaded to Drive · 2 files", syncLine(checked.copy(lastTransferAt = t0 + 50, lastDirection = SyncDirection.UPLOAD, fileCount = 2))!!.label)
        // Older entries (no transfer time recorded): nothing guessed.
        assertEquals("Last checked", syncLine(checked.copy(lastTransferAt = 0))!!.label)
        assertEquals("Checked · no changes", syncLine(GameSyncStatus(SyncState.SYNCED, lastSuccessAt = t0))!!.label)
        // Offline / failed / never.
        assertEquals("Offline · sync skipped", syncLine(GameSyncStatus(SyncState.OFFLINE, updatedAt = t0))!!.label)
        assertEquals("Sync failed", syncLine(GameSyncStatus(SyncState.ERROR, updatedAt = t0))!!.label)
        assertNull(syncLine(GameSyncStatus(SyncState.PENDING)))
        val ui = src("ui/SaveExplorer.kt")
        assertTrue(!ui.contains("\"Fetched from Drive\""))
        assertTrue(src("data/CloudSync.kt").contains("lastTransferAt = if (moved > 0) now else old.lastTransferAt"))
    }

    @Test fun chargingBatteryKeepsItsSizeAndShowsTheLevel() {
        val h = src("ui/HomeChrome.kt")
        assertTrue(h.contains("else PlainBattery(battery.percent, battery.charging, ink, look.iconSize)"))
        // Animated only while charging, active, with animations on and not calm; drawn, not recomposed.
        assertTrue(h.contains("val moving = charging && rememberIsActive() && rememberAnimationsEnabled() && !calmHere()"))
        assertTrue(h.contains("modifier = Modifier.size(iconSize)\n                .graphicsLayer"))
        assertTrue(h.contains("val p = phase.floatValue"))
        assertEquals(batteryIconFor(20, false), chargingLevelIcon(5))
        assertEquals(batteryIconFor(55, false), chargingLevelIcon(55))
        assertEquals(batteryIconFor(100, false), chargingLevelIcon(100))
        // The Volt gauge's bolt pulse is read while drawing.
        assertTrue(src("ui/HomeExtras.kt").contains("outline.copy(alpha = 0.6f + 0.4f * pulseS.value)"))
    }

    @Test fun pinnedCommentsScrollAndUseTheListsLines() {
        val o = src("ui/RaOverlayView.kt")
        assertEquals(16f / 12f, COMMENT_LINE_MULT, 0.0001f)
        assertTrue(o.contains("setLineSpacing(0f, COMMENT_LINE_MULT)"))
        assertTrue(o.contains("private val pinnedFrame = MaxHeightScroll(context")) // (497: the whole pinned section scrolls)
        assertTrue(o.contains("list.sortedByDescending { it.Submitted ?: \"\" }.take(12)"))
    }

    @Test fun windowsFollowTheLayoutOnlyWhenAskedAndWhereThereIsALook() {
        assertEquals(WindowLook.Style.OWN, WindowLook.styleFor(HomeLayout.CHAN, false))
        assertEquals(WindowLook.Style.CHANNEL, WindowLook.styleFor(HomeLayout.CHAN, true))
        assertEquals(WindowLook.Style.CROSS, WindowLook.styleFor(HomeLayout.CROSS, true))
        assertEquals(WindowLook.Style.SHELF, WindowLook.styleFor(HomeLayout.STACKS, true)) // (497: frame family)
        assertEquals(WindowLook.Style.OWN, WindowLook.styleFor(HomeLayout.CLASSIC, true))
        assertEquals(24.dp, WindowLook.corner(WindowLook.Style.OWN, 24.dp, false, false))
        assertEquals(0.dp, WindowLook.corner(WindowLook.Style.CHANNEL, 0.dp, false, true))
        assertEquals(false, com.shelf.launcher.data.Features.windowsFollowLayout.default)
        // OWN draws exactly the old frame; only Settings and Achievements ask.
        assertTrue(src("ui/WindowLook.kt").contains("WindowLook.Style.OWN -> this.voltSurface(shape, base = surface).border(1.dp, Color.White.copy(alpha = 0.08f), shape)"))
        assertTrue(src("ui/AchievementListDialog.kt").contains("layoutAware = true"))
        assertTrue(src("ui/DialogChrome.kt").contains("val winStyle = if (layoutAware) WindowLook.current() else WindowLook.Style.OWN"))
    }
}
