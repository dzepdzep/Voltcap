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
        assertTrue(body.contains("else if (small.isNotEmpty()) WiiRoundMore("))
        assertTrue(body.contains("primary = back == null)"))
        assertTrue(s.contains("private fun WiiRoundMore("))
    }

    @Test fun resumesDiscoverCardHasMore() {
        assertTrue(WinScene.DISCOVER_CARD in MORE_SCENES)
    }
}
