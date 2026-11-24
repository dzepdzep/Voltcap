package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 529 (master sprint, Phase 3): the approved action row where hardware showed a window without it (Flip 2 f529a / f529b). */
class Hierarchy529Test {
    private fun src(name: String) = listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
        .map(::File).first { it.exists() }.readText()

    // (674l: the owner's button brief of 15 Nov replaces 529's round ••• beside a filled Start: one row of identical Wii keys -
    // Channels, Start (never stretched), then a worded MORE / LESS last, never three dots.)
    @Test fun channelGridIsOneUniformRowWithAWordedMoreLast() {
        val s = src("WinScenesWii.kt")
        val body = s.substring(s.indexOf("private fun WiiChannelPreviewBody("), s.indexOf("private fun WiiMorePage("))
        // (727a: one row for Info and the channel's own screens - on Info it is the Channels / Start / MORE row)
        assertTrue(body.contains("UniformKeyRow(if (main) row.map { it.first } else ctx.buttons, 12.dp"))
        assertTrue(body.contains("moreAct?.let { it to it.label })"))           // MORE the last of the row
        assertTrue(!body.contains("WiiRoundMore("))
        assertTrue(!body.contains("I.MoreHoriz"))                                // no three dots
        assertTrue(!body.contains("Modifier.weight(1f), compact, primary"))      // Start not stretched
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
