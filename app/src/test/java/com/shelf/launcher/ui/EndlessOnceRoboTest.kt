package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
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
import com.shelf.launcher.data.cardArtFor
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

    /** Each game name's count among the texts wholly on the screen; [edge] gets the names cut by a screen edge. */
    private val where = mutableMapOf<String, MutableList<String>>()
    private fun shownNames(edge: MutableMap<String, Int>? = null): Map<String, Int> {
        where.clear()
        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val screen = roots.firstOrNull()?.boundsInRoot ?: Rect.Zero
        val out = mutableMapOf<String, Int>()
        fun walk(s: SemanticsNode) {
            if (!s.layoutInfo.isPlaced) return
            val t = s.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            val b = s.boundsInRoot
            if (t != null && b.width > 1f && b.height > 1f && b.overlaps(screen)) {
                // (the unclipped place: a text running past the screen's edge is a sliver at the edge)
                val whole = runCatching { s.layoutInfo.coordinates.let { c -> val r = c.findRootCoordinates().localBoundingBoxOf(c, false); r.left >= screen.left - 1f && r.right <= screen.right + 1f && r.top >= screen.top - 1f && r.bottom <= screen.bottom + 1f } }.getOrDefault(true)
                for (nm in NAMES) if (t.contains(nm)) {
                    if (whole) { out[nm] = (out[nm] ?: 0) + 1; where.getOrPut(nm) { mutableListOf() } += "'${t.take(24)}'@${b.left.toInt()},${b.top.toInt()}" }
                    else if (edge != null) edge[nm] = (edge[nm] ?: 0) + 1
                }
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
        var state by mutableStateOf(LauncherUiState(games = games, appearance = AppearanceSettings(homeLayout = layout, loopRow = false)))
        val actions = StyleActions(select = { state = state.copy(focusedIndex = it) }, open = {}, play = {}, category = {}, achievements = {},
            needPhysical = {}, needMedia = {})
        compose.mainClock.autoAdvance = false
        compose.setContent {
            if (layout == HomeLayout.TILES) TileHomeRow(games = state.visibleGames, art = emptyMap(), artOverrides = emptyMap(), focusedIndex = state.focusedIndex,
                raProgress = emptyMap(), favorites = emptySet(), cardArtType = state.appearance.cardArtFor(), cardSize = state.appearance.cardSize,
                dimmed = false, onFocus = { state = state.copy(focusedIndex = it) }, onOpen = {}, loop = state.appearance.loopRow, loopTick = state.loopTick, loopStep = state.loopStep)
            else StyleHome(layout, state, emptyMap(), emptyList(), actions)
        }
        // The layout's own texts per game with Endless OFF (a card's name and its shadow, the game you're on in a header):
        // going round may not add any - the copies are what Endless ON would add.
        state = state.copy(appearance = state.appearance.copy(loopRow = false))
        frames(120)
        var perGame = 1
        var perFocus = 1
        fun baseline() {
            val off = shownNames()
            val onName = state.visibleGames.getOrNull(state.focusedIndex)?.name
            perGame = maxOf(perGame, off.filterKeys { it != onName }.values.maxOrNull() ?: 1)
            perFocus = maxOf(perFocus, off[onName] ?: 1)
        }
        baseline()
        // (and a step in and back, Endless off: what a move itself shows - a page turning, a name sliding in)
        if (n > 1) {
            state = state.copy(focusedIndex = 1, loopTick = 1, loopStep = 1); frames(30); baseline(); frames(30)
            state = state.copy(focusedIndex = 0, loopTick = 2, loopStep = -1); frames(30); baseline(); frames(30)
        }
        state = state.copy(appearance = state.appearance.copy(loopRow = true))
        frames(120)
        val problems = mutableListOf<String>()
        val slivers = mutableListOf<String>()
        fun check(where: String) {
            val edge = mutableMapOf<String, Int>()
            val seen = shownNames(edge)
            val on = state.visibleGames.getOrNull(state.focusedIndex)?.name
            for ((nm, k) in seen) if (k > (if (nm == on) perFocus else perGame)) problems += "$where: '$nm' shown $k times (on '$on'; Endless off: $perGame, on: $perFocus) $seen ${this.where[nm]}"
            for ((nm, k) in edge) if ((seen[nm] ?: 0) > 0 || k > 1) slivers += "$where: '$nm' whole ${seen[nm] ?: 0} + $k at an edge"
        }
        check("start")
        // forward past the last game, then back past the first: the view model's endless steps (loopTick / loopStep)
        var tick = 2
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
        if (slivers.isNotEmpty()) println("EDGE-SLIVERS $layout n=$n x$scale:\n" + slivers.take(6).joinToString("\n"))
        assertTrue("$layout n=$n x$scale: ${problems.size} problem(s)\n" + problems.take(12).joinToString("\n"), problems.isEmpty())
    }
}
