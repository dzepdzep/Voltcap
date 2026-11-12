package com.shelf.launcher.ui

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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
 * Popup native pages (12 Nov): the real game window composed on the JVM (Robolectric + Compose UI test), at the KONKR's
 * 640 × 427 dp. Probe: the Storage boxes window opens and shows its actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class WinFocusRoboTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(RegisterComposeTestActivity()).around(compose)

    @Test fun storageWindowComposes() {
        val g = Game("Probe Quest", "SNES", 1, SourceType.ROM, romPath = "/r/p.sfc")
        // (The window has endless motion - marquees, glows: an auto-advancing clock never goes idle. Frames are stepped here.)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GameWindow(HomeLayout.STORAGE, g, LauncherUiState(), emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {})
        }
        repeat(120) { compose.mainClock.advanceTimeByFrame() }
        val tree = compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 60)
        println("WINTREE-BEGIN\n$tree\nWINTREE-END")
        assertTrue(tree, tree.contains("Probe Quest"))
    }
}
