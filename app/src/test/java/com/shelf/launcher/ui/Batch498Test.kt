package com.shelf.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.shelf.launcher.data.Features.ChanPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 498 (staging only): Inspect pinch / pan / reset; PC fixture sheet of the layout-aware window colours. */
class Batch498Test {
    private fun root(): File = listOf(File("src/main/java/com/shelf/launcher"), File("app/src/main/java/com/shelf/launcher")).first { it.exists() }
    private fun src(rel: String): String = File(root(), rel).readText()

    @Test fun inspectPinchStaysInBounds() {
        // Zoom in 2x on a 400 x 300 piece, then pan far: it stops where the piece still covers its place.
        var (z, x, y) = inspectPinchStep(1f, 0f, 0f, 2f, 0f, 0f, 400f, 300f)
        assertEquals(2f, z, 0.001f)
        val p = inspectPinchStep(z, x, y, 1f, 900f, -900f, 400f, 300f); z = p.first; x = p.second; y = p.third
        assertEquals(200f, x, 0.001f); assertEquals(-150f, y, 0.001f)
        // Never past the maximum, never below 1; at 1 the pan snaps back to the middle.
        assertEquals(INSPECT_ZOOM_MAX, inspectPinchStep(2.9f, 0f, 0f, 5f, 0f, 0f, 400f, 300f).first, 0.001f)
        val back = inspectPinchStep(z, x, y, 0.1f, 0f, 0f, 400f, 300f)
        assertEquals(1f, back.first, 0.001f); assertEquals(0f, back.second, 0.001f); assertEquals(0f, back.third, 0.001f)
        // A bad zoom value from the platform changes nothing.
        assertEquals(1.5f, inspectPinchStep(1.5f, 0f, 0f, Float.NaN, 0f, 0f, 400f, 300f).first, 0.001f)
        val m = src("ui/MediaInspectView.kt")
        // Two fingers only, Initial pass; draw-time reads (no recomposition per pinch frame); Y / long press reset it.
        assertTrue(m.contains("if (down >= 2) {"))
        assertTrue(m.contains("awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)"))
        assertTrue(m.contains("val z = 1f + (motion.zoom.floatValue - 1f) * k"))
        assertTrue(m.contains("resetZoom() // (Update 498"))
        // The one-finger turn and the tap are unchanged.
        assertTrue(m.contains("if (drag.x != 0f) motion.nudge(drag.x * 0.42f * motion.yawSign())"))
        assertTrue(m.contains("detectTapGestures(onLongPress = { if (!motion.pinchedJustNow()) motion.resetPose(scope) }")) // (504: not right after a pinch)
    }

