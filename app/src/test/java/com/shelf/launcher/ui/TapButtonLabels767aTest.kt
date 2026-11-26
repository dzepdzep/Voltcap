package com.shelf.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
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
 * 767a (P12, TalkBack labels): icon-only controls that react only to raw taps (Icon bar's round Options / Save sync /
 * Achievements buttons next to Play, the classic card's favourite star and More dots) had no name, no role and no click
 * action - TalkBack said nothing and could not press them. tapButtonSemantics gives all three; touch and keys unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class TapButtonLabels767aTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p")
        .map(::File).first { it.exists() }.readText().replace("\r\n", "\n")

    @Test fun theControlIsNamedAButtonAndPressable() {
        var presses = 0
        compose.setContent { Box(Modifier.size(34.dp).tapButtonSemantics("Save sync") { presses++ }) }
        val node = compose.onNodeWithContentDescription("Save sync")
        val cfg = node.fetchSemanticsNode().config
        assertEquals(Role.Button, cfg.getOrNull(SemanticsProperties.Role))
        assertEquals("Save sync", cfg.getOrNull(SemanticsActions.OnClick)?.label)
        node.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, presses)
    }

    @Test fun iconBarRoundButtonsCarryTheirNames() {
        val s = src("ui/StyleIconBar.kt")
        val fn = s.substringAfter("private fun BarRound(").substringBefore("\n}\n")
        assertTrue("BarRound takes a label", fn.startsWith("icon: androidx.compose.ui.graphics.vector.ImageVector, label: String"))
        assertTrue("BarRound applies it", fn.contains(".tapButtonSemantics(label)"))
        for (name in listOf("\"Options\"", "\"Save sync\"", "\"Achievements\"")) assertTrue(name, s.contains("BarRound(Icons.Filled.") && s.contains(name))
        assertEquals("every BarRound call is named", 3, Regex("""BarRound\(Icons\.Filled\.\w+, "[A-Z][a-z ]+"""").findAll(s).count())
    }

    @Test fun classicCardMiniIconsCarryTheirNames() {
        val s = src("ui/GameCard.kt")
        val fn = s.substringAfter("private fun MiniIcon(").substringBefore("\n}\n")
        assertTrue(fn.contains("label: String"))
        assertTrue(fn.contains(".tapButtonSemantics(label)"))
        assertTrue(s.contains("\"Remove from favourites\" else \"Add to favourites\""))
        assertTrue(s.contains("MiniIcon(Icons.Filled.MoreHoriz, label = \"More options\""))
    }
}
