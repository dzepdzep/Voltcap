package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.math.abs

/**
 * 529j (owner video VID20261113180355, compact screens: "the incoming categories suddenly spawn at the edge"): a row of
 * category chips (WholeChipRow - Library, Console hub, Pocket Quest, Icon bar, Settings tabs...) stepped one category at a
 * time across a narrow row, every frame recorded (each chip's place and how much of it shows, ChipGlide.probe).
 *  - When the row has to move, no chip goes from hidden to (nearly) whole in one frame, the move takes several frames, and
 *    no chip that shows on two frames jumps between them.
 *  - At rest only whole chips show (update 294) and the chip you're on is one of them.
 *  - The legacy path (the instant re-layout before 529j, kept for the QA A/B) does pop - so the check can see a pop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi") // (the same screen as WinFocusRoboTest: a screen-size cache set here at another size upset it)
class ChipStrip529RoboTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private val widths = listOf(52, 74, 60, 88, 46, 70, 64, 92, 58, 66)
    private val rowW = 300
    private var xs = FloatArray(0)
    private var al = FloatArray(0)

    @After fun reset() { ChipGlide.probe = null; ChipGlide.legacy = false }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private class Step(val target: Int, val xs: List<FloatArray>, val al: List<FloatArray>)

    private fun walk(): List<Step> {
        ChipGlide.probe = { x, a -> xs = x.copyOf(); al = a.copyOf() }
        var sel by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(rowW.dp, 40.dp).background(Color.Black)) {
                WholeChipRow(widths.size, sel, Modifier.fillMaxWidth(), spacing = 6.dp, hintColor = Color.Transparent) { i ->
                    Box(Modifier.size(widths[i].dp, 24.dp).background(Color.White))
                }
            }
        }
        repeat(10) { frame() }
        val out = mutableListOf<Step>()
        for (target in (1 until widths.size) + (widths.size - 2 downTo 0)) {
            sel = target
            val x = mutableListOf(xs.copyOf()); val a = mutableListOf(al.copyOf())
            repeat(30) { frame(); x += xs.copyOf(); a += al.copyOf() }
            out += Step(target, x, a)
        }
        return out
    }

    /** The largest jump from hidden to shown in one frame, over every chip and frame of a step. */
    private fun biggestPop(s: Step): Float = (1 until s.al.size).maxOf { f -> s.al[f].indices.maxOf { i -> s.al[f][i] - s.al[f - 1][i] } }

    @Test fun incomingCategoriesGlideInsteadOfAppearingAtTheEdge() {
        val steps = walk()
        var moves = 0
        for (s in steps) {
            val busy = (1 until s.xs.size).count { f -> s.xs[f].indices.any { abs(s.xs[f][it] - s.xs[f - 1][it]) > 0.01f } }
            if (busy == 0) continue // the chip was already whole: nothing moves (as before)
            moves++
            assertTrue("step to ${s.target}: a chip came in by ${biggestPop(s)} in one frame (pop-in)", biggestPop(s) <= 0.35f)
            assertTrue("step to ${s.target}: moved in $busy frames only", busy >= 8)
            for (f in 1 until s.xs.size) for (i in widths.indices) {
                if (s.al[f - 1][i] > 0f && s.al[f][i] > 0f) {
                    val d = s.xs[f][i] - s.xs[f - 1][i]
                    assertTrue("step to ${s.target} chip $i frame $f jumped $d px", abs(d) <= 24f)
                }
            }
            // At rest: whole chips only, the chip you're on among them, inside the row.
            val a = s.al.last(); val x = s.xs.last()
            for (i in widths.indices) assertTrue("step to ${s.target}: chip $i rests half shown (${a[i]})", a[i] == 0f || a[i] == 1f)
            assertEquals("step to ${s.target}: the chip you're on shows whole", 1f, a[s.target], 0f)
            assertTrue("step to ${s.target}: the chip you're on is inside the row", x[s.target] >= 0f && x[s.target] + widths[s.target] <= rowW)
        }
        assertTrue("the row had to move at least 4 times across 10 chips ($moves)", moves >= 4)
    }

    @Test fun theOldInstantRowPopsAndTheCheckSeesIt() {
        ChipGlide.legacy = true
        val pops = walk().map { biggestPop(it) }
        assertTrue("the legacy row should show a chip whole in one frame somewhere: ${pops.max()}", pops.max() >= 0.99f)
    }
}
