package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.SourceType
import com.shelf.launcher.data.cardArtFor
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Sprint 525 (owner: "Fill ON = repeated games allowed, but one highlight only"): every style home that goes round, and
 * Tiles, with Endless › Fill the row ON and OFF, 1 / 2 / 3 / 5 / 12 games: walked forward past the last game, back past the
 * first, a fast D-pad burst, a list refresh and a game removed. At every settled place at most ONE game wears the
 * layout's selection highlight ([QaClip.Lit]: styleFocus, focusedCard, the lit Night / Airy / Art library row) - copies
 * of the game you're on look unselected. A layout whose highlight isn't one of those marks shows 0 and is listed as
 * "LIT-UNMARKED" (covered by the code audit and the hardware check, not by this test).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h480dp-land-mdpi")
class EndlessFillLitRoboTest(private val layout: HomeLayout, private val n: Int, private val fill: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} n={1} fill={2}")
        fun cases(): List<Array<Any>> {
            val out = mutableListOf<Array<Any>>()
            val layouts = HomeLayout.entries.filter { it.styleHome && endlessApplies(it) } + HomeLayout.TILES
            for (l in layouts) for (k in listOf(1, 2, 3, 5, 12)) for (f in listOf(true, false)) out += arrayOf<Any>(l, k, f)
            return out
        }
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)
    @After fun reset() { Features.endlessFill.value = false; QaClip.litMarks = false }

    private fun frames(k: Int) = repeat(k) {
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    /** The placed nodes marked lit whose box is on the screen. */
    private fun lit(): List<Rect> {
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val screen = roots.firstOrNull()?.boundsInRoot ?: Rect.Zero
        val out = mutableListOf<Rect>()
        fun walk(s: SemanticsNode) {
            if (!s.layoutInfo.isPlaced) return
            val b = s.boundsInRoot
            if (s.config.getOrNull(QaClip.Lit) == true && b.width > 1f && b.height > 1f && b.overlaps(screen)) out += b
            s.children.forEach(::walk)
        }
        roots.forEach(::walk)
        // (one highlight can mark two nested boxes - its card and its frame: those overlap, copies elsewhere don't)
        val groups = mutableListOf<Rect>()
        for (r in out) if (groups.none { it.overlaps(r) }) groups += r
        return groups
    }

    @Test fun oneLitCopy() {
        QaClip.litMarks = true
        Features.endlessFill.value = fill
        val names = EndlessOnceRoboTest.NAMES
        val games = names.take(n).mapIndexed { i, nm -> Game(nm, "PS1", 1, SourceType.ROM, romPath = "/r/$i.chd") }
        var state by mutableStateOf(LauncherUiState(games = games, appearance = AppearanceSettings(homeLayout = layout, loopRow = true)))
        val actions = StyleActions(select = { state = state.copy(focusedIndex = it) }, open = {}, play = {}, category = {}, achievements = {},
            needPhysical = {}, needMedia = {})
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (layout == HomeLayout.TILES) TileHomeRow(games = state.visibleGames, art = emptyMap(), artOverrides = emptyMap(), focusedIndex = state.focusedIndex,
                raProgress = emptyMap(), favorites = emptySet(), cardArtType = state.appearance.cardArtFor(), cardSize = state.appearance.cardSize,
                dimmed = false, onFocus = { state = state.copy(focusedIndex = it) }, onOpen = {}, loop = state.appearance.loopRow, loopTick = state.loopTick, loopStep = state.loopStep)
            else StyleHome(layout, state, emptyMap(), emptyList(), actions)
        }
        frames(120)
        val problems = mutableListOf<String>()
        var maxLit = 0
        fun check(where: String) {
            val l = lit()
            maxLit = maxOf(maxLit, l.size)
            if (l.size > 1) problems += "$where: ${l.size} lit (on #${state.focusedIndex}) at " + l.joinToString { "${it.left.toInt()},${it.top.toInt()}" }
        }
        check("start")
        var tick = 0
        fun step(d: Int, settle: Int = 40) {
            tick++
            state = state.copy(focusedIndex = (state.focusedIndex + d).mod(n), loopTick = tick, loopStep = d)
            frames(settle)
        }
        repeat(n + 2) { step(+1); check("fwd ${it + 1}") }
        repeat(2 * n + 3) { step(-1); check("back ${it + 1}") }
        repeat(8) { step(if (it % 3 == 2) -1 else 1, settle = 1) }
        frames(90); check("fast")
        state = state.copy(games = games.toList()); frames(60); check("refresh")
        if (n > 1) { state = state.copy(games = games.drop(1), focusedIndex = 0); frames(90); check("removed") }
        if (maxLit == 0) println("LIT-UNMARKED $layout n=$n fill=$fill")
        assertTrue("$layout n=$n fill=$fill: ${problems.size} problem(s)\n" + problems.take(12).joinToString("\n"), problems.isEmpty())
    }
}
