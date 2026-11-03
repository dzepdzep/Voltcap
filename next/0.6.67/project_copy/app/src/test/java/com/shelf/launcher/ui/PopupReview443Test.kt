package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v0.6.66 update 443 popup review: Row opens up ran its text and buttons off the right edge (wide cards: Cinema, Channel grid;
 * the 960×640 GT78), Row reveal / Shared move showed the whole-screen layout's own title under the window's (the name twice), and
 * Shared move's story ran off the bottom of the GT78 ending in "…". Source checks (the windows are full Compose dialogs).
 */
class PopupReview443Test {
    private fun src(name: String): String {
        val roots = listOf("src_copy/ui/", "../src_copy/ui/", "app/src/main/java/com/shelf/launcher/ui/", "src/main/java/com/shelf/launcher/ui/")
        return roots.map { File(it + name) }.first { it.exists() }.readText().replace("\r\n", "\n")
    }

    @Test fun rowOpensKeepsItsCoverAndButtonsOnScreen() {
        val s = src("RowRevealWindow.kt")
        assertTrue("cover capped at 42 % of the width, shape kept", s.contains("val maxW = W * 0.42f") && s.contains("if (w > maxW) { w = maxW; h = w / aspect }"))
        assertTrue("buttons wrap inside the text column", s.contains("androidx.compose.foundation.layout.FlowRow("))
        assertTrue("buttons never above the name", s.contains("titleTo.y + titleSize.height + with(d) { 12.dp.toPx() }"))
        assertTrue("no 200 dp floor past the screen", !s.contains("coerceAtLeast(with(d) { 200.dp.toPx() })"))
    }

    @Test fun wholeScreenLayoutsAreShadedUnderTheWindow() {
        assertTrue(src("RowRevealWindow.kt").contains("if (WindowShade.on(state.appearance.homeLayout)) Canvas("))
        assertTrue(src("GlanceWindow.kt").contains("if (WindowShade.on(state.appearance.homeLayout)) Spacer("))
    }

    @Test fun sharedMoveStoryScrollsInsteadOfEllipsis() {
        val s = src("GlanceWindow.kt")
        assertTrue(s.contains("Box(Modifier.heightIn(max = boxH).verticalScroll(story))"))
        assertTrue(!s.contains("maxLines = (room / with(d) { 19.dp.toPx() }).toInt().coerceIn(1, 5), overflow = TextOverflow.Ellipsis"))
    }

    @Test fun carouselPulseIsReadWhileDrawing() {
        val s = src("Carousel.kt")
        assertTrue(s.contains("rememberFocusPulseState(animate = pulseOn)") && !s.contains("val pulse = if (on) rememberFocusPulse(animate = pulseOn) else 0f"))
    }
}
