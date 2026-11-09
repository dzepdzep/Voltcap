package com.shelf.launcher.ui

import com.shelf.launcher.data.Features
import com.shelf.launcher.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 495 (staging only): D293 settings held during QA previews, overlay titles whole, Channel grid light windows, still charge light. */
class Batch495Test {
    private fun src(rel: String): String = listOf(
        File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")
    ).first { it.exists() }.readText()

    @Test fun previewChangesAreHeldNotSaved() {
        val seen = ArrayList<String>()
        val pref = Features.windowsFollowLayout
        val was = pref.value
        try {
            Features.onChange = { k, o, n, held -> seen += "$k $o->$n $held" }
            Features.holdWrites = { true }
            pref.value = !was
            assertEquals(listOf("windows_follow_layout $was->${!was} true"), seen)
            Features.holdWrites = { false }
            pref.value = was
            assertEquals("windows_follow_layout ${!was}->$was false", seen.last())
        } finally { Features.onChange = null; Features.holdWrites = { false }; pref.value = was }
        val f = src("data/Features.kt")
        assertTrue(f.contains("if (!held) prefs?.edit()?.let { write(it, v); it.apply() }"))
        val t = src("ui/TestHooks.kt")
        // Only deliberate writes get through a preview: snapshot restore and voltcap_test_pref; ending the preview reloads.
        assertEquals(3, Regex("allowWrites = true").findAll(t).count()) // (499: + cloudHold, its three Drive switches OFF only)
        assertTrue(t.contains("if (pvEnd) { previewOnly = false; com.shelf.launcher.data.Features.reload(context)"))
        assertTrue(src("ui/QaTrace.kt").contains("Features.holdWrites = { TestHooks.previewOnly && !TestHooks.allowWrites }"))
    }

    @Test fun overlayShowsWholeTitles() {
        val o = src("ui/RaOverlayView.kt")
        val header = o.substring(o.indexOf("private val header = TextView"), o.indexOf("private val headerBar"))
        assertTrue(!header.contains("ellipsize") && !header.contains("maxLines"))
        val row = o.substring(o.indexOf("val texts = LinearLayout(context)"), o.indexOf("line.addView(texts"))
        assertTrue(!row.contains("ellipsize") && !row.contains("maxLines"))
        assertEquals(280f, SUMMARY_MAX_W_DP)
        assertTrue(o.contains("private val sumName = summaryLine(11f, Color.WHITE, 3, bold = true)"))
    }

    @Test fun channelWindowIsLightAndReadable() {
        val c = channelWindowColors(com.shelf.launcher.ui.theme.AppTheme.EMBER.colors)
        assertTrue(contrastRatio(c.textPrimary, c.surface) >= 7f)
        assertTrue(contrastRatio(c.textPrimary, c.surfaceRaised) >= 4.5f)
        assertTrue(contrastRatio(c.textMuted, c.surface) >= 4.5f)
        assertTrue(maxOf(contrastRatio(androidx.compose.ui.graphics.Color.White, c.accent), contrastRatio(androidx.compose.ui.graphics.Color.Black, c.accent)) >= 4.5f) // (497: the real accent; text on it white or black)
        val w = src("ui/WindowLook.kt")
        assertTrue(w.contains("if (style == WindowLook.Style.OWN) { content(); return }")) // (496: Cross passes its style on, colours unchanged)
        // Both windows put their content inside WindowColors; tints that vanished on light panels follow the writing colour.
        assertTrue(src("ui/SettingsDialog.kt").contains(") { WindowColors(winStyle) {"))
        assertTrue(src("ui/DialogChrome.kt").contains(") { WindowColors(winStyle) {"))
        assertTrue(src("ui/SettingsSlider.kt").contains("inactiveTrackColor = inkTint(0.15f)"))
    }

    @Test fun chargingLightHoldsStillWhenNotMoving() {
        val h = src("ui/HomeChrome.kt")
        assertTrue(h.contains("if (!moving) { phase.floatValue = CHARGE_STILL_PHASE; return@LaunchedEffect }"))
        assertTrue(!h.substring(h.indexOf("internal fun PlainBattery"), h.indexOf("internal fun chargingLevelIcon")).contains("BatteryChargingFull"))
    }
}
