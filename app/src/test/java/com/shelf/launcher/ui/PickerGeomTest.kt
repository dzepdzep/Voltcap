package com.shelf.launcher.ui

import androidx.compose.ui.geometry.Offset
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 527 Phase 1: one centre for each profile's picture, image, rings, halo and the wire (PickerGeom). */
class PickerGeomTest {
    private fun src(p: String): String = listOf("src/main/java/com/shelf/launcher/$p", "app/src/main/java/com/shelf/launcher/$p").map(::File).first { it.exists() }.readText()

    @Test fun scalingRoundThePictureKeepsItsCentre() {
        // A bubble: 12 px padding, a 150 px picture, then the name and badge - a 260 px column, 180 px wide.
        val pad = 12; val size = 150; val colH = 260f; val colW = 180f
        val centre = Offset(colW / 2f, PickerGeom.centreY(pad, size))
        assertEquals(87f, centre.y, 0f)
        val k = 1.16f
        // The old pivot (the column's middle) moved the picture's centre up by (k - 1) x 43 px = ~6.9 px: off the wire's ring.
        val old = PickerGeom.scaled(centre, k, Offset(colW / 2f, colH / 2f), 0f)
        assertTrue(old.y < centre.y - 6f)
        // The picture's own centre as the pivot: it stays put (only the lift moves it, as the wire's ring is raised).
        val o = PickerGeom.pivot(pad, size, colH)
        val now = PickerGeom.scaled(centre, k, Offset(o.pivotFractionX * colW, o.pivotFractionY * colH), -12f)
        assertTrue(PickerGeom.concentric(now, Offset(centre.x, centre.y - 12f), 0.01f))
        // Appearing (0.86x) round it too.
        val ap = PickerGeom.scaled(centre, 0.86f, Offset(o.pivotFractionX * colW, o.pivotFractionY * colH), 0f)
        assertTrue(PickerGeom.concentric(ap, centre, 0.01f))
        assertEquals(1f, PickerGeom.tolerancePx(1f), 0f)
        assertEquals(1.6875f, PickerGeom.tolerancePx(2.25f), 0.001f)
        assertFalse(PickerGeom.concentric(Offset(0f, 0f), Offset(2f, 0f), 1.5f))
    }

    @Test fun bothBubblesPivotOnThePictureAndTheGlowIsNotCutByTheCard() {
        val ps = src("ui/ProfileSelect.kt")
        assertEquals(2, Regex("transformOrigin = PickerGeom\\.pivot\\(").findAll(ps).count())
        // ProfileBubble: the light is drawn before the card's rounded clip (it cut the glow into a box on Aurora / Theme).
        val pb = ps.substringAfter("private fun ProfileBubble(").substringBefore("private fun ProfileName(")
        assertTrue(pb.indexOf("drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = style.glow * lift)") < pb.indexOf("clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))"))
        // Every centre is measured in the frame the wire draws in - not root coordinates of another layer.
        assertTrue(ps.contains("centers[i] = f.localPositionOf(c, local)"))
        assertFalse(ps.contains("c.x - rootPos[0]"))
        assertTrue(ps.contains("PickerGeom.centreY(pad.roundToPx(), size.roundToPx())"))
        // The opening's slide moves the wire and the row together.
        assertTrue(ps.contains("Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - enter.value) * 40f }.onPlaced { frame[0] = it }"))
    }

    @Test fun theAlignmentOverlayIsQaOnly() {
        val th = src("ui/TestHooks.kt")
        assertTrue(th.contains("intent?.hasExtra(\"voltcap_test_geom\") == true && com.shelf.launcher.data.QaVariant.isolated(context)"))
        assertTrue(src("ui/ProfileSelect.kt").contains("if (PickerGeomQa.on.value) PickerGeomOverlay("))
        assertTrue(src("ui/PickerGeom.kt").contains("if (i < 0 || !PickerGeomQa.on.value) this else"))
    }
}
