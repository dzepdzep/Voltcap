package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Shelf 3D work (12 Nov): the REAL Case3D drawn by Android's graphics on the PC (Robolectric native graphics) at the pull's
 * poses (shelfCasePose), written to build/shelf3d/render.png - evidence of the shape and light, not of the handheld (its GPU,
 * its fonts and the game's real pictures are not in this: no cover is loaded, the case shows Voltcap's plain front).
 * Checks: every frame drew something case-coloured, and the drawn width follows the turn (spine-thin at the start, the front
 * at the end) - a flat picture turning would not grow a visible side.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1400dp-h340dp-land-mdpi")
class ShelfCase3DRenderTest {
    private val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun ease(t: Float) = androidx.compose.animation.core.FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))
    private val slide = { p: Float -> ease(p / 0.3f) }
    private val turn = { p: Float -> ease((p - 0.3f) / 0.4f) }
    private val grow = { p: Float -> ease((p - 0.62f) / 0.38f) }
    private fun pFor(t: Float): Float { var lo = 0.3f; var hi = 0.7f; repeat(40) { val m = (lo + hi) / 2; if (turn(m) < t) lo = m else hi = m }; return (lo + hi) / 2 }

    @Test fun renderTheTurn() {
        val frames = listOf(0.001f, 0.12f, 0.28f, pFor(0.25f), pFor(0.5f), pFor(0.75f), 0.7f, 1f)
        val g = Game("Probe Quest", "PS2", 3, SourceType.ROM, romPath = "/r/p.iso")
        val slotH = 150f; val slotW = 13f; val bigH = 260f; val shape = 0.7f; val depth = slotW / slotH
        val poses = frames.map { p -> shelfCasePose(p, 0f, 0f, slotH, 0f, 0f, bigH, shape, depth, 0.42f, slide, turn, grow) }
        // (Case3D keeps its loading shimmer moving: frames are stepped, and the activity's view is drawn into a bitmap -
        // captureToImage waits for an idle window that never comes.)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Row(Modifier.testTag("sheet").background(Color(0xFF15100A))) {
                for (pp in poses) Box(Modifier.size(170.dp, 320.dp)) {
                    Box(Modifier.size((pp.h * 2.2f / 1f).dp.coerceAtMost(170.dp), pp.h.dp).align(androidx.compose.ui.Alignment.Center)) {
                        Case3D(g, null, null, pp.yaw, shape, Modifier.fillMaxSize(), pitch = pp.pitch, fill = 1f, depthOverride = depth)
                    }
                }
            }
        }
        repeat(90) { compose.mainClock.advanceTimeByFrame(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }
        val root = compose.activity.window.decorView
        val full = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(full))
        val sheetBounds = compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val bmp = android.graphics.Bitmap.createBitmap(full, sheetBounds.left.toInt(), sheetBounds.top.toInt(),
            sheetBounds.width.toInt().coerceAtMost(full.width - sheetBounds.left.toInt()), sheetBounds.height.toInt().coerceAtMost(full.height - sheetBounds.top.toInt()))
        val out = java.io.File("build/shelf3d"); out.mkdirs()
        java.io.FileOutputStream(java.io.File(out, "render.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        // drawn width of each cell (pixels that are not the background)
        val bg = android.graphics.Color.rgb(0x15, 0x10, 0x0A)
        val cell = bmp.width / frames.size
        val widths = (frames.indices).map { n ->
            var minX = Int.MAX_VALUE; var maxX = -1
            for (x in n * cell until (n + 1) * cell) for (y in 0 until bmp.height step 2) {
                val c = bmp.getPixel(x, y)
                if (kotlin.math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(bg)) + kotlin.math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(bg)) +
                    kotlin.math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(bg)) > 24) { if (x < minX) minX = x; if (x > maxX) maxX = x }
            }
            if (maxX < 0) 0 else maxX - minX + 1
        }
        println("SHELF3D drawn widths per frame: $widths")
        assertTrue("every frame drew the case: $widths", widths.all { it > 0 })
        assertTrue("spine-on is thin, front-on is wide: $widths", widths.first() * 3 < widths.last())
        assertTrue("mid-turn shows two faces (wider than the spine alone): $widths", widths[4] > widths.first() * 2)
    }
}
