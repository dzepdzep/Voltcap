package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import com.shelf.launcher.data.Features
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 504 (staging only): Inspect pauses while handled and settles back; Disc Station / Circuit popups in their layout's colours. */
class Batch504Test {
    private fun src(rel: String): String = listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    @Test fun inspectSettlesBackQuicklyAfterTheDefaultThreeSeconds() {
        assertTrue(INSPECT_IDLE_RETURN_MS in 300..400)
        assertEquals(Features.InspectHold.S3, Features.inspectHold.default)
        val t = src("ui/MediaInspectView.kt")
        // Hands on: paused at the first finger; the wait only starts when the last one lifts.
        assertTrue(t.contains("motion.touchDown() // (Update 504"))
        assertTrue(t.contains("motion.touchUp(scope) // (Update 504"))
        assertTrue(t.contains("if (touch && fingers > 0) return"))
        // The stick keeps the owner's "Turning back" speed; zoom and pan always come back.
        assertTrue(t.contains("motion.release(this, touch = false)"))
        assertTrue(t.contains("zoom.floatValue = z0 + (1f - z0) * v"))
        // The case content's turn is a pausable clock (no restart, no jump); a pinch never taps.
        assertTrue(t.contains("motion.contentPhase.floatValue = (motion.contentPhase.floatValue + dt / 12_000f) % 1f"))
        assertTrue(!t.contains("rememberInfiniteTransition(label = \"contentSpin\")"))
        assertTrue(t.contains("if (!motion.pinchedJustNow()) inspectTap("))
    }

    /** Device finding (Thor, 504): a tap that opened the case / took the cartridge out cancelled the settle-back and left the
     *  idle motion paused until the next drag. Every case step now ends by resuming it (never with a finger down). */
    @Test fun caseStepsResumeTheIdleMotion() {
        val m = src("ui/MediaInspectView.kt")
        assertTrue(m.contains("private fun caseStepDone() {"+"\n        if (fingers == 0) idlePaused.value = false") ||
            m.contains("private fun caseStepDone() {"+"\r\n        if (fingers == 0) idlePaused.value = false"))
        // toggle, openLid, takeOut, putBack (closeCase ends in startSway, which resumes it too)
        assertTrue(Regex("caseStepDone\\(\\) // \\(Update 504\\)").findAll(m).count() == 4)
    }

    @Test fun circuitSheetFollowsTheBoardColours() {
        val green = circuitSheetColorsFor(Features.CircuitColors.GREEN, Color.White, Color.Red)
        assertEquals(Color(0xFF0D3B26), green.first)
        val surface = Color(0xFF1A1C22); val accent = Color(0xFF3355FF)
        val theme = circuitSheetColorsFor(Features.CircuitColors.THEME, surface, accent)
        assertEquals(surface, theme.first)
        assertTrue(com.shelf.launcher.ui.theme.contrastRatio(theme.second, theme.first) >= 2.9f)
        assertTrue(com.shelf.launcher.ui.theme.contrastRatio(theme.third, theme.first) >= 4.5f)
        val light = circuitSheetColorsFor(Features.CircuitColors.THEME, Color(0xFFF3F4F6), accent)
        assertTrue(com.shelf.launcher.ui.theme.contrastRatio(light.third, light.first) >= 4.5f)
    }

    @Test fun discStationDeckUsesTheLayoutAccent() {
        val t = src("ui/WinScenesMore.kt")
        assertTrue(t.contains("legibleArtAccent(accent, lcdBg, lcdBg)"))
        assertTrue(!t.contains("val lcdBg = Color(0xFF0F2A2A); val lcd = Color(0xFF7BF2D6)"))
    }
}
