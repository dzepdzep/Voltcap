package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 682as (owner 24 Nov - Arcade, RP Classic: "fix continuous redrawing ... prefer static text for unfocused machines; if a marquee
 * is necessary, animate only when appropriate"): an attract screen's words never slide on a machine you're not on, the big
 * machine's console line ("PLAYSTATION 2", a few px too wide at 9 sp) fits without sliding, and no word is split across lines.
 * The legacy switch (the behaviour before 682as) slides - the check fails on the old implementation.
 * Tube sizes: the RP Classic's big showcase tube (~118 x 76 dp) and a small machine's (~36 x 24 dp).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w620dp-h516dp-land-xhdpi")
class ArcadeTube682asTest(private val big: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "big={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }

    private val compose = createComposeRule()
    @get:Rule val rules: org.junit.rules.RuleChain = org.junit.rules.RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private val names = listOf(
        Game("Shadow of the Colossus", "PS2", 1, SourceType.ROM, romPath = "/r/a.iso"),
        Game("The Legend of Zelda: Ocarina of Time", "N64", 2, SourceType.ROM, romPath = "/r/b.z64"),
        Game("Tetris", "GB", 3, SourceType.ROM, romPath = "/r/c.gb"),
        Game("Castlevania: Symphony of the Night", "PSX", 4, SourceType.ROM, romPath = "/r/d.cue"),
        Game("Super Mario World", "SNES", 5, SourceType.ROM, romPath = "/r/e.sfc")
    )

    private fun slides(): Int = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(QaClip.Slides))
        .fetchSemanticsNodes().size

    private fun layouts(): List<TextLayoutResult> = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult))
        .fetchSemanticsNodes().mapNotNull { n: SemanticsNode ->
            val out = mutableListOf<TextLayoutResult>()
            n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(out); out.firstOrNull()
        }

    private val cur = androidx.compose.runtime.mutableStateOf<Triple<Game, Boolean, Boolean>?>(null)
    private var started = false

    private fun show(g: Game, detail: Boolean, live: Boolean, legacy: Boolean) {
        ArcadeTubeText.legacy.value = legacy
        TestHooks.scale.value = if (big) 1.3f to 1.15f else null
        cur.value = Triple(g, detail, live)
        if (!started) {
            started = true
            compose.setContent {
                com.shelf.launcher.ui.theme.ShelfLauncherTheme {
                    cur.value?.let { (gg, d, l) ->
                        androidx.compose.runtime.key(gg.stableKey, d, l) {
                            Box(Modifier.size(if (d) 118.dp else 36.dp, if (d) 76.dp else 24.dp)) { ArcadeAttract(gg, d, l) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun machinesYouAreNotOnNeverSlide() {
        try {
            for (g in names) for (detail in listOf(true, false)) {
                show(g, detail, live = false, legacy = false)
                assertEquals("${g.name} detail=$detail: a tube you're not on slid", 0, slides())
                for (r in layouts()) {
                    assertFalse("${g.name} detail=$detail: a word split '${r.layoutInput.text}'", brokenWord(r))
                    // (the big tube shows it whole; a far machine's small tube may leave its last words out - never half a word - its
                    // sign right above it carries the whole name, and so does the machine once you move onto it)
                    if (detail) assertFalse("${g.name} detail=$detail: words left out '${r.layoutInput.text}'", r.hasVisualOverflow)
                }
            }
        } finally { ArcadeTubeText.legacy.value = false; TestHooks.scale.value = null }
    }

    @Test fun theBigMachinesConsoleLineFitsWithoutSliding() {
        try {
            show(names[0], detail = true, live = true, legacy = false)
            assertEquals("PLAYSTATION 2 on the big tube slid", 0, slides())
            val console = layouts().first { it.layoutInput.text.text == "PLAYSTATION 2" }
            // (one line - it steps down before it wraps)
            assertEquals("PLAYSTATION 2 wrapped instead of stepping down", 1, console.lineCount)
            for (r in layouts()) assertFalse("a word split '${r.layoutInput.text}'", brokenWord(r))
        } finally { ArcadeTubeText.legacy.value = false; TestHooks.scale.value = null }
    }

    @Test fun legacyBehaviourSlides() {
        // (the implementation before 682as: the same tubes slide - what kept the RP Classic's home repainting)
        try {
            var any = 0
            for (g in names) for (detail in listOf(true, false)) { show(g, detail, live = false, legacy = true); any += slides() }
            assertTrue("the old behaviour slid nowhere - the fixture no longer shows the case", any > 0)
        } finally { ArcadeTubeText.legacy.value = false; TestHooks.scale.value = null }
    }
}
