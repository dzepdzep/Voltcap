package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
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
 * 682k (owner 18 Nov: "fix the cramped TV Room teletext confirmation and the Record Crate Cancel label touching the edge. All
 * text, controls and confirmation messages must remain completely visible at x1.0 and x1.3"). With real text measurement
 * (native graphics - NativeConfirm681Test runs the D-pad walk on legacy graphics, whose text sizes are not real): the armed
 * removing row's confirmation, both of its halves, whole and clear of the edges (the crate's row; the TV set's screen, where it
 * is the page's status line), the row's own name whole, and every Fastext key still on the set. The row is armed by a tap.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class ConfirmRoom682kRoboTest(private val layout: HomeLayout, private val big: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1}")
        fun cases(): List<Array<Any>> = listOf(HomeLayout.CRATE, HomeLayout.TV).flatMap { l -> listOf(false, true).map { arrayOf<Any>(l, it) } }
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)
    private var hides = 0

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
    private fun words(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() + " " +
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()).lowercase().trim()
    private fun node(t: String) = compose.onAllNodes(hasText(t, substring = true, ignoreCase = true), useUnmergedTree = true)
        .fetchSemanticsNodes(atLeastOneRootRequired = false).maxByOrNull { it.boundsInRoot.height }

    @Test fun armedConfirmationIsWholeAndClear() {
        val before = com.shelf.launcher.data.Features.winScenes.state.value
        com.shelf.launcher.data.Features.winScenes.state.value = true
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        val oldRect = TvScreen.rect; val oldId = TvScreen.displayId
        if (layout == HomeLayout.TV) { TvScreen.rect = Rect(97f, 117f, 314f, 265f); TvScreen.displayId = -1 }
        val sx = if (big) "x1.3" else "x1.0"
        try {
            compose.mainClock.autoAdvance = false
            val g = Game("Probe Quest", "SNES", 1, SourceType.ROM, romPath = "/r/p.sfc")
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(layout, g, LauncherUiState(), emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {},
                    options = listOf(WinOption("Add to favorites") {}) + (1..7).map { WinOption("Probe setting $it", "A line about it") {} } +
                        WinOption("Hide from library", "Takes it off your library - nothing is deleted", destructive = true) { hides++ })
            } }
            frames(180)
            fun dump(tag: String) = compose.onAllNodes(SemanticsMatcher("c") { it.config.contains(SemanticsActions.OnClick) }).fetchSemanticsNodes()
                .forEach { println("ROOM $layout $big $tag [${words(it)}] ${it.boundsInRoot} ${it.size}") }
            // To Options as a person does with touch: the crate's Options control; the set's Info key, then its OPTIONS Fastext key.
            if (layout == HomeLayout.TV) {
                val info = SemanticsMatcher("Info") { n -> n.config.contains(SemanticsActions.OnClick) && words(n) == "info" }
                compose.onAllNodes(info)[0].performSemanticsAction(SemanticsActions.OnClick); frames(60)
                val key = SemanticsMatcher("Fastext") { n -> words(n) == "options" && n.boundsInRoot.top > 0f && n.config.getOrNull(SemanticsProperties.Text) != null }
                val keys = compose.onAllNodes(key).fetchSemanticsNodes()
                assertTrue("no Fastext key for the options", keys.isNotEmpty())
                compose.onAllNodes(key)[keys.indices.maxBy { keys[it].boundsInRoot.top }].performTouchInput { click() }
            } else {
                val opt = SemanticsMatcher("Options") { n -> n.config.contains(SemanticsActions.OnClick) && words(n).let { it == "options" || it == "game options" || it == "pressing notes" } }
                if (compose.onAllNodes(opt).fetchSemanticsNodes().isEmpty()) {
                    val more = SemanticsMatcher("More") { n -> n.config.contains(SemanticsActions.OnClick) && words(n) == "more" }
                    if (compose.onAllNodes(more).fetchSemanticsNodes().isNotEmpty()) { compose.onAllNodes(more)[0].performSemanticsAction(SemanticsActions.OnClick); frames(20) }
                }
                val os = compose.onAllNodes(opt).fetchSemanticsNodes()
                if (os.isEmpty()) dump("more")
                assertTrue("$layout: no Options control", os.isNotEmpty())
                compose.onAllNodes(opt)[os.size - 1].performSemanticsAction(SemanticsActions.OnClick)
            }
            frames(90)
            // Tap the removing row (scrolled to, as a finger does): it arms - the confirmation shows, nothing is removed.
            val row = SemanticsMatcher("removing row") { n -> n.config.contains(SemanticsActions.OnClick) && "hide from library" in words(n) }
            assertTrue("$layout $sx: the Options page shows no removing row", compose.onAllNodes(row, useUnmergedTree = false).fetchSemanticsNodes().isNotEmpty())
            // (the finger scrolls the list to it: the list's own scroll, a row at a time)
            run { var k = 0
                val list = SemanticsMatcher("list") { it.config.contains(SemanticsActions.ScrollBy) }
                while (k++ < 12 && compose.onAllNodes(row).fetchSemanticsNodes()[0].let { it.boundsInRoot.height < it.size.height - 1.5f }) {
                    val ls = compose.onAllNodes(list).fetchSemanticsNodes()
                    if (ls.isEmpty()) break
                    compose.onAllNodes(list)[ls.indices.maxBy { ls[it].size.height }].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 40f) }
                    frames(10) } }
            frames(20)
            compose.onAllNodes(row)[0].performTouchInput { click() }
            frames(60)
            assertEquals("$layout $sx: one tap must not remove", 0, hides)
            val r = compose.onAllNodes(row).fetchSemanticsNodes()[0]
            // (the TV room shows it on the panel under the set - Play, Back - across its width)
            val frame = if (layout == HomeLayout.TV) TvScreen.rect!!.let { Rect(it.left - 6f, it.bottom + 8f, it.right + 6f, 427f) } else r.boundsInRoot
            for (part in listOf("again to confirm", "to cancel")) {
                val t = node(part)
                assertTrue("$layout $sx: the confirmation has no \"$part\"", t != null)
                val b = t!!.boundsInRoot
                assertTrue("$layout $sx: \"$part\" is cut ($b of ${t.size})", b.height >= t.size.height - 1.5f && b.width >= t.size.width - 1.5f)
                assertTrue("$layout $sx: \"$part\" touches the edge (text $b, frame $frame)",
                    b.left >= frame.left + 3f && b.right <= frame.right - 3f && b.top >= frame.top && b.bottom <= frame.bottom)
            }
            val name = node("Hide from library")!!
            assertTrue("$layout $sx: the armed row's name is cut (${name.boundsInRoot} of ${name.size})",
                name.boundsInRoot.height >= name.size.height - 1.5f && name.boundsInRoot.width >= name.size.width - 1.5f)
            val sub = node("nothing is deleted")!!
            assertTrue("$layout $sx: the armed row's line is cut (${sub.boundsInRoot} of ${sub.size})",
                sub.boundsInRoot.height >= sub.size.height - 1.5f && sub.boundsInRoot.width >= sub.size.width - 1.5f)
            if (layout == HomeLayout.TV) for (k in listOf("picture", "info", "trophies", "options")) {
                val n = compose.onAllNodes(SemanticsMatcher(k) { words(it) == k && it.config.getOrNull(SemanticsProperties.Text) != null && it.boundsInRoot.top > TvScreen.rect!!.top })
                    .fetchSemanticsNodes().maxByOrNull { it.boundsInRoot.top }
                if (n != null) assertTrue("TV $sx: the Fastext key $k is cut while armed (${n.boundsInRoot} of ${n.size})",
                    n.boundsInRoot.height >= n.size.height - 1.5f)
            }
            // A second tap removes it, once.
            compose.onAllNodes(row)[0].performTouchInput { click() }; frames(20)
            assertEquals("$layout $sx: the second tap removes it once", 1, hides)
        } finally {
            TestHooks.scale.value = null; TvScreen.rect = oldRect; TvScreen.displayId = oldId
            com.shelf.launcher.data.Features.winScenes.state.value = before
        }
    }
}
