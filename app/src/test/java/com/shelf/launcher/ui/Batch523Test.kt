package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 523 (final badge polish): wordmarks whose words are only part of the logo's height are drawn a little taller in the
 * same pill (never past it), a wordmark whose words would read smaller than the Text look's letters goes compact, the ES-DE
 * fallback is sized by its own shape, and logos that fill into a blob in one colour show the name.
 */
class Batch523Test {
    private val h96 = 96f * 0.13f
    private val cap96 = 96f * 0.78f
    private val lh96 = h96 * BadgeLogos.ART_PILL

    @Test fun emblemPlusSmallWordsGetTallerButStayInsideThePill() {
        val full = BadgeLogos.artHeight(4.08f, 1f, false, h96, cap96)          // a logo that is all lettering
        val n64 = BadgeLogos.artHeight(4.42f, BadgeLogos.lettering("N64"), false, h96, cap96)
        assertEquals(lh96 * 0.62f, full, 0.01f)
        assertTrue(n64 > full * 1.25f)                                           // N64 lettering 0.36 -> 30 % taller
        assertTrue(n64 <= lh96 * 0.62f * 1.3f + 0.01f)                           // capped
        assertTrue(n64 < lh96)                                                   // never past the pill
        // symbols fill 84 % of the pill (same size as before Batch 523: 0.74 x the old 1.3 h pill)
        assertEquals(lh96 * 0.84f, BadgeLogos.artHeight(1.28f, 1f, true, h96, cap96), 0.01f)
    }

    @Test fun boostNeverMakesALogoWiderThanTheCap() {
        // WonderSwan 7.79 : 1 with lettering 0.47 - its boost is limited by the pill's width on a 96 dp tile
        val a = BadgeLogos.artHeight(7.79f, 0.47f, false, h96, cap96)
        assertTrue(a * 7.79f + lh96 * 0.7f <= cap96 + 0.01f)
    }

    @Test fun lettersNeverSmallerThanTheTextLooksOnTheSameBadge() {
        assertTrue(BadgeLogos.showsWordmark(4.42f, BadgeLogos.lettering("N64"), h96, cap96))
        assertTrue(BadgeLogos.showsWordmark(5.75f, BadgeLogos.lettering("PS2"), h96, cap96))
        assertFalse(BadgeLogos.showsWordmark(11.19f, 1f, h96, cap96))          // GBA: too wide here
        assertTrue(BadgeLogos.showsWordmark(11.19f, 1f, 20f, 170f))            // whole on a big tile
        // a 66 dp tile: NES's two-line logo would read smaller than "NES" in the Text look -> compact
        assertFalse(BadgeLogos.showsWordmark(6.98f, BadgeLogos.lettering("NES"), 11f, 66f * 0.78f))
        assertEquals(1f, BadgeLogos.lettering("SNES"), 0f)                     // not listed: the words are the logo
        assertEquals(BadgeLogos.lettering("PICO8"), BadgeLogos.lettering("Pico8"), 0f)
    }

    @Test fun esdeFallbackHasItsOwnShape() {
        assertEquals(5.47f, BadgeLogos.esdeForm("file:///android_asset/system_logos/tg16.svg")!!.first, 0f)
        assertNull(BadgeLogos.esdeForm("file:///android_asset/system_logos/sfc.svg"))
        // NGPC's tiny "NEOGEO POCKET COLOR" lettering doesn't read on a 96 dp tile -> its name there
        val ngpc = BadgeLogos.esdeForm("file:///android_asset/system_logos/ngpc.svg")!!
        assertFalse(BadgeLogos.showsWordmark(ngpc.first, ngpc.second, h96, cap96))
    }

    @Test fun blobLogosShowTheName() {
        assertTrue(com.shelf.launcher.data.SystemLogos.silhouetteUnreadable("ScummVM"))
        assertTrue(com.shelf.launcher.data.SystemLogos.silhouetteUnreadable("Steam"))
        assertFalse(com.shelf.launcher.data.SystemLogos.silhouetteUnreadable("TG16"))
    }

    @Test fun storesAreNamedAndInTheGallery() {
        assertEquals("Steam", com.shelf.launcher.data.ConsoleNames.display("Steam"))
        val keys = QaBadgeGallery.MAKERS.flatMap { it.second }
        assertTrue(keys.containsAll(listOf("Steam", "Epic", "GOG", "Amazon", "PC")))
        assertEquals(73, keys.size)                                              // 68 consoles + 5 PC store categories
        assertEquals(keys.size, keys.toSet().size)
    }
}
