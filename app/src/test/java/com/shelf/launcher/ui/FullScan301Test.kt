package com.shelf.launcher.ui

import com.shelf.launcher.data.FullScanCuts
import com.shelf.launcher.data.PhysicalOverrides
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 301: your own full case scan and its cuts. */
class FullScan301Test {
    @Test fun cutTextRoundTrips() {
        val c = FullScanCuts.parse(FullScanCuts.encode(0.4512f, 0.5123f))!!
        assertEquals(0.4512f, c.first, 0.0001f); assertEquals(0.5123f, c.second, 0.0001f)
        assertNull(FullScanCuts.parse("0.6,0.4"))
        assertNull(FullScanCuts.parse("x"))
        assertNull(FullScanCuts.parse(null))
    }

    @Test fun automaticModelIsUnchanged() {
        val url = "https://example.invalid/auto-scan.png"
        assertEquals("voltcrop:front:PS2:$url", BoxTexture.model(url, BoxPart.FRONT, "PS2"))
        assertNull(BoxTexture.cutOf(BoxTexture.model(url, BoxPart.FRONT, "PS2")))
    }

    @Test fun yourCutTravelsInTheAddress() {
        val url = "file:///data/user_art/abc.png"
        FullScanCuts.set(url, 0.45f to 0.55f)
        try {
            val m = BoxTexture.model(url, BoxPart.BACK, "Xbox360")
            assertTrue(m.startsWith("voltcrop:back@0.4500,0.5500:"))
            val (part, platform, address) = BoxTexture.parse(m)!!
            assertEquals(BoxPart.BACK, part); assertEquals("Xbox360", platform); assertEquals(url, address)
            assertEquals(0.45f, BoxTexture.cutOf(m)!!.first, 0.0001f)
        } finally { FullScanCuts.set(url, null) }
        assertEquals("voltcrop:back:Xbox360:$url", BoxTexture.model(url, BoxPart.BACK, "Xbox360"))
    }

    @Test fun yourFullScanStandsForFrontBackAndSpine() {
        val key = "/test/Halo (USA).iso"
        val scan = "file:///data/user_art/halo.png"
        PhysicalOverrides.set(key, BoxTexture.LABEL, scan)
        try {
            val scraped = listOf("Box" to "https://s/box.png", "Back of the box" to "https://s/back.png", "Disc / cartridge" to "https://s/disc.png")
            val m = PhysicalOverrides.apply(key, scraped).toMap()
            assertEquals(BoxPart.FRONT, BoxTexture.parse(m["Box"])!!.first)
            assertEquals(BoxPart.BACK, BoxTexture.parse(m["Back of the box"])!!.first)
            assertEquals(BoxPart.SPINE, BoxTexture.parse(m["Spine"])!!.first)
            assertEquals(scan, m[BoxTexture.LABEL])
            assertEquals("https://s/disc.png", m["Disc / cartridge"]) // (untouched)
            // A front you picked yourself still wins over the cut.
            PhysicalOverrides.set(key, "Box", "file:///mine.png")
            assertEquals("file:///mine.png", PhysicalOverrides.apply(key, scraped).toMap()["Box"])
        } finally { PhysicalOverrides.clear(key) }
        // Without your scan: the scraped list as it was.
        assertEquals(listOf("Box" to "https://s/box.png"), PhysicalOverrides.apply(key, listOf("Box" to "https://s/box.png")))
    }
}
