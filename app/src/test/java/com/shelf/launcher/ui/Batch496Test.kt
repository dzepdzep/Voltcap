package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 496 (staging only): layout-aware windows phase 3, D293 layout-look gate, overlay fixture, charging-light probes. */
class Batch496Test {
    private fun src(rel: String): String = listOf(
        File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")
    ).first { it.exists() }.readText()

    @Test fun layoutLookOnlyForARealSwitch() {
        val g = LayoutLookGate
        g.resetForTest()
        try {
            // The home screen appearing (or being rebuilt) on the layout it has: nothing applied.
            assertFalse(g.due(HomeLayout.CHAN, true))
            assertFalse(g.due(HomeLayout.CHAN, true))
            // A real switch: applied.
            assertTrue(g.due(HomeLayout.CROSS, true))
            // A QA preview of another layout, then yours coming back: not a switch.
            assertTrue(g.due(HomeLayout.CHAN, true))
            g.restored(HomeLayout.CROSS)
            assertFalse(g.due(HomeLayout.CROSS, true))
            // Layout standard off: never; switched on again: applied once.
            assertFalse(g.due(HomeLayout.CHAN, false))
            assertTrue(g.due(HomeLayout.CHAN, true))
            assertFalse(g.due(HomeLayout.CHAN, true))
        } finally { g.resetForTest() }
        val l = src("ui/LauncherScreen.kt")
        assertTrue(l.contains("if (!LayoutLookGate.due(viewModel.uiState.value.appearance.homeLayout, lookFromLayoutOn)) return@LaunchedEffect"))
        assertTrue(l.contains("LayoutLookGate.restored(viewModel.uiState.value.appearance.homeLayout)"))
    }

    @Test fun channelMaterialTonesStayReadable() {
        // (Update 497: the tones come from the layout's own palette now — channelWindowTokens, checked in Batch497Test.)
        val t = channelWindowTokens(Color(0xFF2F7BC0), chanColorsFor(com.shelf.launcher.data.Features.ChanPalette.PALE, 0), true, true,
            Triple(Color.Black, Color.Black, Color.Black), Color.White, Color.White)
        assertTrue(contrastRatio(t.outline, t.colors.surface) >= 3f)
        assertTrue(contrastRatio(t.outline, t.track) >= 2f)
        assertTrue(contrastRatio(t.deep, t.soft) >= 4.5f)
        val w = src("ui/WindowLook.kt")
        assertTrue(w.contains("surfaceContainerHighest = t.track, outline = t.outline"))
        // Off / other layouts: the faint tint is exactly the white one it replaced.
        assertTrue(w.contains("else Color.White.copy(alpha = alpha)"))
        val v = src("ui/VoltUi.kt")
        assertTrue(v.contains("val offTrack = winTint(0.10f * (1f - t))"))
        assertTrue(v.contains("val hairline = if (LocalWindowStyle.current == WindowLook.Style.CHANNEL) winTint(0.09f) else VoltUi.hairline"))
    }

    @Test fun saveVaultFrameOnlyAndDryViewWritesNothing() {
        val s = src("ui/SaveExplorer.kt")
        assertTrue(s.contains(".windowRim(winStyle, vaultShape"))
        // The vault's own dark scene is unchanged: no WindowColors inside it.
        assertFalse(s.contains("WindowColors("))
        // Dry view: no search, no sync, no connect, no list rewritten.
        assertTrue(s.contains("if (dry) com.shelf.launcher.data.SaveCapsules.showCached(context, games)"))
        assertTrue(s.contains("if (!dry && connected && fullSync.serial > openedAtSerial)"))
        assertTrue(s.contains("if (!dry && cloud.settings.connected && !cloud.running)"))
        assertTrue(s.contains("if (!dry && !connecting)"))
        val c = src("data/SaveCapsules.kt")
        val dry = c.substring(c.indexOf("suspend fun showCached"), c.indexOf("suspend fun refreshLocal"))
        assertFalse(dry.contains("writeCache")); assertFalse(dry.contains("build("))
        assertTrue(src("ui/LauncherScreen.kt").contains("if (away.isNotEmpty() && !TestHooks.vaultDry) viewModel.loadVaultArt(away)"))
    }

    @Test fun overlayFixtureNeverPinsOrFetches() {
        assertNull(QaOverlayFixture.parse("off"))
        assertEquals(QaOverlayFixture.Spec(true, 1.3f, 360), QaOverlayFixture.parse("open=1,scale=1.3,width=360"))
        assertEquals(2f, QaOverlayFixture.parse("scale=9")!!.scale)
        val o = src("ui/RaOverlayView.kt")
        assertTrue(o.contains("if (fixture != null) return@setOnClickListener"))
        assertTrue(o.contains("fixture?.let { f -> into.removeAllViews(); f.comments.forEach"))
        assertTrue(o.contains("fixture?.let { f -> return f.pinnedId?.let"))
        // The open panel fits the screen: the list gives up room (found with the fixture: tabs cut, list off-screen).
        assertTrue(o.contains("overlayPanelFit(maxH, fixed, pinH, want, px(LIST_MIN_DP), px(PINNED_MIN_DP), px(LIST_FLOOR_DP))")) // (497: Batch497Test)
        assertEquals(0.3f, PINNED_COMMENTS_MAX); assertEquals(0.94f, PANEL_MAX_H)
        // The fixture's own text is long enough to need wrapping in the 320 dp panel.
        assertTrue(QaOverlayFixture.items.first().title.length > 80)
    }

    @Test fun qaSessionRunsNoDriveSync() {
        // A QA install / restart (hooks file present) must not start the launch-time or periodic Drive sync.
        assertTrue(src("LauncherViewModel.kt").contains("if (com.shelf.launcher.ui.TestHooks.enabled(application)) { android.util.Log.e(\"VoltcapQA\", \"launch sync skipped (QA session)\"); return@launch }"))
        assertTrue(src("data/CloudSyncWorker.kt").contains("if (com.shelf.launcher.ui.TestHooks.enabled(app)) return Result.success()"))
        // Every sync path (full, scoped / after a game, the watcher) stops at one gate during a QA session.
        val c = src("data/CloudSync.kt")
        assertTrue(c.contains("if (qaPaused(context)) return@withLock SyncOutcome(true, QA_PAUSED)"))
        assertTrue(c.contains("if (qaPaused(context)) return SyncOutcome(true, QA_PAUSED)"))
        assertTrue(c.contains("watchJob?.isActive == true || qaPaused(context)) return"))
    }

    @Test fun chargingLightProbesAreQaOnly() {
        val h = src("ui/HomeChrome.kt")
        assertTrue(h.contains("if (QaTrace.on) androidx.compose.runtime.SideEffect { QaTrace.count(\"Batt\") }"))
        assertTrue(h.contains("if (QaTrace.on) QaTrace.count(\"BattDraw\")"))
        // The phase is read only while drawing (never while composing): the light never re-composes the bar.
        val body = h.substring(h.indexOf("internal fun PlainBattery"), h.indexOf("internal fun chargingLevelIcon"))
        assertEquals(1, Regex("phase\\.floatValue(?! =)").findAll(body).count())
    }
}
