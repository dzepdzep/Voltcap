package com.shelf.launcher.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.ui.theme.AppTheme
import com.shelf.launcher.ui.theme.LocalShelfColors
import com.shelf.launcher.ui.theme.contrastRatio
import androidx.compose.ui.graphics.luminance
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 769a (P12 contrast): the status bar's type on its own box reads at 4.5:1 (points / progress accent 3:1) for every home
 * layout x every theme x pale / dark backdrop - over black and over white behind a translucent box. Before 769a the Live tiles'
 * bar wrote white on the theme's accent at 92 %: on a pale accent (yellow, ice blue) about 1.3:1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class BarContrast769aTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    @After fun restore() {
        Features.dashPanel.state.value = Features.PanelLook.LIGHT
        Features.arRoom.state.value = Features.ArRoom.CARPET
        Features.cubeTint.state.value = Features.CubeTint.INDIGO
        Features.circuitColors.state.value = Features.CircuitColors.THEME
        Features.liveColor.state.value = Features.LiveColor.THEME
    }

    private data class Seen(val layout: HomeLayout, val theme: AppTheme, val pale: Boolean, val look: StatusLook)

    private fun allLooks(): List<Seen> {
        val out = ArrayList<Seen>()
        compose.setContent {
            for (t in AppTheme.entries) CompositionLocalProvider(LocalShelfColors provides t.colors) {
                for (l in HomeLayout.entries) for (p in listOf(false, true)) out += Seen(l, t, p, statusLookFor(l, p))
            }
        }
        compose.waitForIdle()
        return out
    }

    private fun backdrop(s: Seen) = if (s.pale) Color.White else s.theme.colors.background

    private fun checkAll(seen: List<Seen>) {
        assertTrue("composition ran", seen.size == AppTheme.entries.size * HomeLayout.entries.size * 2)
        val bad = seen.filter { barIsBoxed(it.look) && !it.look.ownPalette }.mapNotNull { s ->
            val l = s.look
            val box = barBoxOver(l.bg, backdrop(s))
            val ink = contrastRatio(l.ink, box)
            // (When nothing reaches the aim: the better of white / black.)
            val bestBw = maxOf(contrastRatio(Color.White, box), contrastRatio(Color.Black, box))
            val inkOk = ink >= 4.49f || ink >= bestBw - 0.01f
            val acc = contrastRatio(l.accent, box)
            val accOk = l.accent == Color.Unspecified || acc >= 2.99f || acc >= bestBw - 0.01f
            if (inkOk && accOk) null else "${s.layout}/${s.theme}/pale=${s.pale} ink=%.2f acc=%.2f".format(ink, contrastRatio(l.accent, box))
        }
        assertTrue("unreadable bar type: ${bad.take(12)} (${bad.size})", bad.isEmpty())
        // The guard is settled: running it again changes nothing.
        seen.forEach { assertSame(it.look, legibleBar(it.look, backdrop(it))) }
        // Only Pocket camera's four-shade palette is left as designed (an owner decision).
        assertEquals(setOf(HomeLayout.CAMERA), seen.filter { it.look.ownPalette }.map { it.layout }.toSet())
    }

    @Test fun everyLayoutAndThemeReadsWithDefaults() = checkAll(allLooks())

    @Test fun everyLayoutAndThemeReadsWithThemeColouredVariants() {
        Features.dashPanel.state.value = Features.PanelLook.THEME
        Features.arRoom.state.value = Features.ArRoom.THEME
        Features.cubeTint.state.value = Features.CubeTint.THEME
        Features.circuitColors.state.value = Features.CircuitColors.THEME
        Features.liveColor.state.value = Features.LiveColor.THEME
        checkAll(allLooks())
    }

    @Test fun liveTileOnPaleAccentGetsDarkTypeAndReadableLooksStayAsDesigned() {
        val theme = AppTheme.SPARK_PX.colors
        val yellow = theme.accent // F9D645
        val live = StatusLook(BarBacking.SEGMENTS, bg = yellow.copy(alpha = 0.92f), accent = Color.White)
        val box = barBoxOver(live.bg, theme.background)
        assertTrue(contrastRatio(Color.White, box) < 2f) // the old look
        val fixed = legibleBar(live, theme.background)
        val fixedBox = barBoxOver(fixed.bg, theme.background)
        // White type stays (the tiles' look); the tile is a deeper yellow (hue kept, same alpha).
        assertEquals(Color.White, fixed.ink)
        assertTrue(contrastRatio(fixed.ink, fixedBox) >= 4.5f)
        assertTrue(contrastRatio(fixed.accent, fixedBox) >= 3f)
        assertEquals(0.92f, fixed.bg.alpha, 0.01f)
        assertTrue(fixed.bg.red > fixed.bg.blue && fixed.bg.green > fixed.bg.blue)
        // A mid-tone tile (Voltcap's orange): white read 2.5:1; now on a deeper orange.
        val volt = AppTheme.VOLTCAP.colors
        val orange = legibleBar(StatusLook(BarBacking.SEGMENTS, bg = volt.accent.copy(alpha = 0.92f), accent = Color.White), volt.background)
        assertEquals(Color.White, orange.ink)
        assertTrue(contrastRatio(orange.ink, barBoxOver(orange.bg, volt.background)) >= 4.5f)
        // A tinted ink moves itself instead (an LCD readout colour on a pale box).
        val lcd = legibleBar(StatusLook(BarBacking.LCD, bg = Color(0xF0DDE6EE), ink = Color(0xFF9EC1DB)), Color.White)
        assertEquals(Color(0xF0DDE6EE), lcd.bg)
        assertTrue(contrastRatio(lcd.ink, barBoxOver(lcd.bg, Color.White)) >= 4.5f)
        // A look that reads keeps every colour (same instance): the Icon bar's dark round boxes with white type.
        val bar = StatusLook(BarBacking.SEGMENTS, bg = Color(0xB3161A21))
        assertSame(bar, legibleBar(bar, theme.background))
        // An accent that doesn't reach 3:1 keeps its hue (lightness moved only): a deep red trim on a dark band.
        val side = StatusLook(BarBacking.TOOLBAR, bg = Color(0xF0080A0E), accent = Color(0xFF8A1020))
        val red = legibleBar(side, Color.Black).accent
        assertTrue(contrastRatio(red, barBoxOver(side.bg, Color.Black)) >= 3f)
        assertTrue(red.red > red.green && red.red > red.blue)
        // No box / a faint one: the scene decides, the guard stays out (shade / shadow carry the type there).
        val pill = StatusLook(BarBacking.CAPSULE, bg = Color.White.copy(alpha = 0.12f))
        assertSame(pill, legibleBar(pill, Color.White))
        val none = StatusLook(BarBacking.NONE, ink = Color(0xFF3A414B))
        assertSame(none, legibleBar(none, Color.Black))
    }

    @Test fun statusLookForRunsEveryLookThroughTheGuard() {
        val s = listOf("src/main/java/com/shelf/launcher/ui/StatusLooks.kt", "app/src/main/java/com/shelf/launcher/ui/StatusLooks.kt")
            .map(::File).first { it.exists() }.readText()
        assertTrue(s.contains("return legibleBar(when (layout) {"))
        assertTrue(s.contains("}, backdrop)"))
    }
}
