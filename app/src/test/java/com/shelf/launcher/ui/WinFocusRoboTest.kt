package com.shelf.launcher.ui

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.SemanticsActions
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

/**
 * Lets Robolectric start Compose's test activity (ComponentActivity) without ui-test-manifest's manifest entry: the unit test
 * manifest isn't merged with test dependencies, so the activity is registered with Robolectric's package manager instead.
 */
internal class RegisterComposeTestActivity : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val app = RuntimeEnvironment.getApplication()
            val cn = ComponentName(app.packageName, ComponentActivity::class.java.name)
            val pm = shadowOf(app.packageManager)
            pm.addActivityIfNotPresent(cn)
            pm.addIntentFilterForActivity(cn, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
            base.evaluate()
        }
    }
}

/**
 * Popup native pages (12 Nov, Phase 2): the REAL game window of each native-page layout composed on the JVM (Robolectric +
 * Compose UI test) at the KONKR's 640 × 427 dp, driven with key events through the window's own dialog (the path a
 * controller's keys take: the dialog's key dispatch, the window's key handlers, Compose focus, the back dispatcher).
 * Frames are stepped one by one (16 ms); the focus is checked on every frame of a page change, not only once it settles.
 * Not hardware: Android's real input timing, the GPU and the handheld's display are not in this.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class WinFocusRoboTest(private val layout: HomeLayout) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun layouts(): List<Array<Any>> = listOf(HomeLayout.STORAGE, HomeLayout.NIGHT, HomeLayout.TILES, HomeLayout.BLADES).map { arrayOf<Any>(it) }
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private var plays = 0
    private var closes = 0
    private var optionHits = 0
    private val game = Game("Probe Quest", "SNES", 1, SourceType.ROM, romPath = "/r/p.sfc")

    private fun open() {
        // (The window has endless motion - marquees, glows: an auto-advancing clock never goes idle. Frames are stepped here.)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GameWindow(
                layout, game, LauncherUiState(), emptyMap(), onClose = { closes++ }, onPlay = { plays++ }, onFullDetails = {}, onNeedMedia = {},
                options = listOf(WinOption("Probe option") { optionHits++ }, WinOption("Second probe option") {})
            )
        }
        frames(150)   // opened, Play asked for (update 122's asks run up to ~1.5 s)
    }

    /** One 16 ms frame: Compose's clock and the main looper (SystemClock, Handler posts) together. */
    private fun frames(n: Int, each: () -> Unit = {}) = repeat(n) {
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        each()
    }

    private fun dialog() = ShadowDialog.getLatestDialog() ?: error("the window's dialog is not showing")

    /** A key pressed and released through the dialog, as a controller's key arrives. */
    private fun key(code: Int) {
        val d = dialog()
        d.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        frames(1)
        d.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun label(n: SemanticsNode): String {
        val c = n.config
        val t = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
        val d = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: ""
        return (t + " " + d).trim()
    }

    private fun focused(): List<String> = compose.onAllNodes(isFocused()).fetchSemanticsNodes(atLeastOneRootRequired = false).map(::label)

    private fun tree(): String = runCatching { compose.onAllNodes(isRoot()).let { r -> (0 until r.fetchSemanticsNodes().size).joinToString("\n") { r[it].printToString(80) } } }.getOrDefault("?")

    private fun words(l: String) = l.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /** A control whose label is [text] (case and the icon glyphs ignored: the layouts' looks write "OPTIONS", "▶, PLAY"). */
    private fun clickable(text: String): SemanticsMatcher = SemanticsMatcher("control '$text'") { n ->
        n.config.contains(SemanticsActions.OnClick) && words(label(n)) == words(text)
    }

    private fun click(text: String) {
        val nodes = compose.onAllNodes(clickable(text)).fetchSemanticsNodes()
        assertTrue("$layout: no '$text' control\n${tree()}", nodes.isNotEmpty())
        // (the window's own action, not a tab of the same name: the last one drawn)
        compose.onAllNodes(clickable(text))[nodes.size - 1].performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Options: on the window, or behind More (the compact windows, update 451). */
    private fun openOptions() {
        if (compose.onAllNodes(clickable("Options")).fetchSemanticsNodes().isEmpty()) { click("More"); frames(12) }
        click("Options")
    }

    private fun onOptionsButton(f: List<String>) = f.size == 1 && words(f[0]) == listOf("options")

    private fun shows(text: String): Boolean = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** The Play control's label in this layout (look.play: "Play", "Start", "Load"...): the one focused right after opening. */
    private fun playLabel(): String {
        val f = focused()
        assertEquals("$layout: one control lit after opening, found $f\n${tree()}", 1, f.size)
        return f[0]
    }

    @Test fun opensOnPlayAndPlayWorksAfterTheGuard() {
        open()
        val play = playLabel()
        assertTrue("$layout: opened on '$play'", play.isNotBlank())
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        frames(2)
        assertEquals("$layout: A on Play after the open guard starts the game", 1, plays)
    }

    @Test fun backFromOptionsLandsOnTheOpenerOnEveryFrame() {
        open()
        val play = playLabel()
        openOptions()
        frames(30)                                   // the page change (NATIVE_PAGE_MS 280) has run
        assertTrue("$layout: Options page shows the game's options\n${tree()}", shows("Probe option"))
        val lit = mutableListOf<List<String>>()
        key(KeyEvent.KEYCODE_BACK)
        frames(30) { lit += focused() }
        assertFalse("$layout: Play was lit during B's page change: $lit", lit.any { play in it })
        assertTrue("$layout: frames with nothing lit after B: ${lit.count { it.isEmpty() }} of ${lit.size}: $lit", lit.count { it.isEmpty() } <= 2)
        assertTrue("$layout: B settles on the control that opened the page, lit ${focused()}\n${tree()}", onOptionsButton(focused()))
        assertEquals(0, plays); assertEquals("$layout: B on a page goes back to the main page, not out", 0, closes)
    }

    @Test fun rapidBThenANeverStartsTheGame() {
        open()
        openOptions()
        frames(30)
        key(KeyEvent.KEYCODE_BACK)
        frames(1)
        key(KeyEvent.KEYCODE_DPAD_CENTER)            // A one frame after B
        frames(30)
        assertEquals("$layout: a quick A after B started the game", 0, plays)
        assertEquals(0, closes)
    }

    /** One-press rule (R23): an A ~100 ms after B is the opener's - it opens that page again, never ignored, never Play. */
    @Test fun anASoonAfterBActsOnTheOpener() {
        open()
        openOptions()
        frames(30)
        key(KeyEvent.KEYCODE_BACK)
        frames(6)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        frames(30)
        assertEquals("$layout: an A after B started the game", 0, plays)
        assertTrue("$layout: an A 100 ms after B was ignored (Options not reopened), lit ${focused()}\n${tree()}", shows("Probe option"))
    }

    @Test fun bInTheMiddleOfAPageChange() {
        open()
        val play = playLabel()
        openOptions()
        frames(4)                                    // mid page change (~64 of 280 ms)
        key(KeyEvent.KEYCODE_BACK)
        val lit = mutableListOf<List<String>>()
        frames(40) { lit += focused() }
        assertFalse("$layout: Play lit after an interrupted page change: $lit", lit.any { play in it })
        assertTrue("$layout: interrupted change settles on the opener, lit ${focused()}\n${tree()}", onOptionsButton(focused()))
        assertEquals(0, plays); assertEquals(0, closes)
        assertFalse("$layout: the left page is gone", shows("Second probe option"))
    }

    @Test fun bumpersWalkThePagesAndFocusStaysOnAVisibleControl() {
        open()
        val play = playLabel()
        openOptions()
        frames(30)
        val lit = mutableListOf<List<String>>()
        // R1 / L1 quickly, twice each, then settle: never two lit controls, never Play on a page that isn't Details,
        // the game never starts, the window never closes.
        for (code in listOf(KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L1)) {
            key(code); frames(3) { lit += focused() }
        }
        frames(40) { lit += focused() }
        assertTrue("$layout: two controls lit at once: ${lit.filter { it.size > 1 }}", lit.none { it.size > 1 })
        assertEquals(0, plays); assertEquals(0, closes)
        assertEquals("$layout: settled with one control lit\n${tree()}", 1, focused().size)
        // R1 R1 L1 L1 from Options is back on Options
        assertTrue("$layout: back on the Options page after R1 R1 L1 L1\n${tree()}", shows("Probe option"))
        key(KeyEvent.KEYCODE_BACK); frames(30)
        assertTrue("$layout: B after the bumpers lands on the opener, not '$play': ${focused()}", onOptionsButton(focused()))
    }
}
