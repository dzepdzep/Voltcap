package com.shelf.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 478: D271 (window names never cut) and D268 (Terminal's blinking cursor no longer resizes its line). */
class Batch478Test {
    private fun src(name: String): String =
        listOf("src/main/java/com/shelf/launcher/ui/$name", "app/src/main/java/com/shelf/launcher/ui/$name")
            .map(::File).first { it.exists() }.readText()

    // D271 (T3 k477T3A): a very long name stopped at maxLines in a dozen window scenes. Names go through StableFullText
    // (shrink, one more line, then slide) or a marked marquee.
    @Test fun noWindowSceneCutsTheNameAtALineLimit() {
        for (f in listOf("WinScenes.kt", "WinScenesConsole.kt", "WinScenesArcade.kt", "WinScenesHomes2.kt", "WinScenesMore.kt", "WinSceneFamilies.kt")) {
            val s = src(f)
            val bad = Regex("""(?<![A-Za-z])Text\(\s*ctx\.name""").findAll(s).map { m ->
                val tail = s.substring(m.range.last + 1, minOf(s.length, m.range.last + 400))
                val end = Regex("""\n\s*(\w+\(|Spacer|Box|Row|Column|[}@])""").find(tail.substringAfter('\n', ""))?.let { tail.indexOf('\n') + 1 + it.range.first } ?: tail.length
                tail.substring(0, end)
            }.filter { Regex("""maxLines = [2-9]""").containsMatchIn(it) }.toList()
            assertTrue("$f: $bad", bad.isEmpty())
        }
    }

    @Test fun theGenericWindowNameSlidesInsteadOfBeingCut() {
        val s = src("GameWindow.kt")
        val w = s.substring(s.indexOf("private fun WinText("), s.indexOf("private fun WinText(") + 900)
        assertTrue(w.contains("WinFullText("))
    }

    // 478a regression (o478A Blades): the reserved 2nd line of a short name pushed the guide's Back under its edge.
    @Test fun windowNamesDoNotReserveLinesTheyDoNotUse() {
        assertTrue(src("FitText.kt").contains("style, reserve = false)"))
        for (f in listOf("WinScenes.kt", "WinScenesConsole.kt", "WinScenesArcade.kt"))
            assertFalse(f, src(f).contains("StableFullText("))
    }

    // D268 (o477t): "█" vs " " changed the command line's height every blink.
    @Test fun terminalCursorBlinksByColourNotByCharacter() {
        val s = src("StyleTerminal.kt")
        assertFalse(s.contains("""(if (blink) "█" else " ")"""))
        assertTrue(s.contains("Color.Transparent)); append(\"█\"); pop()"))
    }

    // D270 (owner): on two screens the upper one no longer repeats the name the lower ribbon shows; one screen keeps it.
    @Test fun pocketRibbonUpperScreenNamesTheGameOnlyWhenBothPartsShareOneScreen() {
        val s = src("PocketRibbon.kt")
        val cap = s.substring(s.indexOf("Update 478, D270"), s.indexOf("Update 478, D270") + 700)
        assertTrue(cap.contains("if (framed) {"))
        assertTrue(cap.contains("PocketText(this, titles.display(g.name)"))
    }

    // R26 (o477T3A): Cinema's round keys sat in a sideways-scrolling row — one half under the edge, 18 px wide.
    @Test fun cinemaKeysWrapInsteadOfScrolling() {
        val s = src("WinScenesHomes.kt")
        val c = s.substring(s.indexOf("internal fun CinemaPremiere"), s.indexOf("internal fun CinemaPremiere") + 9800) // (506: the premiere mark added ~400 characters)
        assertFalse(c.contains("Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp))"))
        assertTrue(c.contains("FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp))"))
    }

    // o478D: a long name's 2nd line pushed the Blades guide's Back under the blade; Cinema's empty story was a 18 px stop.
    @Test fun bladesNameIsOneLineAndCinemaStoryOnlyAStopWithText() {
        assertTrue(src("WinScenesConsole.kt").contains("fontWeight = FontWeight.Light, maxLines = 1)"))
        assertTrue(src("WinScenesHomes.kt").contains("if (readable) Modifier.sceneReadable(s, ctx.accent) else Modifier"))
        assertTrue(src("GameWindow.kt").contains("Modifier.heightIn(min = 24.dp).clip(shape)"))
    }

    // E5 (owner + Odin cf479): a bright 1 px line where Case flow's reflection ends — the fade mask stopped at the
    // layer's fractional bottom, the rounded-out last row kept the mirror image.
    @Test fun caseReflectionMaskCoversTheWholeLayer() {
        val s = src("Case3D.kt")
        assertTrue(s.contains("c.drawRect(cx - w * 2f - 2f, floorY - d - 2f, cx + w * 2f + 2f, reflBottom + 4f, fade)"))
    }

    // B2: the backdrop clip pauses (keeps its place) under an opaque full-screen page, not under panels with edges.
    @Test fun backdropClipPausesOnlyUnderOpaquePages() {
        val s = src("LauncherScreen.kt")
        assertTrue(s.contains("val clipCovered = categoryScreenOpen || guideForGame != null || manualFor != null || artViewer != null || viewingShots != null"))
        assertFalse(Regex("val clipCovered = [^\\n]*(showSettings|showAppDrawer|showMenu)").containsMatchIn(s))
    }

    // B1: Cinema and Arctic run to every screen edge (no 12 dp side / 8 dp bottom gutter); the others keep theirs.
    @Test fun fullBleedHomesHaveNoGutter() {
        val h = src("StyleHomes.kt")
        assertTrue(h.contains("internal fun HomeLayout.fullBleed(): Boolean = drawsUnderStatusBar()"))
        assertTrue(h.contains("this == HomeLayout.CINEMA || this == HomeLayout.ARCTIC"))
        val s = src("LauncherScreen.kt")
        assertTrue(s.contains("bottom = if (legendShown) 28.dp else if (state.appearance.homeLayout.fullBleed()) 0.dp else 8.dp"))
        assertTrue(s.contains("end = if (state.appearance.homeLayout.fullBleed()) 0.dp else 12.dp"))
    }

    private fun data(name: String): String = listOf(
        File("../src_copy/data/$name"), File("src/main/java/com/shelf/launcher/data/$name"), File("app/src/main/java/com/shelf/launcher/data/$name"),
    ).first { it.exists() }.readText()

    // B3: status bar Follow layout / Always / Minimal / Hidden; existing installs keep the full bar, new ones follow.
    @Test fun statusBarModes() {
        val f = data("Features.kt")
        assertTrue(f.contains("enum class StatusMode(val label: String) { FOLLOW(\"Follow layout\"), ALWAYS(\"Always\"), MINIMAL(\"Minimal\") }"))
        assertTrue(f.contains("if (!p.contains(\"status_mode\")) statusMode.value = if (FirstRun.isDone(context.applicationContext)) StatusMode.ALWAYS else StatusMode.FOLLOW"))
        val a = data("AppearanceStore.kt")
        assertTrue(a.contains("Features.StatusMode.FOLLOW -> homeLayout.statusFollowsMinimal()"))
        val h = src("HomeChrome.kt")
        // Minimal keeps the clock and the battery; search, Wi-Fi, devices, profile and progress go.
        assertTrue(h.contains("if (accessories.isNotEmpty() && !minimal)"))
        assertTrue(h.contains("if (!minimal) Group {\n                Icon(Icons.Filled.Search"))
        assertTrue(h.contains("if (!minimal) Icon(if (wifiConnected)"))
        assertTrue(h.contains("if (progress.isNotEmpty() && !minimal"))
        assertTrue(src("LauncherScreen.kt").contains("minimal = state.appearance.statusMinimal"))
        assertTrue(src("SettingsDialog.kt").contains("arrayOf(\"Follow layout\", \"Always\", \"Minimal\", \"Hidden\")"))
    }
}
