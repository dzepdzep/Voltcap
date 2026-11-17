package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 676i (owner 16 Nov, option C): Cube's status bar is one facet panel, not separate outlined boxes; no other layout changes. */
class CubeFacet676iTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n").map { File(it) }.first { it.exists() }.readText()

    @Test fun cubeUsesTheFacetOnly() {
        val s = src("StatusLooks.kt")
        val cube = s.substring(s.indexOf("HomeLayout.CUBE -> Features.cubeTint"), s.indexOf("HomeLayout.MEMCARD ->"))
        assertTrue(cube.contains("StatusLook(BarBacking.FACET"))
        assertTrue(!cube.contains("BarBacking.SEGMENTS"))
        // only Cube wears it: every layout keeps its own identity
        assertTrue(s.split("StatusLook(BarBacking.FACET").size == 2)
        assertTrue(src("HomeChrome.kt").contains(".statusContainer(look, atBottom)"))
    }
}

/** 676m: Cube's A / X / Y hints wrap whole, never squeezed ("Achievements" was cut to "Achieve" at x1.3 on the Thor). */
class CubeHints676mTest {
    @Test fun hintsWrapWhole() {
        val s = listOf("src/main/java/com/shelf/launcher/ui/CubeHub.kt", "app/src/main/java/com/shelf/launcher/ui/CubeHub.kt").map { File(it) }.first { it.exists() }.readText()
        val i = s.indexOf("HubHint(\"Y\", \"Achievements\")")
        val block = s.substring(s.lastIndexOf("FlowRow(", i), i)
        assertTrue(block.contains("Arrangement.spacedBy(12.dp, Alignment.End)"))
        assertTrue(s.contains("Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, softWrap = false)"))
    }
}

/** 676n (Thor x1.3 t676n_x13): the menu hint is never cut, and a short panel keeps the cover flow instead of two-line reserves. */
class CubeShort676nTest {
    private val s = listOf("src/main/java/com/shelf/launcher/ui/CubeHub.kt", "app/src/main/java/com/shelf/launcher/ui/CubeHub.kt").map { File(it) }.first { it.exists() }.readText()

    @Test fun menuHintWrapsInHalves() {
        assertTrue(!s.contains("\"▲ ▼ Choose   Ⓐ Open\""))
        assertTrue(s.contains("listOf(\"▲ ▼ Choose\", \"Ⓐ Open\")"))
    }

    @Test fun shortPanelGivesTheCoversRoom() {
        val lib = s.substring(s.indexOf("private fun HubLibrary"), s.indexOf("private fun HubHint"))
        assertTrue(lib.contains("val lines = if (maxHeight - fixed < CUBE_COVERS_MIN) 1 else 2"))
        // one line still shown whole: FullText shrinks, then slides (rule 11) and reserves its line (Update 460)
        assertTrue(lib.contains("if (lines == 1) FullText(\n                    titles.display(g.name)"))
        assertTrue(lib.contains("if (lines == 1) FullText(line,"))
        assertTrue(!lib.contains("Ellipsis"))
    }
}

/** 676o (D339, RP Nova x1.0): Spotlight's five hints wrap whole instead of cutting OPTIONS and squeezing MENU to nothing. */
class SpotHints676oTest {
    @Test fun hintsWrapWhole() {
        val s = listOf("src/main/java/com/shelf/launcher/ui/StyleSpotlight.kt", "app/src/main/java/com/shelf/launcher/ui/StyleSpotlight.kt").map { File(it) }.first { it.exists() }.readText()
        val f = s.substring(s.indexOf("private fun SpotHints"), s.indexOf("hints.forEachIndexed", s.indexOf("private fun SpotHints")))
        assertTrue(f.contains("FlowRow("))
        assertTrue(!f.contains("    Row(\n"))
    }
}

/** 676r (D340): the home texts homecut.py found cut now shrink then slide (or wrap) - never "…", never cut. */
class HomeCuts676rTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n").map { File(it) }.first { it.exists() }.readText()
    @Test fun eachFound() {
        assertTrue(src("FitText.kt").contains("slideWhenCut && !fits(s, lines)"))
        assertTrue(src("StyleArcade.kt").split("slideWhenCut = true").size == 3)
        assertTrue(src("StyleCircuit.kt").contains("FullText(label, if (on) ink"))
        assertTrue(src("StyleGreenCore.kt").contains("FullText(text, accent.copy(alpha = 0.7f), 9.sp"))
        assertTrue(src("StylePocketCamera.kt").contains("FullText(\"POCKET CAMERA\""))
        assertTrue(src("StyleOrbitDeck.kt").contains("FullText(bits.joinToString(\"   ·   \")"))
        assertTrue(src("StyleRecordCrate.kt").contains("horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment"))
        val card = src("GameCard.kt"); val i = card.indexOf("remainingNote(earned, possible)?.let { \"  ·  \$it\" }")
        assertTrue(!card.substring(i, i + 300).contains("Ellipsis"))
        assertTrue(src("StyleNightMenu.kt").contains("TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 120.sp)"))
    }
}

/** 676v (D342): every Material type style trims the room above its first line and below its last (no letters cut in snug boxes). */
class TrimmedLines676vTest {
    @Test fun allStylesTrimmed() {
        val s = listOf("src/main/java/com/shelf/launcher/ui/theme/Theme.kt", "app/src/main/java/com/shelf/launcher/ui/theme/Theme.kt").map { File(it) }.first { it.exists() }.readText()
        val block = s.substring(s.indexOf("fun androidx.compose.ui.text.TextStyle.trim()"), s.indexOf("labelSmall = t.labelSmall.trim()"))
        for (n in listOf("displayLarge", "headlineSmall", "titleMedium", "bodyLarge", "bodyMedium", "bodySmall", "labelLarge", "labelMedium"))
            assertTrue(n, block.contains("$n = t.$n.trim()"))
        assertTrue(s.contains("LineHeightStyle.Trim.Both"))
    }
}

