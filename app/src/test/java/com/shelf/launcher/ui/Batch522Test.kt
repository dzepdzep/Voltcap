package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Batch 522: the three badge looks keep each console's own identity; wide wordmarks go compact only where they'd be small. */
class Batch522Test {
    @Test fun regionalKeysNeverWearAnotherConsolesWordmark() {
        assertNull(BadgeLogos.wordmark("SFC"))            // not "SUPER NINTENDO"
        assertNull(BadgeLogos.wordmark("Famicom"))        // not "NES"
        assertNull(BadgeLogos.wordmark("MegaDrive"))      // not "GENESIS"
        assertNull(BadgeLogos.wordmark("WonderSwanColor"))
        assertTrue(BadgeLogos.wordmark("Genesis")!!.first.endsWith("badge_logos/Genesis.svg"))
        assertTrue(BadgeLogos.wordmark("MAME")!!.first.endsWith("badge_logos/Arcade.svg")) // the same console under two keys
        assertNull(BadgeLogos.wordmark("3DO"))            // dark-on-white set art, left out (521b)
    }

    @Test fun symbolsOnlyWhereTheSetHasOne() {
        assertTrue(BadgeLogos.symbol("PS2")!!.first.endsWith("badge_icons/PS2.webp"))
        assertTrue(BadgeLogos.symbol("SFC")!!.first.endsWith("badge_icons/SNES.webp")) // the emblem both regions print
        assertNull(BadgeLogos.symbol("GBA"))
        assertNull(BadgeLogos.symbol("PS5"))
    }

    @Test fun wideWordmarksAreCompactOnSmallTilesWholeOnBigOnes() {
        // a 96 dp tile: badge 12.48 dp, pill at most 74.9 dp (78 %); a 200 dp tile: 20 dp, 170 dp (85 %)
        assertFalse(BadgeLogos.fitsWordmark(11.19f, 12.48f, 74.9f))  // GBA
        assertFalse(BadgeLogos.fitsWordmark(8.56f, 12.48f, 74.9f))   // Genesis
        assertTrue(BadgeLogos.fitsWordmark(4.61f, 12.48f, 74.9f))    // PS4
        assertTrue(BadgeLogos.fitsWordmark(8.56f, 20f, 170f))        // Genesis on a big tile
        assertTrue(BadgeLogos.fitsWordmark(10.58f, 20f, 170f))       // Lynx on a big tile
        assertTrue(BadgeLogos.fitsWordmark(11.19f, 20f, 170f))       // GBA on a big tile
    }

    @Test fun eachKeyItsOwnShortName() {
        assertEquals("Genesis", com.shelf.launcher.data.ConsoleNames.short("Genesis"))
        assertEquals("Mega Drive", com.shelf.launcher.data.ConsoleNames.short("MegaDrive"))
        assertEquals("Famicom", com.shelf.launcher.data.ConsoleNames.short("Famicom"))
    }
}
