package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.RaAchievementDetail
import com.shelf.launcher.data.RaGameProgress
import com.shelf.launcher.data.SourceType
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
 * 529r (owner, 14 Nov - P0: "the first achievement row appearing as a thin strip is unacceptable"): on every layout's
 * Achievements page the first achievement is whole on screen when the page opens - not a sliver at the scroll edge - at ×1.0
 * and at the KONKR's real ×1.3 (the QA scale `font=1.3,density=1.15`, applied the way the device hook applies it:
 * TestHooks.scale). The KONKR is 960 × 640 px at 240 dpi = 640 × 427 dp. One, a dozen and forty achievements.
 * Fails with the row's shown / full height and the page's other heights, so the cause is in the message.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
open class AchFirstRowRoboTest(private val layout: HomeLayout, private val big: Boolean, private val count: Int) {
    companion object {
        /** The layouts whose own pages are native (their family's card) - every list size, both scales. */
        internal val NATIVE = listOf(HomeLayout.COVERDETAILS, HomeLayout.SHELF, HomeLayout.STACKS, HomeLayout.CONSOLES,
            HomeLayout.TWIN_DESK, HomeLayout.TWIN_ICONS, HomeLayout.TWIN_SHOWCASE,
            // (P2 681f: the native-page batch 1 layouts - every list size, both scales)
            HomeLayout.CRATE, HomeLayout.CLASSIC, HomeLayout.HUB, HomeLayout.RESUME,
            // (682r: batch 2 - KONKR x1.3 showed the magazine's CHECKLIST with no row at all)
            HomeLayout.MAGAZINE, HomeLayout.CINEMA, HomeLayout.ARCADE, HomeLayout.CUBE, HomeLayout.SPOT,
            // (682w: batch 3)
            HomeLayout.TERMINAL, HomeLayout.DASH, HomeLayout.LIBRARY, HomeLayout.CARDS, HomeLayout.SIDE, HomeLayout.BAR, HomeLayout.ARCTIC, HomeLayout.STORAGE,
            // (682ad: batch 6)
            HomeLayout.TILES, HomeLayout.GRID,
            // (682ae: batch 7)
            HomeLayout.NIGHT, HomeLayout.BLADES,
            // (726a: class C - Circuit board's datasheet sections)
            HomeLayout.CIRCUIT,
            // (727a: class C - Channel grid's own screens)
            HomeLayout.CHAN,
            // (728a: class C - the Timeline entry's own pages)
            HomeLayout.TIMELINE,
            // (729a: class C - the Hero banner's own pages, both layouts that share it)
            HomeLayout.HERO, HomeLayout.HERO_TILES,
            // (730a: class C - the Memory card browser's own screens)
            HomeLayout.MEMCARD,
            // (731a: class C - the Cardboard box insert's own sheets)
            HomeLayout.BOX,
            // (732a: class C - the File select file's own screens)
            HomeLayout.FILESELECT,
            // (733a: class C - the Case flow case's own pages)
            HomeLayout.CASEFLOW,
            // (734b: class C - the Orbit deck console's own screens)
            HomeLayout.ORBIT,
            // (735a: class C - the Airy home panel's own pages)
            HomeLayout.AIRY,
            // (736a: class C - the Bubble home card's own pages)
            HomeLayout.BUBBLES,
            // (737a: class C - the Pocket camera menu's own pages)
            HomeLayout.CAMERA,
            // (738a: class C - the Panel dashboard's own pages)
            HomeLayout.PANELS,
            // (739a: class C - the Rental shop's own tag pages)
            HomeLayout.SHOP,
            // (740a: class C - Green core's own console pages)
            HomeLayout.GREENCORE,
            // (741a: class C - the Art library's own gallery pages)
            HomeLayout.ARTLIB,
            // (742a: class C - the Cross menu's own pages)
            HomeLayout.CROSS,
            // (743a: class C - the Live tiles panel's own pages)
            HomeLayout.LIVE,
            // (744a: class C - Layout Studio's own pages)
            HomeLayout.CUSTOM)
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} big={1} n={2}")
        fun cases(): List<Array<Any>> {
            val out = mutableListOf<Array<Any>>()
            for (l in NATIVE) for (b in listOf(false, true)) for (n in listOf(1, 12, 40)) out += arrayOf<Any>(l, b, n)
            // every other layout: the usual list at the real ×1.3
            for (l in HomeLayout.values()) if (l !in NATIVE && l != HomeLayout.CROSS) out += arrayOf<Any>(l, true, 12)
            return out
        }
        const val FIRST = "Defeat the first colossus"
    }

    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun frames(n: Int) = repeat(n) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun words(n: SemanticsNode) = (n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() + " " +
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()).lowercase()

    @Test fun firstAchievementIsWholeWhenThePageOpens() {
        val g = Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/s.iso")
        val list = (1..count).map { i ->
            RaAchievementDetail(i, if (i == 1) FIRST else "Achievement number $i of the set", "Synthetic fixture achievement $i, a description that runs to a second line.",
                5 + i, null, null, if (i % 3 == 0) null else "2026-11-01 10:00:00", null, unlockPercent = 10f, displayOrder = i)
        }
        val state = LauncherUiState().copy(raAchievementDetails = mapOf(g.stableKey to list),
            raProgress = mapOf(g.stableKey to RaGameProgress(NumPossibleAchievements = count, NumAchieved = list.count { it.isUnlocked })))
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        try {
            compose.mainClock.autoAdvance = false
            // (In the app's own theme, as on the device: its body text has a 24 sp line - without it every row measured shorter.)
            compose.setContent { com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                GameWindow(layout, g, state, emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {}) } }
            frames(180)
            // Open Achievements as a person would: its control (behind More / ••• where the window keeps it there), else R1 R1.
            val ach = SemanticsMatcher("achievements control") { n -> n.config.contains(SemanticsActions.OnClick) &&
                words(n).split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.let { w -> w.isNotEmpty() && w.all { it in setOf("achievements", "trophies") } } }
            fun firstRow() = compose.onAllNodes(SemanticsMatcher("first achievement") { n -> n.config.contains(SemanticsActions.OnClick) && FIRST.lowercase() in words(n) })
                .fetchSemanticsNodes()
            if (firstRow().isEmpty()) {
                var found = compose.onAllNodes(ach).fetchSemanticsNodes()
                if (found.isEmpty()) {
                    val more = SemanticsMatcher("More") { n -> n.config.contains(SemanticsActions.OnClick) && words(n).trim().let { w -> w == "•••" || w.split(Regex("[^a-z•]+")).filter { it.isNotEmpty() }.let { ws -> ws.isNotEmpty() && ws.all { it == "more" } } } }
                    val ms = compose.onAllNodes(more).fetchSemanticsNodes()
                    if (ms.isNotEmpty()) { compose.onAllNodes(more)[ms.size - 1].performSemanticsAction(SemanticsActions.OnClick); frames(20) }
                    found = compose.onAllNodes(ach).fetchSemanticsNodes()
                }
                if (found.isNotEmpty()) compose.onAllNodes(ach)[found.size - 1].performSemanticsAction(SemanticsActions.OnClick)
                else org.robolectric.shadows.ShadowDialog.getLatestDialog()?.let { d ->
                    repeat(2) {
                        d.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_R1)); frames(1)
                        d.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BUTTON_R1)); frames(30)
                    }
                }
                frames(60)
            }
            val rows = firstRow()
            // (Opened in the layout's own full list instead of a page - Steam / a layout without the page - is not this check.)
            // (529t: what was there instead, for AIRY / CASEFLOW - the steps the test took and the controls it could see)
            if (rows.isEmpty()) println("ACHFIRST $layout: no page - clickable now: " + compose.onAllNodes(SemanticsMatcher("clickable") {
                it.config.contains(SemanticsActions.OnClick) }).fetchSemanticsNodes().map { words(it).trim().take(30) }.filter { it.isNotBlank() })
            org.junit.Assume.assumeTrue("$layout: no Achievements page with rows reached", rows.isNotEmpty())
            if (System.getenv("VOLTCAP_ACHPROBE") != null) compose.onAllNodes(SemanticsMatcher("clickable") { it.config.contains(SemanticsActions.OnClick) })
                .fetchSemanticsNodes().forEach { println("ACHPROBE ${words(it).trim().take(40)} size=${it.size} at=${it.boundsInRoot} parent=${it.parent?.boundsInRoot}") }
            val r = rows.last()
            val full = r.size.height.toFloat(); val shown = r.boundsInRoot.height
            // (the page as drawn, for the review of a failure: build/achfirst/<case>.png)
            if (shown < full - 1.5f) runCatching {
                // (the dialog's own view drawn into a bitmap - captureToImage waits for an idle window that never comes)
                val v = org.robolectric.shadows.ShadowDialog.getLatestDialog()!!.window!!.decorView
                val bmp = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888)
                v.draw(android.graphics.Canvas(bmp))
                val dir = java.io.File("build/achfirst").apply { mkdirs() }
                java.io.FileOutputStream(java.io.File(dir, "${layout}_${if (big) "x13" else "x10"}_n$count.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }.onFailure { println("ACHFIRST capture failed: $it") }
            assertTrue("$layout ×${if (big) 1.3 else 1.0} n=$count: first achievement shown ${shown}px of ${full}px (at ${r.boundsInRoot})",
                full > 0f && shown >= full - 1.5f)
        } finally { TestHooks.scale.value = null }
    }
}
