package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.shelf.launcher.data.HomeLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 679c (owner request H7 / D347): on the Console hub at display density 1.4 the category title ("All games") lay over
 * the profile name / score. The title starts at a fixed spot a fifth of the way down (categoryPartSpot HUB = 0 to 0.2); on a
 * short screen in dp that spot falls inside the Hub's own profile / icons / clock row. The title now keeps clear of the row
 * the layout registers (PartRects.rootPx via keepPartsClear) as it already did of parts you placed (update 258); where they
 * don't meet it stays exactly where it was. The numbers are illustrative (a ~260 dp tall home at 1.4, ~393 dp at x1.0).
 * Fails on the 679b sources (PartRects.rootPx / layoutId / inLayer / keepPartsClear don't exist; LauncherScreen passes only
 * the placed parts).
 */
class HubCategoryClear679cTest {
    private val density = 3.85f
    private val titleW = 120.dp
    private val titleH = 26.dp

    private fun zone(h: Float) = SafeZone(width = 400.dp, height = h.dp, left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)

    /** The Hub's profile row, root px (status bar above it, the parts layer at [origin]). */
    private fun rowPx(origin: Offset, topDp: Float, bottomDp: Float) =
        Rect(origin.x, origin.y + topDp * density, origin.x + 400 * density, origin.y + bottomDp * density)

    @Test fun atDensity14TheTitleStepsBelowTheProfileRow() {
        val z = zone(260f)
        val want = categoryPartSpot(HomeLayout.HUB)
        val origin = Offset(0f, 0f)
        val row = PartRects.inLayer(rowPx(origin, 34f, 76f), origin, density)
        val before = z.place(want.first, want.second, titleW, titleH)
        // The old behaviour: no region to keep clear of -> the fixed spot, inside the row.
        assertEquals(want, PartRects.clearSpot(want, z, titleW, titleH, emptyList()))
        assertTrue("the fixed spot falls inside the profile row (the bug)", before.y.value < row.bottom && before.y.value + titleH.value > row.top)
        val f = PartRects.clearSpot(want, z, titleW, titleH, listOf(row))
        val after = z.place(f.first, f.second, titleW, titleH)
        assertEquals("below the row, 6 dp apart", row.bottom + 6f, after.y.value, 0.01f)
        assertEquals("same left edge", before.x.value, after.x.value, 0.01f)
    }

    @Test fun atNormalDensityTheTitleStaysWhereItWas() {
        val z = zone(393f)
        val want = categoryPartSpot(HomeLayout.HUB)
        val row = PartRects.inLayer(rowPx(Offset.Zero, 30f, 64f), Offset.Zero, density)
        assertEquals("no overlap -> unchanged", want, PartRects.clearSpot(want, z, titleW, titleH, listOf(row)))
    }

    @Test fun rootRegionsConvertIntoTheLayersDp() {
        val origin = Offset(40f, 120f)
        val r = PartRects.inLayer(Rect(40f + 3.85f * 10, 120f + 3.85f * 20, 40f + 3.85f * 110, 120f + 3.85f * 60), origin, density)
        assertEquals(10f, r.left, 0.01f); assertEquals(20f, r.top, 0.01f); assertEquals(110f, r.right, 0.01f); assertEquals(60f, r.bottom, 0.01f)
        assertEquals("layout:HUB:top", PartRects.layoutId(HomeLayout.HUB, "top"))
    }

    private fun source(name: String): String {
        val rel = "src/main/java/com/shelf/launcher/ui/$name"
        return listOf(File(rel), File("app/$rel")).first { it.exists() }.readText()
    }

    @Test fun theHubRegistersItsRowAndTheTitleReadsTheLayoutsRegions() {
        assertTrue("the Hub's profile row registers itself",
            source("StyleConsoleHomes.kt").contains("Modifier.keepPartsClear(PartRects.layoutId(com.shelf.launcher.data.HomeLayout.HUB, \"top\"))"))
        assertTrue("the category title keeps clear of the layout's regions",
            source("LauncherScreen.kt").contains("+ PartRects.layoutIds(overlayLayout) else emptyList()"))
        assertTrue("MovableWidget converts them into its layer",
            source("HomeEdit.kt").contains("PartRects.at[k] ?: PartRects.rootPx[k]?.let { r -> origin?.let { o -> PartRects.inLayer(r, o, density.density) } }"))
    }
}
