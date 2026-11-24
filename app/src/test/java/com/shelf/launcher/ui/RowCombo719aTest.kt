package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.CategoryBar
import com.shelf.launcher.data.DockPosition
import com.shelf.launcher.data.HomeLayout
import com.shelf.launcher.data.RowInfo
import com.shelf.launcher.data.categoryBarFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 719a (owner 22:43, Task 1): a QA path for 716a's real case - game header + Game info / news + category bar of every
 * category + a song in the status bar's player, at x1.3 - and a line saying where the Voltcap row's column landed.
 */
class RowCombo719aTest {
    private fun src(rel: String): String =
        listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    @Test fun comboSetsInfoAndEveryCategoryBarOnTheLayoutInUse() {
        // A layout that has its own "current only" choice still gets the bar of every category.
        val a = AppearanceSettings(homeLayout = HomeLayout.CLASSIC, rowInfo = RowInfo.OFF, categoryBar = CategoryBar.OFF,
            categoryBarByLayout = "CLASSIC=CURRENT;TILES=OFF")
        val c = RowCombo719.appearance(a)
        assertEquals(RowInfo.BOTH, c.rowInfo)
        assertEquals(CategoryBar.FULL, c.categoryBarFor(HomeLayout.CLASSIC))
        assertTrue("other layouts' own choices kept", c.categoryBarByLayout.contains("TILES=OFF"))
        assertEquals("nothing else changed", a.copy(rowInfo = c.rowInfo, categoryBar = c.categoryBar, categoryBarByLayout = c.categoryBarByLayout), c)
    }

    @Test fun lineInDp() {
        assertEquals("CLASSIC MIDDLE top=52.0 bottom=410.0 bar=48.0 screen=448.0 legend=on header=on info=BOTH cats=FULL player=on",
            RowCombo719.line("CLASSIC", DockPosition.MIDDLE, 104f, 820f, 96f, 896f, 2f, true, true, RowInfo.BOTH, CategoryBar.FULL, true))
        assertEquals("CLASSIC TOP top=8.0 bottom=300.5 bar=0.0 screen=448.0 legend=off header=off info=OFF cats=CURRENT player=off",
            RowCombo719.line("CLASSIC", DockPosition.TOP, 16f, 601f, 0f, 896f, 2f, false, false, RowInfo.OFF, CategoryBar.CURRENT, false))
    }

    @Test fun hooksArePreviewOnlyAndLogTheMeasuredColumn() {
        val ls = src("ui/LauncherScreen.kt")
        val combo = ls.substringAfter("val testRowCombo = TestHooks.rowCombo.value").substringBefore("// (Pass 5:")
        assertTrue("preview only", combo.contains("if (!TestHooks.previewOnly) return@LaunchedEffect"))
        assertTrue("the shared helper", combo.contains("RowCombo719.appearance(state.appearance)"))
        assertTrue("off puts the header back", combo.contains("rowComboHeaderWas?.let { com.shelf.launcher.data.Features.homeHeader.value = it }"))
        assertTrue("off ends the fake song", combo.substringAfter("} else {").contains("TestHooks.fakeNowPlaying = null"))
        val col = ls.substringAfter("val rowColAsk = TestHooks.rowCol.intValue").substringBefore("fun BoxScope.GameRows()")
        assertTrue("QA builds only", col.contains("QaTrace.on"))
        assertTrue("the column's own measured edges", col.contains("rowTopPx, rowBottomPx"))
        assertTrue("logged", col.contains("QaTrace.event(\"rowcol\", rowColLine)"))
        val th = src("ui/TestHooks.kt")
        assertTrue(th.contains("getStringExtra(\"voltcap_test_rowcombo\")") && th.contains("getBooleanExtra(\"voltcap_test_rowcol\", false)"))
    }
}
