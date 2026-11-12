package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.SourceType
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Batch 524 (owner: "three games should look like three games - not an endless wall of repeated copies with multiple
 * selected rows"): every style home that goes round (Endless on, the default) composed at the Flip 2's 640 x 480 dp with
 * 1-5 games, a row with about enough and a big library; walked forward past the last game and back past the first.
 * At each settled place no game's name is on the screen more often than once (the game you're on: twice - its card and
 * its name / info line). Names drawn without text semantics (a Canvas) aren't seen; the hardware check covers those.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h480dp-land-mdpi")
class EndlessOnceRoboTest(private val layout: HomeLayout, private val n: Int, private val scale: Float) {
    companion object {
        val NAMES = listOf("Crash Bandicoot", "Spyro the Dragon", "Tekken 3", "Gran Turismo", "Ape Escape", "Medievil",
            "Wipeout", "Vagrant Story", "Silent Hill", "Parappa", "Xenogears", "Legend of Dragoon")
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} n={1} x{2}")
        fun cases(): List<Array<Any>> {
            val out = mutableListOf<Array<Any>>()
            val layouts = HomeLayout.entries.filter { it.styleHome && endlessApplies(it) }
            for (l in layouts + HomeLayout.TILES) for (k in listOf(1, 2, 3, 4, 5, 12)) out += arrayOf<Any>(l, k, 1.0f)
            for (l in listOf(HomeLayout.NIGHT, HomeLayout.AIRY, HomeLayout.ARTLIB).filter { it in layouts }) out += arrayOf<Any>(l, 3, 1.3f)
            return out
        }
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)
    @After fun reset() { Features.endlessFill.value = false }

    private fun frames(k: Int) = repeat(k) {
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun shownNames(): Map<String, Int> {
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val screen = roots.firstOrNull()?.boundsInRoot ?: Rect.Zero
        val out = mutableMapOf<String, Int>()
        fun walk(s: SemanticsNode) {
            if (!s.layoutInfo.isPlaced) return
            val t = s.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            val b = s.boundsInRoot
            if (t != null && b.width > 1f && b.height > 1f && b.overlaps(screen)) {
                for (nm in NAMES) if (t.contains(nm)) out[nm] = (out[nm] ?: 0) + 1
            }
            if (s.config.isClearingSemantics) return
            s.children.forEach(::walk)
        }
        roots.forEach(::walk)
        return out
    }

    @Test fun eachGameOnceOneFocus() {
        RuntimeEnvironment.setFontScale(scale)
        Features.endlessFill.value = Features.endlessFill.default
        val games = NAMES.take(n).mapIndexed { i, nm -> Game(nm, "PS1", 1, SourceType.ROM, romPath = "/r/$i.chd") }
        var state by mutableStateOf(LauncherUiState(games = games, appearance = AppearanceSettings(homeLayout = layout, loopRow = true)))
        val actions = StyleActions(select = { state = state.copy(focusedIndex = it) }, open = {}, play = {}, category = {}, achievements = {},
            needPhysical = {}, needMedia = {})
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (layout == HomeLayout.TILES) TileHomeRow(games = state.visibleGames, art = emptyMap(), artOverrides = emptyMap(), focusedIndex = state.focusedIndex,
                raProgress = emptyMap(), favorites = emptySet(), cardArtType = state.appearance.cardArtFor(), cardSize = state.appearance.cardSize,
                dimmed = false, onFocus = { state = state.copy(focusedIndex = it) }, onOpen = {}, loop = true, loopTick = state.loopTick, loopStep = state.loopStep)
            else StyleHome(layout, state, emptyMap(), emptyList(), actions)
        }
        frames(120)
        val problems = mutableListOf<String>()
        fun check(where: String) {
            val seen = shownNames()
            val on = games.getOrNull(state.focusedIndex)?.name
            for ((nm, k) in seen) if (k > (if (nm == on) 2 else 1)) problems += "$where: '$nm' shown $k times (on '$on') $seen"
        }
        check("start")
        // forward past the last game, then back past the first: the view model's endless steps (loopTick / loopStep)
        var tick = 0
        fun step(d: Int) {
            tick++
            state = state.copy(focusedIndex = (state.focusedIndex + d).mod(n), loopTick = tick, loopStep = d)
            frames(30)
        }
        repeat(n + 2) { step(+1); check("fwd ${it + 1}") }
        repeat(2 * n + 3) { step(-1); check("back ${it + 1}") }
        // fast D-pad: steps a frame or two apart, then settled
        repeat(6) { tick++; state = state.copy(focusedIndex = (state.focusedIndex + 1).mod(n), loopTick = tick, loopStep = 1); frames(2) }
        frames(90); check("fast")
        // the list refreshed (a new list object, same games) and a game removed
        state = state.copy(games = games.toList()); frames(60); check("refresh")
        if (n > 1) { state = state.copy(games = games.drop(1), focusedIndex = 0); frames(90); check("removed") }
        assertTrue("$layout n=$n x$scale: ${problems.size} problem(s)\n" + problems.take(12).joinToString("\n"), problems.isEmpty())
    }
}
