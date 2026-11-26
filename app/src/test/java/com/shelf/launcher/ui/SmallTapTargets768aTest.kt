package com.shelf.launcher.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 768a (P12, touch size >= 24 dp): the thin tap controls drawn smaller than 24 dp on purpose (the music players' seek bars,
 * 10 / 18 / 20 dp; the mini player's 18 dp keys) rely on Compose's minimum touch target: a pointerInput node takes a tap up
 * to half of ViewConfiguration.minimumTouchTargetSize (48 dp) round its centre when nothing else is directly under the finger
 * - even inside a panel with its own gesture handler (the mini player's playerGestures). Growing their layout boxes would
 * change the approved player designs (update 487 found the same for the popup keys), so this locks the safety net in instead:
 * it fails if a Compose update or an app-wide ViewConfiguration override takes it away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class SmallTapTargets768aTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private var bar = 0
    private var key = 0
    private var panel = 0

    /**
     * Like the mini player: a panel with its own tap handler holding a 10 dp seek bar (x 0..300 dp, y 40..50 dp) and, to its
     * right on the row above, an 18 dp key directly next to the bar's top edge (x 300..318 dp, y 22..40 dp).
     */
    private fun show() {
        compose.setContent {
            Column(Modifier.width(400.dp).pointerInput(Unit) { detectTapGestures { panel++ } }) {
                Row {
                    Spacer(Modifier.width(300.dp))
                    Column {
                        Spacer(Modifier.height(22.dp))
                        Box(Modifier.width(18.dp).height(18.dp).pointerInput(Unit) { detectTapGestures { key++ } })
                    }
                }
                Box(Modifier.width(300.dp).height(10.dp).pointerInput(Unit) { detectTapGestures { bar++ } })
                Spacer(Modifier.height(60.dp))
            }
        }
    }

    private fun tap(x: Dp, y: Dp) {
        compose.onRoot().performTouchInput { click(Offset(x.toPx(), y.toPx())) }
        compose.waitForIdle()
    }

    @Test fun aThinBarTakesATapJustOffItsEdge() {
        show()
        tap(150.dp, 45.dp); assertEquals("on the bar", 1, bar)
        tap(150.dp, 36.dp); assertEquals("4 dp above (the old 24 dp gate's room)", 2, bar)
        tap(150.dp, 54.dp); assertEquals("4 dp below", 3, bar)
        tap(150.dp, 30.dp); assertEquals("10 dp above, still inside 48 dp round its centre", 4, bar)
        assertEquals("the panel's own handler did not take them", 0, panel)
    }

    @Test fun farFromTheBarThePanelGetsIt() {
        show()
        tap(150.dp, 12.dp)
        assertEquals("33 dp from the bar's centre: not the bar's", 0, bar)
        assertEquals(1, panel)
    }

    @Test fun aControlDirectlyUnderTheFingerWins() {
        show()
        tap(309.dp, 31.dp)
        assertEquals("the key under the finger", 1, key)
        assertEquals("the bar next to it does not steal it", 0, bar)
    }

    @Test fun noAppWideOverrideShrinksTheTouchTarget() {
        val root = listOf("src/main/java/com/shelf/launcher", "app/src/main/java/com/shelf/launcher").map(::File).first { it.isDirectory }
        val offenders = root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "ThemeShaders.kt" }
            .filter { f -> f.readText().let { "LocalViewConfiguration provides" in it || Regex("""override val minimumTouchTargetSize""").containsMatchIn(it) } }
            .map { it.name }.toList()
        assertTrue("a ViewConfiguration override would take the 48 dp touch target from thin controls: $offenders", offenders.isEmpty())
    }
}
