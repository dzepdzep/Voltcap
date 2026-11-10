package com.shelf.launcher.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 488: D284 action rows that fit, K1 spacing (Voltcap row, Tiles, Case stacks), K3 / K2 / K4 QA hooks. */
class Batch488Test {
    private fun src(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name"),
    ).first { it.exists() }.readText()

    @Test fun rowsShareTheRoomOrScroll() {
        assertEquals(40.dp, fitRowHeight(400.dp, 8, 2.dp, 40.dp, 30.dp))
        // 8 rows in 300 dp with 2 dp gaps: 35.75 dp each.
        assertEquals(35.75f, fitRowHeight(300.dp, 8, 2.dp, 40.dp, 30.dp)!!.value, 0.01f)
        assertNull(fitRowHeight(200.dp, 8, 2.dp, 40.dp, 30.dp))
    }

    @Test fun scrollingRowsShowWholeRowsOnly() {
        // p488d (Thor, Side): 160 dp for 8 rows -> 4 whole rows of 38.5 dp, none cut at the edge
        val h = snapRowHeight(160.dp, 8, 2.dp, 40.dp, 30.dp)!!
        assertEquals(38.5f, h.value, 0.01f)
        assertEquals(160f, (h * 4 + 2.dp * 3).value, 0.01f)
        assertTrue(snapRowHeight(100.dp, 8, 2.dp, 40.dp, 30.dp)!!.value >= 30f)
        assertNull(snapRowHeight(20.dp, 8, 2.dp, 40.dp, 30.dp))
        assertTrue(src("WinScenesHomes2.kt").contains("rowH ?: snapH ?: 40.dp"))
    }

    @Test fun saveDetailButtonsOutsideTheScroll() {
        val s = src("SaveExplorer.kt")
        val d = s.substring(s.indexOf("private fun SaveDetail("), s.indexOf("internal enum class SaveObjectKind"))
        assertTrue(d.contains("Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))"))
        assertTrue(!d.contains("Column(Modifier.weight(1f).verticalScroll(rememberScrollState()))"))
    }

    @Test fun k4SpinesComingBackKeepTheirName() {
        assertEquals(960, SPINE_MAX_PX)
        // 144 x 1280 (the Thor's SNES pack spine) -> the small named copy: 36 x 320, ~46 KB: ~360 in 16 MB
        assertEquals(36 to 320, spineFitSize(144, 1280, SPINE_LOW_PX))
        val s = src("CoverCases.kt")
        assertTrue(s.contains("low != null -> SpineCache.Titled(false, low)"))
        assertTrue(s.indexOf("low != null -> SpineCache.Titled(false, low)") < s.indexOf("else -> SpineCache.Titled(false, SpineCache.peekUntitled(res, landscape))"))
        assertEquals(2, Regex("keepLow\\(key, out\\)").findAll(s).count())
    }

    @Test fun stacksSpacingRebuildsTheGeometry() {
        // 488b, Thor k1s: 50 / 100 / 250 % looked the same - the pitch wasn't one of the geometry's keys
        assertTrue(src("StyleCaseStacks.kt").contains("val geo = remember(w, h, caseW, caseH, depthK, density, mirror, pitch, nSide)"))
    }

    @Test fun d284ScenesUseFittedRows() {
        assertTrue(src("WinScenesConsole.kt").contains("val restH = fitRowHeight("))
        assertTrue(src("WinScenesHomes2.kt").contains("val rowH = fitRowHeight(maxHeight, keys.size, 2.dp, 40.dp, 30.dp)"))
        assertTrue(src("WinScenesHomes.kt").contains("val rowH = fitRowHeight(maxHeight, acts.size"))
    }

    @Test fun spacingDefaultsKeepTodaysGaps() {
        assertEquals(1f, stacksPitchK(100), 0.0001f)
        assertTrue(stacksPitchK(50) >= 0.75f)
        val f = File(listOf("../src_copy/data/Features.kt", "src/main/java/com/shelf/launcher/data/Features.kt", "app/src/main/java/com/shelf/launcher/data/Features.kt").first { File(it).exists() }).readText()
        for (k in listOf("\"row_spacing\", 100", "\"tiles_spacing\", 100", "\"stacks_spacing\", 100")) assertTrue(k, f.contains(k))
        assertTrue(src("GameDock.kt").contains("baseSpacing * (com.shelf.launcher.data.Features.rowSpacing.state.value / 100f)"))
    }

    @Test fun ps2SwitchOnlyForPs2Saves() {
        val s = src("SaveExplorer.kt")
        assertTrue(s.contains("val ps2Style = with(Features) { Features.saveVaultStyle.state.value.showsIcons() }")) // (Batch 523: Icons only or PS2 browser)
        assertTrue(src("TestHooks.kt").contains("voltcap_test_ps2browser"))
    }
}
