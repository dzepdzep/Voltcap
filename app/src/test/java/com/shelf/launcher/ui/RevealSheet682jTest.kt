package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * (682j, H12 "reveal / trophy redesign - comparison for the owner first") The new-unlock reveal as a storyboard: the app's
 * Volt reveal (unchanged) next to two PROPOSALS that exist only in this test - nothing in the app plays them. Drawn by the real
 * StageSlot (Robolectric native graphics) at 9 moments of one badge's reveal, written to
 * app/build/reports/tests/h12-reveal/storyboard.png (uploaded with CI's "update-310-test-results-*" artifact).
 * Not handheld evidence: no badge picture is loaded (plain plate), no GPU, no sound.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1100dp-h620dp-land-mdpi")
class RevealSheet682jTest {
    private val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun seg(p: Float, a: Float, z: Float) = ((p - a) / (z - a)).coerceIn(0f, 1f)
    private fun ease(t: Float) = FastOutSlowInEasing.transform(t)

    /** Proposal A "Trophy flip": face-down badge charges, lifts and turns over, a gold ring bursts, a shine sweeps, it settles. */
    private val trophyFlip = { p: Float, waiting: Boolean, _: Float ->
        val lift = 22f * ease(seg(p, 0.15f, 0.35f)) * (1f - ease(seg(p, 0.80f, 0.95f)))
        val turn = 180f * (1f - ease(seg(p, 0.30f, 0.58f)))
        val ring = if (p in 0.56f..0.90f) seg(p, 0.56f, 0.90f).coerceAtLeast(0.01f) else 0f
        val flash = if (p in 0.52f..0.70f) 1f - abs((p - 0.58f) / 0.12f).coerceIn(0f, 1f) else 0f
        val shine = if (p in 0.62f..0.82f) seg(p, 0.62f, 0.82f) else -1f
        val pop = 1f + 0.18f * (if (p in 0.56f..0.80f) 1f - abs((p - 0.62f) / 0.18f).coerceIn(0f, 1f) else 0f)
        RevealPose(lift, pop, flash, if (waiting) 0.12f else seg(p, 0f, 0.55f).coerceAtLeast(0.12f), if (waiting) 0f else ring.let { if (it > 0f) 1f - it else 0f },
            arc = false, turn = if (waiting) 180f else turn, shine = shine, ring = ring)
    }

    /** Proposal B "Glow burst": no flight - the silhouette lights up from inside, a ring bursts, the badge overshoots and settles. */
    private val glowBurst = { p: Float, waiting: Boolean, _: Float ->
        val sil = if (waiting) 1f else 1f - ease(seg(p, 0.25f, 0.50f))
        val flash = if (p in 0.40f..0.62f) 1f - abs((p - 0.48f) / 0.14f).coerceIn(0f, 1f) else 0f
        val ring = if (p in 0.45f..0.85f) seg(p, 0.45f, 0.85f).coerceAtLeast(0.01f) else 0f
        val over = if (p in 0.45f..0.85f) sin(seg(p, 0.45f, 0.85f) * PI.toFloat()) * (1f - seg(p, 0.45f, 0.85f) * 0.6f) else 0f
        val shine = if (p in 0.70f..0.92f) seg(p, 0.70f, 0.92f) else -1f
        RevealPose(0f, 1f + 0.16f * over, flash, if (waiting) 0.12f else seg(p, 0f, 0.5f).coerceAtLeast(0.12f), if (waiting) 0f else flash,
            arc = false, shine = shine, ring = ring, silhouette = sil)
    }

    @Test fun voltPoseIsTheOldReveal() {
        // The formulas StageSlot used inline before 682j, copied: the Volt reveal must not change by the refactor.
        for (waiting in listOf(false, true)) for (amount in listOf(0.8f, 1f, 1.3f)) for (i in 0..100) {
            val p = i / 100f
            val charge = seg(p, 0f, 0.30f)
            val flash = if (p < 0.14f) seg(p, 0f, 0.14f) else 1f - seg(p, 0.14f, 0.30f)
            val rise = ease(seg(p, 0.20f, 0.36f)); val hover = seg(p, 0.36f, 0.78f); val settle = ease(seg(p, 0.78f, 0.92f))
            val lockFlare = (1f - abs(seg(p, 0.88f, 1f) * 2f - 1f)).coerceIn(0f, 1f)
            val amp = amount.coerceIn(0.6f, 1.4f)
            val hop = abs(sin(hover * 2 * PI.toFloat())) * 7f * amp * (1f - hover * 0.4f)
            val lift = (rise * 30f * amp + hop) * (1f - settle)
            val q = voltRevealPose(p, waiting, amount)
            assertEquals(lift, q.lift, 1e-5f)
            assertEquals(1f + (0.2f * amp) * rise * (1f - settle), q.scale, 1e-5f)
            assertEquals(flash, q.flash, 1e-5f)
            assertEquals(if (waiting) 0.12f else if (p >= 1f) 1f else charge, q.capsuleCharge, 1e-5f)
            assertEquals(if (waiting) 0f else maxOf(if (p in 0.2f..0.4f) 1f - abs((p - 0.3f) / 0.1f) else 0f, lockFlare), q.capsuleFlare, 1e-5f)
            assertEquals(!waiting && lift > 2f, q.arc)
            assertEquals(0f, q.turn); assertEquals(-1f, q.shine); assertEquals(0f, q.ring); assertEquals(0f, q.silhouette)
        }
    }

    @Test fun storyboard() {
        val moments = listOf(0f, 0.15f, 0.30f, 0.45f, 0.55f, 0.62f, 0.72f, 0.85f, 1f)
        val rows = listOf<Pair<String, (Float, Boolean, Float) -> RevealPose>>(
            "NOW  Volt capsule (app)" to ::voltRevealPose, "A  Trophy flip (proposal)" to trophyFlip, "B  Glow burst (proposal)" to glowBurst
        )
        val badge = StageBadge(1, null, "First Steps", 10)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val style = achievementStyle()
            Column(Modifier.testTag("sheet").background(Color(0xFF0E0F14)).padding(8.dp)) {
                Row { Text("", Modifier.width(120.dp)); for (m in moments) Text("p ${"%.2f".format(m)}", Modifier.width(104.dp), color = Color.White, fontSize = 11.sp) }
                for ((name, pose) in rows) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(name, Modifier.width(120.dp), color = Color.White, fontSize = 11.sp)
                    for (m in moments) StageSlot(badge, m, waiting = false, size = 82.dp, style = style, pose = pose)
                }
            }
        }
        repeat(30) { androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)) }
        val root = compose.activity.window.decorView
        val full = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(full))
        val b = compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val bmp = android.graphics.Bitmap.createBitmap(full, b.left.toInt(), b.top.toInt(),
            b.width.toInt().coerceAtMost(full.width - b.left.toInt()), b.height.toInt().coerceAtMost(full.height - b.top.toInt()))
        val out = java.io.File("build/reports/tests/h12-reveal").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(out, "storyboard.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        // Something was drawn in every row (the sheet is not the empty background).
        val bg = android.graphics.Color.rgb(0x0E, 0x0F, 0x14)
        val lit = (0 until bmp.height step 3).count { y -> (0 until bmp.width step 3).any { x -> bmp.getPixel(x, y) != bg } }
        println("H12 storyboard ${bmp.width}x${bmp.height}, rows with ink: $lit")
        assertTrue("the storyboard drew something", lit > bmp.height / 6)
    }
}
