package com.shelf.launcher.ui

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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Popup native pages (12 Nov): the real game window composed on the JVM (Robolectric + Compose UI test), at the KONKR's
 * 640 × 427 dp. Probe: the Storage boxes window opens and shows its actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w640dp-h427dp-land-mdpi")
class WinFocusRoboTest {
    @get:Rule val rule = createComposeRule()

    @Test fun storageWindowComposes() {
        val g = Game("Probe Quest", "SNES", 1, SourceType.ROM, romPath = "/r/p.sfc")
        rule.mainClock.autoAdvance = true
        rule.setContent {
            GameWindow(HomeLayout.STORAGE, g, LauncherUiState(), emptyMap(), onClose = {}, onPlay = {}, onFullDetails = {}, onNeedMedia = {})
        }
        rule.mainClock.advanceTimeBy(2000)
        val tree = rule.onRoot(useUnmergedTree = true).printToString(maxDepth = 60)
        println("WINTREE-BEGIN\n$tree\nWINTREE-END")
        assertTrue(tree, tree.contains("Probe Quest"))
    }
}
