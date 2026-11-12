package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Features
import org.junit.Assert.assertEquals
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
 * Shelf 3D hand-over (12 Nov): the first frames of the turn at 3 × scale, drawn by Android's graphics on the PC. Each cell is
 * what the Game shelf draws at that moment with the 3D case on: the shelf's own spine (its picture, its drawn top - drawCaseTop
 * - and its shading - drawSpineDepth - with ShelfHome's transform) at 1 − blendIn, and Case3D's face renderer at blendIn.
 * Row 2: the shelf's spine alone (the old path) at the same moments, for comparison. Written to build/shelf3d/handover.png.
 * Checks: the hand-over frame is the shelf's spine pixel for pixel; no frame is emptier than its neighbours (no blank / gap).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1400dp-h420dp-land-mdpi")
class ShelfHandOverFramesTest {
    private val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private val S = 3f                                   // 3 × scale
    private val cellW = 140f; private val cellH = 200f
    private val slotH = 50f; private val slotW = 4.6f     // a KONKR shelf's spine, in dp
    private val caseShape = 0.7f
    private val turns = listOf(0f, 0.02f, 0.05f, 0.1f, 0.15f, 0.2f, 0.3f, 0.45f, 0.6f)

    private fun spineArt(): Bitmap {
        val b = Bitmap.createBitmap(40, 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(0xFFC8402F.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f; isFakeBoldText = true }
        c.save(); c.rotate(90f, 20f, 200f); c.drawText("MY SPINE", 20f - 60f, 208f, p); c.restore()
        return b
    }
    private fun frontArt(): Bitmap {
        val b = Bitmap.createBitmap(280, 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(0xFF2F62C8.toInt())
        c.drawText("FRONT", 40f, 220f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 56f })
        return b
    }

    @Test fun firstFramesOfTheTurn() {
        val before = Features.spineDepth.state.value
        Features.spineDepth.state.value = Features.SpineDepth.REALISTIC       // the strongest drawn top and shading
        try { render() } finally { Features.spineDepth.state.value = before }
    }

    private fun render() {
        val spine = spineArt(); val front = frontArt(); val back = frontArt()
        val look = CaseLook.of(null, CASE_KEEP, front, spine)
        val depth = slotW / slotH
        val cx = cellW / 2; val bottom = cellH * 0.8f
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column(Modifier.testTag("sheet").background(androidx.compose.ui.graphics.Color(0xFF15100A))) {
                for (row in 0..1) Row {
                    for (t in turns) Box(Modifier.size((cellW * S).dp, (cellH * S).dp)) {
                        val shelfAlpha = if (row == 1) 1f else 1f - Shelf3D.blendIn(t)
                        // the shelf's own spine, as ShelfHome draws it (lifted, turning round its middle, beside the front)
                        if (shelfAlpha > 0f && t < 0.999f) Image(spine.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                            modifier = Modifier.offset(((cx - slotW / 2) * S).dp, ((bottom - slotH) * S).dp).size((slotW * S).dp, (slotH * S).dp)
                                .drawBehind { drawCaseTop(Features.SpineDepth.REALISTIC, (cellW / 2 - (cx - slotW / 2)) * S, false) }
                                .drawWithContent { drawContent(); drawSpineDepth(Features.SpineDepth.REALISTIC, 7) }
                                .graphicsLayer {
                                    alpha = shelfAlpha
                                    rotationY = 90f * t; cameraDistance = 14f * density
                                    translationX = shelfBoxFaces(t, slotW * S * density, slotH * caseShape * S * density).first
                                })
                        // the 3D case (row 0 only)
                        if (row == 0) ComposeCanvas(Modifier.fillMaxSize().graphicsLayer { alpha = Shelf3D.blendIn(t) }) {
                            val yaw = 90f * (1f - t); val pitch = Shelf3D.TURN_PITCH * kotlin.math.sin(Math.PI * t).toFloat()
                            val h = slotH * nearFaceCorrection(yaw, caseShape, depth) * S * density
                            CaseDraw.draw(drawContext.canvas.nativeCanvas, cx * S * density, (bottom - slotH / 2) * S * density, h * caseShape, h, h * depth, yaw, pitch,
                                front, spine, 1f, false, 0, 0f, back, look)
                        }
                    }
                }
            }
        }
        repeat(20) { compose.mainClock.advanceTimeByFrame(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }
        val root = compose.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(full))
        val b = compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val sheet = Bitmap.createBitmap(full, b.left.toInt(), b.top.toInt(), b.width.toInt().coerceAtMost(full.width - b.left.toInt()), b.height.toInt().coerceAtMost(full.height - b.top.toInt()))
        val lc = Canvas(sheet); val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 22f }
        turns.forEachIndexed { i, t -> lc.drawText("new turn ${"%.2f".format(t)}", i * cellW * S + 8, cellH * S - 12, lp); lc.drawText("old turn ${"%.2f".format(t)}", i * cellW * S + 8, 2 * cellH * S - 12, lp) }
        val out = java.io.File("build/shelf3d"); out.mkdirs()
        java.io.FileOutputStream(java.io.File(out, "handover.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // The hand-over frame (turn 0): the new path is the shelf's spine, pixel for pixel.
        val cw = (cellW * S).toInt(); val ch = (cellH * S).toInt()
        var diff = 0
        for (x in 0 until cw) for (y in 0 until ch - 40) if (sheet.getPixel(x, y) != sheet.getPixel(x, y + ch)) diff++
        assertEquals("hand-over frame differs from the shelf's own spine in $diff px", 0, diff)
        // No frame emptier than the hand-over (no blank, no gap while it fades): drawn pixels per new cell.
        val bg = sheet.getPixel(2, 2)
        val drawn = turns.indices.map { i -> var n = 0; for (x in i * cw until (i + 1) * cw step 2) for (y in 0 until ch - 40 step 2) if (sheet.getPixel(x, y) != bg) n++; n }
        println("SHELF3D handover drawn px per frame: $drawn")
        assertTrue("a frame of the hand-over is emptier than the spine alone: $drawn", drawn.all { it >= drawn[0] * 0.9 })
    }
}
