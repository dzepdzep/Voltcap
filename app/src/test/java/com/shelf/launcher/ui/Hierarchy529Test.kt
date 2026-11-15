package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 529 (master sprint, Phase 3): the approved action row where hardware showed a window without it (Flip 2 f529a / f529b). */
class Hierarchy529Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    @Test fun channelGridKeepsAWiiPairWithStartFilledAndRoundMore() {
        val s = src("WinScenesWii.kt")
        val body = s.substring(s.indexOf("private fun WiiChannelPreviewBody("), s.indexOf("private fun WiiMorePage("))
        // The full-width More bar only where Back is still a key; otherwise round ••• beside a filled Start.
        assertTrue(body.contains("if (small.isNotEmpty() && back != null) {"))
        // (Owner, 13 Nov: the round ••• is the last key - after Start.)
        assertTrue(body.indexOf("if (back == null && small.isNotEmpty()) WiiRoundMore(") > body.indexOf("if (play != null) WiiBigKey(play"))
        assertTrue(body.contains("primary = back == null)"))
        assertTrue(s.contains("private fun WiiRoundMore("))
    }

    @Test fun resumesDiscoverCardHasMore() {
        assertTrue(WinScene.DISCOVER_CARD in MORE_SCENES)
    }

    /** Owner, 13 Nov: "on every pop-up, the last thing that's shown from the options should be the 3 dots, not anything else beside it." */
    @Test fun moreIsTheLastKeyOfEveryWindowWithMore() {
        val s = src("GameWindow.kt")
        val body = s.substring(s.indexOf("private fun SceneBody("), s.indexOf("internal val MORE_SCENES"))
        // Not in the Play / Details row any more (it sat there, before Inspect)...
        assertTrue(!body.contains("if (labActions) moreKey?.let { add(it) }"))
        // ...but closing the tools, after Inspect (closed) or after everything More shows (open).
        assertTrue(body.contains("else allTools.filter { it.label == \"Inspect\" }) + moreKey!!"))
        assertTrue(body.contains("else -> allTools.filter { it.label == \"Inspect\" } + moreKey!!"))
        // Storage's one-line row: Play, Details, Inspect, then •••.
        val w = src("WinScenes.kt")
        assertTrue(w.contains("ctx.tools.filter(PopupLab::isMore) else emptyList()"))
    }

    /** Flip 2 mlast529b: Game shelf's flowing key row put "Saves: …" beside •••, after it. sceneKeys ends on More. */
    @Test fun sceneKeysEndOnMore() {
        val m = src("WinScenesMore.kt")
        assertTrue(m.contains("all.filterNot(PopupLab::isMore) + all.filter(PopupLab::isMore)"))
    }
}
