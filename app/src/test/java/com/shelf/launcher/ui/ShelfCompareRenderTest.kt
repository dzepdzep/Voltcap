package com.shelf.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
 * Shelf 3D work (12 Nov): OLD vs NEW Game shelf pull-out, drawn by Android's graphics on the PC (Robolectric native graphics),
 * same slot, same big place, same pictures, same pull at each frame.
 *  - OLD (top row): the two flat pictures with the exact transforms of ShelfHome.kt before the 3D case - the shelf's spine
 *    turned +90°·t round its own middle and moved by shelfBoxFaces, the front turned −90°·(1 − t) round its own middle, both
 *    with cameraDistance 14 × density; sizes, slide and lift as in ShelfHome (PULL_LIFT 0.42).
 *  - NEW (bottom row): Case3D's own face renderer (CaseDraw) at shelfCasePose.
 * The pictures are made here: a blue FRONT with an up arrow and an "L" top left (a mirrored or upside-down face shows at once),
 * a red custom spine, a green BACK. Written to build/shelf3d/compare.png. Not the handheld (its GPU, its decode timing).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1800dp-h640dp-land-mdpi")
class ShelfCompareRenderTest {
    private val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun ease(t: Float) = androidx.compose.animation.core.FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))
    private val slide = { p: Float -> ease(p / 0.3f) }
    private val turn = { p: Float -> ease((p - 0.3f) / 0.4f) }
    private val grow = { p: Float -> ease((p - 0.62f) / 0.38f) }
    private fun pFor(t: Float): Float { var lo = 0.3f; var hi = 0.7f; repeat(40) { val m = (lo + hi) / 2; if (turn(m) < t) lo = m else hi = m }; return (lo + hi) / 2 }

    // The cell: 220 × 300; the slot stands on the board at y 270, the big place above it.
    private val cellW = 220f; private val cellH = 300f
    private val slotH = 120f; private val slotW = 11f; private val shape = 0.7f; private val bigH = 230f
    private val slotCx = 110f; private val slotBottom = 270f; private val bigTop = 20f; private val lift = 0.42f

    private fun art(w: Int, h: Int, bg: Int, label: String, marks: Boolean): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = w * 0.18f; isFakeBoldText = true }
        if (marks) {
            c.drawText(label, w * 0.12f, h * 0.55f, p)
            p.textSize = w * 0.16f; c.drawText("L", w * 0.06f, h * 0.12f, p)
            val a = android.graphics.Path().apply { moveTo(w * 0.5f, h * 0.18f); lineTo(w * 0.62f, h * 0.32f); lineTo(w * 0.38f, h * 0.32f); close() }
            c.drawPath(a, p)
        } else {
            c.save(); c.rotate(90f, w / 2f, h / 2f); p.textSize = w * 0.55f; c.drawText(label, w / 2f - h * 0.32f, h / 2f + w * 0.2f, p); c.restore()
        }
        return b
    }

    @Test fun oldVersusNew() {
        val front = art(280, 400, 0xFF2F62C8.toInt(), "FRONT", true)
        val spine = art(40, 400, 0xFFC8402F.toInt(), "MY SPINE", false)
        val back = art(280, 400, 0xFF2F9A52.toInt(), "BACK", true)
        val look = CaseLook.of(null, CASE_KEEP, front, spine)
        val names = listOf("on the shelf", "extracting", "turn 25%", "turn 50%", "turn 75%", "front", "reverse 50%", "returned")
        val ps = listOf(0.0005f, 0.15f, pFor(0.25f), pFor(0.5f), pFor(0.75f), 1f, pFor(0.5f), 0.0005f)
        val depth = slotW / slotH
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column(Modifier.testTag("sheet").background(androidx.compose.ui.graphics.Color(0xFF15100A))) {
                // OLD: ShelfHome's two flat pictures
                Row {
                    for (p in ps) Box(Modifier.size(cellW.dp, cellH.dp)) {
                        Board()
                        val t = turn(p); val gr = grow(p)
                        val startW = slotH * shape
                        // the shelf's spine, turning as one face of the box (hidden once the turn is done)
                        if (t < 0.999f) Image(spine.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                            modifier = Modifier.offset((slotCx - slotW / 2).dp, (slotBottom - slotH).dp).size(slotW.dp, slotH.dp).graphicsLayer {
                                translationY = -slotH * lift * slide(p) * density
                                rotationY = 90f * t
                                cameraDistance = 14f * density
                                translationX = shelfBoxFaces(t, slotW * density, startW * density).first
                            })
                        // the pulled-out front, coming round from edge-on, then growing
                        if (t > 0f) {
                            val w = startW + (bigH * shape - startW) * gr; val h = slotH + (bigH - slotH) * gr
                            val startX = slotCx - startW / 2; val endX = slotCx - bigH * shape / 2
                            val faceShift = shelfBoxFaces(t, slotW, startW).second
                            val x = startX + (endX - startX) * gr + faceShift
                            val startY = slotBottom - slotH - slotH * lift; val y = startY + (bigTop - startY) * gr
                            Image(front.asImageBitmap(), null, contentScale = ContentScale.FillBounds,
                                modifier = Modifier.offset(x.dp, y.dp).size(w.dp, h.dp).graphicsLayer {
                                    rotationY = -90f * (1f - t); cameraDistance = 14f * density
                                })
                        }
                    }
                }
                // NEW: one rigid Case3D
                Row {
                    for (p in ps) Box(Modifier.size(cellW.dp, cellH.dp)) {
                        Board()
                        val pp = shelfCasePose(p, slotCx, slotBottom, slotH, slotCx, bigTop, bigH, shape, depth, lift, slide, turn, grow)
                        androidx.compose.foundation.Canvas(Modifier.size(cellW.dp, cellH.dp)) {
                            val k = density
                            CaseDraw.draw(drawContext.canvas.nativeCanvas, pp.cx * k, pp.cy * k, pp.h * shape * k, pp.h * k, pp.h * depth * k, pp.yaw, pp.pitch,
                                front, spine, 1f, false, 0, 0f, back, look)
                        }
                    }
                }
            }
        }
        repeat(30) { compose.mainClock.advanceTimeByFrame(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }
        val root = compose.activity.window.decorView
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(full))
        val b = compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val sheet = Bitmap.createBitmap(full, b.left.toInt(), b.top.toInt(), b.width.toInt().coerceAtMost(full.width - b.left.toInt()), b.height.toInt().coerceAtMost(full.height - b.top.toInt()))
        // labels under each frame
        val lc = Canvas(sheet); val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 13f }
        names.forEachIndexed { i, n -> lc.drawText("OLD  $n", i * cellW + 6, 292f, lp); lc.drawText("NEW  $n", i * cellW + 6, cellH + 292f, lp) }
        val out = java.io.File("build/shelf3d"); out.mkdirs()
        java.io.FileOutputStream(java.io.File(out, "compare.png")).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // The NEW row: never the BACK's green; the FRONT's blue only once it has started turning; the custom spine's red
        // on the shelf (never the dark stand-in: the spine given is drawn).
        fun count(row: Int, col: Int, pred: (Int) -> Boolean): Int {
            var n = 0
            for (x in (col * cellW).toInt() until ((col + 1) * cellW).toInt()) for (y in (row * cellH).toInt() until (row * cellH + 270).toInt() step 2) if (pred(sheet.getPixel(x, y))) n++
            return n
        }
        val green = { c: Int -> Color.green(c) > 120 && Color.red(c) < 90 && Color.blue(c) < 120 }
        val red = { c: Int -> Color.red(c) > 140 && Color.green(c) < 90 && Color.blue(c) < 90 }
        val blue = { c: Int -> Color.blue(c) > 140 && Color.red(c) < 90 }
        for (i in ps.indices) assertTrue("NEW frame ${names[i]} shows the BACK", count(1, i, green) < 5)
        assertTrue("NEW on the shelf: the custom spine is drawn", count(1, 0, red) > 50)
        assertTrue("NEW front: the front is drawn", count(1, 5, blue) > 2000)
        assertTrue("NEW mid-turn: spine and front both show (one box)", count(1, 3, red) > 20 && count(1, 3, blue) > 200)
    }

    @androidx.compose.runtime.Composable
    private fun Board() {
        Box(Modifier.offset(0.dp, slotBottom.dp).size(cellW.dp, 8.dp).background(androidx.compose.ui.graphics.Color(0xFF6B4A2E)))
    }
}
