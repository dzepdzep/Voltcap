package com.shelf.launcher.ui

import com.shelf.launcher.ui.theme.PixelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 679b (owner decision B): pixel text at 62.5 %, small text keeps a readable minimum, never larger than before. */
class PixelType679bTest {
    @Test fun drawnRule() {
        for (v in listOf(4f, 6f, 7f, 8.5f, 9f, 11f, 12f, 13.6f, 16f, 20f, 32f)) {
            val d = PixelType.drawn(v)
            assertTrue("never larger than before: $v -> $d", d <= v + 1e-4f)
            assertTrue("readable minimum: $v -> $d", d >= minOf(v, PixelType.MIN_SP) - 1e-4f)
            assertTrue("62.5 % baseline: $v -> $d", d >= v * PixelType.SCALE - 1e-4f)
        }
        assertEquals(20f * 0.625f, PixelType.drawn(20f), 1e-4f)   // big text: the baseline
        assertEquals(8.5f, PixelType.drawn(11f), 1e-4f)          // a small label: the minimum
        assertEquals(7f, PixelType.drawn(7f), 1e-4f)             // already small: unchanged
    }

    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n").map(::File).first { it.exists() }.readText()

    /** Every place that draws the pixel face routes its sizes through the shared rule (no per-layout factors). */
    @Test fun wired() {
        assertTrue(src("PocketShared.kt").contains("PixelType.size(with(androidx.compose.ui.unit.Density(density)) { (u * dots).toSp() })"))
        assertTrue(src("PocketShared.kt").contains("internal fun pocketFont(): FontFamily = com.shelf.launcher.ui.theme.PixelType.family"))
        assertTrue(src("theme/Theme.kt").contains("fontSize = PixelType.size(fontSize * k)"))
        assertTrue(src("StylePocketCamera.kt").contains("PixelType.size(with(density) { maxOf((u * k).toSp().value, k * 2.1f).sp })"))
        assertTrue(src("WinScenesConsole.kt").contains("PixelType.size(with(density) { (u * k).toSp() })"))
        assertTrue(src("StyleTerminal.kt").contains("PixelType.sizeFor(font, fsz)"))
        assertTrue(src("HomeChrome.kt").contains("PixelType.sizeFor(look.font, 12.sp)"))
        assertTrue(!src("WinScenesStyled.kt").contains("fontSize = 13.sp, fontFamily = font"))
        // only the one pixel family instance
        assertTrue(!src("theme/Theme.kt").contains("Font(com.shelf.launcher.R.font.volt_pixel)"))
    }
}