    /** A labelled PC fixture (token maths drawn with java.awt) — NOT a device screenshot. Written to build/fixtures498. */
    @Test fun windowFixtureSheet() {
        System.setProperty("java.awt.headless", "true")
        val pale = chanColorsFor(ChanPalette.PALE, 0)
        val theme = Triple(Color(0xFF14161C), Color(0xFF1E2129), Color(0xFF2A2E38))
        val blue = Color(0xFF3D7BFF); val purple = Color(0xFF8E44D8)
        data class Cell(val label: String, val t: ChannelWindowTokens?, val rim: Color? = null)
        fun frame(acc: Color) = lerp(Color.White.copy(alpha = 0.12f), acc, 0.45f)
        val cells = listOf(
            Cell("OFF (default): own window", null),
            Cell("Channel · Pale · theme blue", channelWindowTokens(blue, pale, true, true, theme, Color.White, Color(0xFFB0B6C0))),
            Cell("Channel · Pale · owner purple", channelWindowTokens(purple, pale, true, true, theme, Color.White, Color(0xFFB0B6C0))),
            Cell("Channel · Light Yellow · purple", channelWindowTokens(purple, chanColorsFor(ChanPalette.YELLOW, 0), true, true, theme, Color.White, Color(0xFFB0B6C0))),
            Cell("Channel · OLED · purple", channelWindowTokens(purple, chanColorsFor(ChanPalette.OLED, 0), false, true, theme, Color.White, Color(0xFFB0B6C0))),
            Cell("Channel · Custom dark · purple", channelWindowTokens(purple, chanColorsFor(ChanPalette.CUSTOM, 0xFF402060.toInt()), false, true, theme, Color.White, Color(0xFFB0B6C0))),
            Cell("Arctic · theme blue", arcticWindowTokens(blue)),
            Cell("Arctic · owner purple", arcticWindowTokens(purple)),
            Cell("Cinema · Cinema red", cinemaWindowTokens(CINEMA_RED)),
            Cell("Cinema · theme purple", cinemaWindowTokens(purple)),
            Cell("Shelf (frame only) · blue", null, frame(blue)),
            Cell("Shelf (frame only) · purple", null, frame(purple)),
        )
        val cw = 300; val ch = 190; val cols = 3; val rows = (cells.size + cols - 1) / cols
        val img = java.awt.image.BufferedImage(cols * cw, rows * ch + 30, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = java.awt.Color(0x0B0C10); g.fillRect(0, 0, img.width, img.height)
        fun c(x: Color) = java.awt.Color(x.toArgb(), true)
        g.color = java.awt.Color(0xFFD27A); g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 14)
        g.drawString("PC FIXTURE — window colour tokens from WindowLook.kt (update 498). Not a device screenshot.", 10, 20)
        cells.forEachIndexed { i, cell ->
            val x0 = (i % cols) * cw + 10; val y0 = (i / cols) * ch + 40; val w = cw - 20; val h = ch - 20
            val t = cell.t
            val surface = t?.colors?.surface ?: theme.second
            val ink = t?.colors?.textPrimary ?: Color(0xFFECEFF4)
            val muted = t?.colors?.textMuted ?: Color(0xFFB0B6C0)
            val acc = t?.colors?.accent ?: (if (cell.rim != null) (if (cell.label.contains("purple")) purple else blue) else blue)
            g.paint = java.awt.GradientPaint(0f, y0.toFloat(), c(t?.frameTop ?: surface), 0f, (y0 + h).toFloat(), c(t?.frameBottom ?: surface))
            g.fillRoundRect(x0, y0, w, h, 26, 26)
            g.color = c(t?.rim ?: cell.rim ?: Color.White.copy(alpha = 0.08f)); g.stroke = java.awt.BasicStroke(2f); g.drawRoundRect(x0, y0, w, h, 26, 26)
            g.color = c(ink); g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 13); g.drawString(cell.label, x0 + 12, y0 + 24)
            g.color = c(muted); g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12); g.drawString("Muted line · settings text", x0 + 12, y0 + 44)
            g.color = c(t?.colors?.surfaceRaised ?: theme.third); g.fillRoundRect(x0 + 12, y0 + 56, w - 24, 44, 14, 14)
            g.color = c(ink); g.drawString("Card on a raised panel", x0 + 22, y0 + 83)
            g.color = c(acc); g.fillRoundRect(x0 + 12, y0 + 112, 92, 30, 14, 14)
            g.color = c(t?.onAccent ?: Color.White); g.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 12); g.drawString("Play", x0 + 44, y0 + 132)
            g.color = c(t?.track ?: Color.White.copy(alpha = 0.1f)); g.fillRoundRect(x0 + 120, y0 + 118, 46, 20, 20, 20)
            g.color = c(acc); g.fillOval(x0 + 146, y0 + 118, 20, 20)
            g.color = c(t?.soft ?: acc.copy(alpha = 0.2f)); g.fillRoundRect(x0 + 180, y0 + 114, w - 192, 28, 14, 14)
            g.color = c(t?.deep ?: ink); g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11); g.drawString("Selected", x0 + 190, y0 + 132)
        }
        g.dispose()
        val out = File(File(root(), "../../../../../..").canonicalFile, "build/fixtures498").apply { mkdirs() }
        javax.imageio.ImageIO.write(img, "png", File(out, "windows498_fixture.png"))
        assertTrue(File(out, "windows498_fixture.png").length() > 1000)
        assertFalse(cells.any { it.t != null && it.t.colors.accent == WINDOW_CHANNEL_BLUE })
    }
}
