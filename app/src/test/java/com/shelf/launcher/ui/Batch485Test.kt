package com.shelf.launcher.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 485: KONKR — a picked cover far off the case's shape gives way to the box scan in Case Flow too. */
class Batch485Test {
    private fun src(name: String): String = listOf(
        File("../src_copy/ui/$name"), File("src/main/java/com/shelf/launcher/ui/$name"), File("app/src/main/java/com/shelf/launcher/ui/$name"),
    ).first { it.exists() }.readText()

    @Test fun caseFrontKnowsTheCoverShapeFromItsOwnDecode() {
        val c = src("Case3D.kt")
        assertTrue(c.contains("val coverAspect = coverKnown ?: coverProbe?.let"))
        assertTrue(c.contains("val front0 = rememberSoftBitmap(if (faces && !frontPending) ownFront ?: cover else null, 480, trim = true)"))
        assertTrue(src("RealMedia.kt").contains("val coverOff = CoverAspects.rawOf(cover)"))
    }

    @Test fun flatCaseTakesTheScanForAFarOffPickedCover() {
        val r = src("RealMedia.kt")
        assertTrue(r.contains("val pickedOff = UserCovers.isPicked(cover0) && (cover0 as? String)?.let { CoverAspects.rawOf(it) }"))
        assertTrue(r.contains("val scanUse = scan ?: ownScan"))
        assertTrue(r.contains("val caseScan = if (forAll || (UserCovers.isPicked(cover0) && (!pickedOff || scanUse == null))) null else"))
    }

    // D3 (Thor): the second screen's own brightness — its window's override, 0 = the same as the main screen.
    @Test fun secondScreenHasItsOwnBrightness() {
        val f = listOf(File("../src_copy/data/Features.kt"), File("src/main/java/com/shelf/launcher/data/Features.kt"), File("app/src/main/java/com/shelf/launcher/data/Features.kt")).first { it.exists() }.readText()
        assertTrue(f.contains("val secondBrightness = IntPref(\"second_brightness\", 0, 0..100)"))
        val d = src("SecondDim.kt")
        assertTrue(d.contains("own > 0 -> own / 100f"))
        assertTrue(d.contains("if (dimmed) undim() else backlight(false)"))
        assertTrue(src("SettingsDialog.kt").contains("ShelfCard { SecondBrightnessChoice() }"))
    }
}
