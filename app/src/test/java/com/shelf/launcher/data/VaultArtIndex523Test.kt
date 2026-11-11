package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Batch 523: a save's look recorded where its game is, found on a device where it isn't; merged by union, small, web only. */
class VaultArtIndex523Test {
    private val here = VaultArtIndex.Entry("PS2", setOf(VaultArtIndex.norm("WWE SmackDown! vs. RAW (USA)"), VaultArtIndex.norm("WWESmackDown!vs.RAW")),
        cover = "https://cdn2.steamgriddb.com/grid/a.png", at = 1)

    @Test fun findsTheGameByAnyOfItsSaveNames() {
        assertEquals(here, VaultArtIndex.find(listOf(here), "ps2", "WWE SmackDown vs RAW"))           // card title, other spacing
        assertEquals(here, VaultArtIndex.find(listOf(here), "PS2", "WWESmackDown!vs.RAW"))
        assertNull(VaultArtIndex.find(listOf(here), "PS2", "Tekken 4"))
        assertEquals("GameCube", VaultArtIndex.platformKey("gc"))                                     // library key vs a stray's
        val gc = VaultArtIndex.Entry(VaultArtIndex.platformKey("GC"), setOf("metroidprime"), cover = "https://x/y.png")
        assertEquals(gc, VaultArtIndex.find(listOf(gc), "GameCube", "Metroid Prime"))
    }

    @Test fun mergeIsAUnionAndNeverLosesAnything() {
        val other = VaultArtIndex.Entry("PS2", setOf("wwesmackdownvsraw"), logo = "https://cdn2.steamgriddb.com/logo/b.png", at = 2)
        val m = VaultArtIndex.merge(listOf(here), listOf(other))
        assertEquals(1, m.size)
        assertEquals(here.cover, m[0].cover); assertEquals(other.logo, m[0].logo)
        assertTrue(m[0].names.containsAll(here.names))
        assertTrue(VaultArtIndex.sameContent(VaultArtIndex.merge(m, listOf(here)), m))              // merging again changes nothing
        // (toJson / fromJson use Android's org.json - not available to JVM unit tests; checked on the device.)
    }

    @Test fun onlyWebAddressesTravel() {
        assertNull(VaultArtIndex.shareable("file:///sdcard/ES-DE/downloaded_media/x.png"))
        assertNull(VaultArtIndex.shareable("/storage/emulated/0/pic.png"))
        assertEquals("https://a/b.png", VaultArtIndex.shareable("https://a/b.png"))
    }
}
