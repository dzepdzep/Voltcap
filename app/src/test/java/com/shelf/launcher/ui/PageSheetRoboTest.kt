package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.getOrNull
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
import org.junit.Assume.assumeTrue
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
 * 682t: a review sheet, not a check - the game window's pages drawn on the PC (native graphics, the KONKR's 640 x 427 dp) for a
 * look between handheld sessions: Info, then R1 page by page, then A on the last page's first row. Runs only when asked:
 *   VOLTCAP_SHEET="SPOT TV" ./gradlew :app:testDebugUnitTest --tests '*PageSheetRoboTest*'
 * PNGs in app/build/pagesheet/<layout>_<x10|x13>_<step>.png. EMULATED evidence for design work - never hardware evidence.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
open class PageSheetRoboTest(private val layout: HomeLayout, private val big: Boolean) {
    /** 682ag: the file prefix of a subclass on another screen (PageSheetRpcRoboTest: "rpc_"). */
    protected open val tag = ""
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1}")
        fun cases(): List<Array<Any>> {
            val want = System.getenv("VOLTCAP_SHEET").orEmpty().split(Regex("[ ,]+")).filter { it.isNotBlank() }
            val ls = HomeLayout.values().filter { it.name in want }.ifEmpty { listOf(HomeLayout.SPOT) }
            return ls.flatMap { l -> listOf(arrayOf<Any>(l, false), arrayOf<Any>(l, true)) }
        }
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun key(code: Int) = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.let { d ->
        d.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code)); frames(1)
        d.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code)); frames(45)
    }

    private fun shot(step: String) = runCatching {
        val v = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView
        val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        val dir = java.io.File("build/pagesheet").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "$tag${layout}_${if (big) "x13" else "x10"}_$step.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }.onFailure { println("PAGESHEET capture failed: $it") }

    private fun words(n: androidx.compose.ui.semantics.SemanticsNode) = (n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
        ?.joinToString(" ") { it.text }.orEmpty() + " " + n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)
        ?.joinToString(" ").orEmpty()).lowercase()

    private fun open(page: Set<String>) {
        val click = androidx.compose.ui.semantics.SemanticsActions.OnClick
        val m = androidx.compose.ui.test.SemanticsMatcher("page") { n -> n.config.contains(click) &&
            words(n).split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.let { w -> w.isNotEmpty() && w.all { it in page } } }
        var found = compose.onAllNodes(m).fetchSemanticsNodes()
        if (found.isEmpty()) {
            val more = androidx.compose.ui.test.SemanticsMatcher("More") { n -> n.config.contains(click) && words(n).trim().let { w -> w == "•••" || w == "more" } }
            val ms = compose.onAllNodes(more).fetchSemanticsNodes()
            if (ms.isNotEmpty()) { compose.onAllNodes(more)[ms.size - 1].performSemanticsAction(click); frames(20) }
            found = compose.onAllNodes(m).fetchSemanticsNodes()
        }
        if (found.isEmpty()) { println("PAGESHEET $layout: no control for $page"); return }
        compose.onAllNodes(m)[found.size - 1].performSemanticsAction(click); frames(60)
    }

    private fun openNoSettle(page: Set<String>) {
        val click = androidx.compose.ui.semantics.SemanticsActions.OnClick
        val m = androidx.compose.ui.test.SemanticsMatcher("page") { n -> n.config.contains(click) &&
            words(n).split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.let { w -> w.isNotEmpty() && w.all { it in page } } }
        val found = compose.onAllNodes(m).fetchSemanticsNodes()
        if (found.isEmpty()) { println("PAGESHEET $layout: no control for $page"); return }
        compose.onAllNodes(m)[found.size - 1].performSemanticsAction(click)
    }

    private val gen = androidx.compose.runtime.mutableIntStateOf(0)

    @Test fun drawThePages() {
        assumeTrue("review sheet: only when VOLTCAP_SHEET is set", System.getenv("VOLTCAP_SHEET") != null)
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/s.iso")
        val list = (1..12).map { i ->
            RaAchievementDetail(i, if (i == 1) "Defeat the first colossus" else "Achievement number $i of the set", "Synthetic fixture achievement $i, a description that runs to a second line.",
                5 + i, null, null, if (i % 3 == 0) null else "2026-11-01 10:00:00", null, unlockPercent = 10f, displayOrder = i)
        }
        val meta = com.shelf.launcher.data.GameMetadata("Shadow of the Colossus", "A young man rides into a forbidden land to bring a girl back to life. " +
            "Sixteen colossi stand between him and the bargain - each a moving landscape to climb.", 2005, null, null, listOf("Action", "Adventure"),
            "Team Ico", null, null, null)
        val state = LauncherUiState().copy(raAchievementDetails = mapOf(g.stableKey to list), metadata = mapOf(g.stableKey to meta),
            raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = 12, NumAchieved = list.count { it.isUnlocked })))
        val before = com.shelf.launcher.data.Features.winScenes.state.value
        com.shelf.launcher.data.Features.winScenes.state.value = true
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        CoverReadingProposal.on.value = System.getenv("VOLTCAP_COVER_READING") != "0" // (682aj: on unless asked for the old look)
        val oldRect = TvScreen.rect; val oldId = TvScreen.displayId
        val oldFront = TvScreen.front
        if (layout == HomeLayout.TV) { TvScreen.rect = androidx.compose.ui.geometry.Rect(97f, 117f, 314f, 265f); TvScreen.displayId = -1
            // the room's figure as the home places it (TvRoomArt, at this screen's scale)
            val k = 217f / TvRoomArt.screen.width; val sc = TvRoomArt.screen; val f = TvRoomArt.front
            if (System.getenv("VOLTCAP_SHEET_NOFRONT") == null) TvScreen.front = androidx.compose.ui.geometry.Rect((f.left - sc.left) * k, (f.top - sc.top) * k, (f.right - sc.left) * k, (f.bottom - sc.top) * k) }
        try {
            compose.mainClock.autoAdvance = false
            compose.setContent { androidx.compose.runtime.key(gen.intValue) { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(layout, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {},
                    options = listOf(WinOption("Add to favorites") {}, WinOption("Game settings") {},
                        WinOption("Hide from library", "Takes it off your library", destructive = true) {})) } } }
            frames(180); shot("1_info")
            // (682ag, owner review 23 Nov - page-change audit: VOLTCAP_SHEET_FRAMES=1 draws the Info -> Details change every 32 ms
            // (2 frames), t_00 .. t_17, so a new heading over the old page's still-visible content shows frame by frame.)
            if (System.getenv("VOLTCAP_SHEET_FRAMES") != null) {
                openNoSettle(setOf("details", "summary", "info", "turn", "over", "the", "story", "game", "sleeve", "notes", "liner", "plot", "card", "text"))
                for (i in 0 until 18) { shot("t_%02d".format(i)); frames(2) }
                return
            }
            // Each page through its own control from a freshly opened window (as a touch would; behind More / ••• where the
            // window keeps it) - under Robolectric the window's L1 / R1 key path does not turn its pages.
            fun fresh() { gen.intValue++; frames(160) }
            open(setOf("details", "summary", "info", "turn", "over", "the", "story", "game", "sleeve", "notes", "liner", "plot", "card", "text")); shot("2_details")
            fresh(); open(setOf("achievements", "trophies", "credits")); shot("3_achievements")
            fresh(); open(setOf("options", "config", "backstage")); shot("4_options")
            // the destructive row armed: A on it twice is never pressed - one click arms it (NativeConfirm)
            val hide = androidx.compose.ui.test.SemanticsMatcher("hide") { n -> n.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) && "hide from library" in words(n) }
            if (compose.onAllNodes(hide).fetchSemanticsNodes().isNotEmpty()) {
                compose.onAllNodes(hide)[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); frames(30)
            }
            shot("5_options_armed")
        } finally {
            TestHooks.scale.value = null; com.shelf.launcher.data.Features.winScenes.state.value = before
            CoverReadingProposal.on.value = true
            TvScreen.rect = oldRect; TvScreen.displayId = oldId; TvScreen.front = oldFront
        }
    }
}

/** 682ag: the same sheet on the RP Classic's screen (620 x 540 dp, xhdpi - the hardware's own size) - files rpc_<layout>_... */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w620dp-h540dp-land-xhdpi")
class PageSheetRpcRoboTest(layout: HomeLayout, big: Boolean) : PageSheetRoboTest(layout, big) {
    override val tag = "rpc_"
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1}")
        fun cases(): List<Array<Any>> = PageSheetRoboTest.cases()
    }
}