/** 676y (D342): the height cuts the trimmed lines didn't cure - each fixed where it was. */
class HeightCuts676yTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n").map { File(it) }.first { it.exists() }.readText()
    @Test fun eachFound() {
        assertTrue(src("StyleCube.kt").contains("\"◀\" -> Icons.Filled.ArrowLeft"))
        assertTrue(src("StyleMemoryCard.kt").contains("maxLines = nameLines"))
        assertTrue(src("FitText.kt").contains("text, color, fit.first.sp,"))
        assertTrue(src("StyleArcade.kt").contains("slideWhenCut = true, modifier = Modifier.weight(1f, fill = false))"))
        assertTrue(src("StyleBubbles.kt").contains("maxSize = 11.sp, minSize = 7.sp, maxLines = 3"))
        assertTrue(src("StyleConsoleHomes.kt").contains("wrapContentHeight(align = Alignment.Top, unbounded = true)"))
        assertTrue(src("PlatformLogos.kt").contains("name.contains(\"app\", ignoreCase = true)"))
    }
}

/** 676z (badge review): a coverless name never sits under the console badge, nor breaks inside a word. */
class NameUnderBadge676zTest {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n").map { File(it) }.first { it.exists() }.readText()
    @Test fun plateAndTileLeaveTheBand() {
        val h = src("StyleHomes.kt"); val plate = h.substring(h.indexOf("internal fun StyleNamePlate"), h.indexOf("/** A smooth 0 → 1 while"))
        assertTrue(plate.contains("(BadgeLogos.ART_PILL + 0.6f)") && plate.contains("laneContent(lane, 6.dp, shiftY ="))
        assertTrue(plate.contains("measurer.measure(longest"))
        assertTrue(src("TileHome.kt").contains("bottom = 10.dp + consoleRoom"))
        assertTrue(src("BadgeLane.kt").contains("insetPx + shiftY.roundToPx()"))
    }
}

/** 676z2: the Tiles name is fixed lane content (the count keeps off it); app folders show the apps icon in both logo paths. */
class TilesNameLane676z2Test {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n").map { File(it) }.first { it.exists() }.readText()
    @Test fun wired() {
        assertTrue(src("TileHome.kt").contains(".laneContentAt(lane, Alignment.BottomStart, 10.dp, 10.dp + consoleRoom)"))
        assertTrue(src("PlatformLogos.kt").split("name.contains(\"app\", ignoreCase = true)").size == 3)
    }
}

/** 677e-g (owner 16 Nov, RP Classic): status pieces never squeezed; one profile picture; Storage facts never broken inside. */
class StatusFit677Test {
    private fun src(n: String) = listOf("src/main/java/com/shelf/launcher/ui/$n", "app/src/main/java/com/shelf/launcher/ui/$n").map { File(it) }.first { it.exists() }.readText()
    @Test fun wired() {
        val h = src("HomeChrome.kt")
        assertTrue(h.contains("if (!look.left) StatusFit(cluster)") && h.contains("if (look.left) { StatusFit(cluster);"))
        assertTrue(h.contains("maxWidth = androidx.compose.ui.unit.Constraints.Infinity")) // measured unsqueezed
        assertTrue(h.contains("TileSwitch.hideBarAvatar || OwnProfile.count > 0"))
        assertTrue(h.contains("level < 5 && OwnProfile.count == 0"))
        val hub = src("StyleConsoleHomes.kt")
        assertTrue(hub.contains("OwnProfile.Mark()\n    val open by rememberUpdatedState(c.actions.profile)"))
        assertTrue(src("StyleStorageBoxes.kt").contains("StorageFact(\"Achievements\""))
    }
}

/** 677h: a line break inside a word ("1 / 0", "Neve / r") is found; breaks at spaces and CJK breaks are not. */
class WholeWords677hTest {
    @Test fun brokenWords() {
        assertTrue(brokenWordAt("6 / 10", intArrayOf(5)))          // "6 / 1" | "0"
        assertTrue(brokenWordAt("Never played", intArrayOf(4)))    // "Neve" | "r played"
        assertTrue(!brokenWordAt("Never played", intArrayOf(6)))   // "Never " | "played"
        assertTrue(!brokenWordAt("6 / 10", intArrayOf(4)))         // "6 / " | "10"
        assertTrue(!brokenWordAt("スーパーマリオ", intArrayOf(3)))      // Japanese breaks between characters
        assertTrue(!brokenWordAt("Well-known", intArrayOf(5)))     // after a hyphen
    }
    @Test fun wired() {
        val f = listOf("src/main/java/com/shelf/launcher/ui/FitText.kt", "app/src/main/java/com/shelf/launcher/ui/FitText.kt").map { File(it) }.first { it.exists() }.readText()
        assertTrue(f.contains("autoSize = if (autoFit) WholeWordsAutoSize(fitMin(fontSize), fontSize) else null"))
        assertTrue(f.contains("while (s > bottom && !whole(s, lines))"))
        assertTrue(f.contains("if (!r.hasVisualOverflow) { if (firstFit.isNaN() && s >= bottom) firstFit = s; if (!brokenWord(r)) break }"))
        assertTrue(f.contains("s = maxOf(wordFloor, s - 1f)"))
        // (677i: words kept whole down to half the size, never under 8 sp)
        org.junit.Assert.assertEquals(8f, wordMin(androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)).value, 0.01f)
        org.junit.Assert.assertEquals(10f, wordMin(androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)).value, 0.01f)
    }
}
