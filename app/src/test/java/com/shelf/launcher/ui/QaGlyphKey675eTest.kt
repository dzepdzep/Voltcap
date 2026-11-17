package com.shelf.launcher.ui

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * 675e (D-674o-1): KONKR x1.3 k674n read Spotlight's START glyph dy +3 px - glyphcheck walked past the 12 % white pill's edge
 * into a backdrop shape of nearly the pill's colour. GlyphPill now marks its pill ([QaClip.GlyphKey], QA traces only) and the
 * QaClip dump gives each glyph the box of the key it sits in ("key"), which glyphcheck measures against. Checked here: the
 * marked pill is found for its InkGlyph, holds the ink box, and is the GlyphPill's own 18 dp high box; a glyph in no marked key
 * gets none; without QA traces GlyphPill marks nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class QaGlyphKey675eTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    private val onField = QaTrace::class.java.getDeclaredField("on").apply { isAccessible = true }
    private fun traces(on: Boolean) = onField.setBoolean(null, on)
    @After fun restore() = traces(false)

    private fun settle() = repeat(3) {
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }

    private fun glyphs(): List<SemanticsNode> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(QaClip.Glyph), useUnmergedTree = true).fetchSemanticsNodes()

    @Test fun glyphPillGivesItsGlyphTheKeyBox() {
        traces(true)
        compose.setContent {
            Row(Modifier.padding(20.dp)) {
                GlyphPill("START")
                Box(Modifier.padding(start = 30.dp)) { InkGlyph("A", Color.White, 10.dp) } // no key round it
            }
        }
        settle()
        val gs = glyphs().associateBy { it.config[QaClip.Glyph] }
        val start = gs.getValue("START"); val loose = gs.getValue("A")
        val key = QaClip.glyphKeyOf(start)
        assertNotNull("GlyphPill's pill is not found for its glyph", key)
        key!!
        assertEquals("the key is GlyphPill's 18 dp pill", 18f, key.height, 0.5f)
        val ink = start.boundsInWindow
        assertTrue("ink $ink outside its key $key", key.left <= ink.left && key.top <= ink.top && ink.right <= key.right && ink.bottom <= key.bottom)
        assertTrue("pill wider than its ink (padding)", key.width > ink.width)
        assertNull("a glyph in no marked key has none", QaClip.glyphKeyOf(loose))
    }

    @Test fun noMarkWithoutQaTraces() {
        traces(false)
        compose.setContent { GlyphPill("START") }
        settle()
        assertNull(QaClip.glyphKeyOf(glyphs().single()))
    }
}
