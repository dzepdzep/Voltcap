package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 331: the matching case pack (tools/media_templates/case_pack_331/case_manifest.json) — every platform's front frame
 * and window, its back base and band, the files shipped, the fits that keep a header / stripe whole, the scan cuts kept.
 */
class CasePack331Test {
    private fun root(): java.io.File = listOf(java.io.File("."), java.io.File("..")).first { java.io.File(it, "tools/media_templates/case_pack_331").isDirectory }
    private val manifestText by lazy { java.io.File(root(), "tools/media_templates/case_pack_331/case_manifest.json").readText() }
    private val res by lazy { java.io.File(root(), "app/src/main/res/drawable-nodpi") }

    /** R.drawable's name for [id] (the generated R class, by reflection). */
    private fun nameOf(id: Int): String? = com.shelf.launcher.R.drawable::class.java.fields.firstOrNull { it.getInt(null) == id }?.name

    /** The manifest's cases (org.json is only a stub on the JVM: read with plain patterns). */
    private class Case(val keys: List<String>, val front: String, val back: String, val aspect: Float, val win: List<Float>)

    private fun cases(): List<Case> {
        val blocks = manifestText.substringAfter("\"cases\"").split("\"id\":").drop(1)
        fun str(b: String, k: String) = Regex("\"$k\":\\s*\"([^\"]+)\"").find(b)!!.groupValues[1]
        fun arr(b: String, k: String) = Regex("\"$k\":\\s*\\[([^\\]]*)\\]").find(b)!!.groupValues[1]
        return blocks.map { b ->
            Case(
                arr(b, "platform_keys").split(',').map { it.trim().trim('"') },
                str(b, "front_resource"), str(b, "back_resource"),
                Regex("\"front_aspect\":\\s*([0-9.]+)").find(b)!!.groupValues[1].toFloat(),
                arr(b, "art_window").split(',').map { it.trim().toFloat() }
            )
        }
    }

    @Test
    fun everyCaseIsMappedToItsFrameAndWindow() {
        val cases = cases()
        assertEquals(62, cases.size)
        var keys = 0
        for (e in cases) for (k in e.keys) {
            keys++
            val t = MediaCovers.of(k)
            assertNotNull("$k has a case", t); t!!
            assertEquals("$k front", e.front, nameOf(t.frontRes))
            assertTrue("$k front shipped", java.io.File(res, e.front + ".webp").isFile)
            assertEquals("$k aspect", e.aspect, t.frontAspect, 0.0006f)
            for (n in 0..3) assertEquals("$k window $n", e.win[n], t.win[n], 0.0006f)
            assertTrue(t.win[0] < t.win[2] && t.win[1] < t.win[3])
            val h = MediaBacks.headerRes(k)
            assertNotNull("$k back header", h)
            assertEquals("$k back", e.back, nameOf(h!!))
            assertTrue("$k back shipped", java.io.File(res, e.back + ".webp").isFile)
        }
        assertEquals(67, keys)
    }

    @Test
    fun printedBacksAreThePacksPlainBases() {
        for (k in listOf("PS2", "PS1", "N64", "SNES", "GameCube", "PSP", "NDS", "3DS", "Switch", "GBA", "Genesis", "Atari2600")) {
            val b = assertNotNull(k, MediaBacks.of(k)).let { MediaBacks.of(k)!! }
            assertTrue("$k no windows", b.wins.isEmpty())
            assertFalse("$k not over art", b.overArt)
            assertEquals(0.125f, b.band!![1], 0.0001f)
            assertEquals(0.91f, b.band!![3], 0.0001f)
        }
        // The collector's backs stay where there was no printed back (their details and rating): only the header comes from the pack.
        for (k in listOf("Steam", "Android", "App", "PS3", "PS4", "Xbox", "Xbox360", "Amiga", "Arcade")) {
            assertNull(k, MediaBacks.of(k)); assertNotNull(k, MediaBacks.headerRes(k))
        }
    }

    @Test
    fun spinesAreUntouched() {
        // (The pack's spines were left out: the shelves keep their spine shapes.)
        assertEquals(0.0809f, MediaCovers.of("PS2")!!.spineAspect, 0.0001f)
        assertEquals(0.0872f, MediaCovers.of("Steam")!!.spineAspect, 0.0001f)
        assertNotNull(MediaCovers.of("Steam")!!.spineRes)
    }

    @Test
    fun fitsKeepHeadersAndStripesWhole() {
        // A header on top: the window changes height (the header is never stretched).
        assertTrue(MediaCovers.of("PS2")!!.fitTo(2f / 3f)!!.vertical)
        assertTrue(MediaCovers.of("Steam")!!.fitTo(2f / 3f)!!.vertical)
        // A stripe down the left (Game Boy, Mega Drive) or right (N64, SNES): the window changes width.
        assertFalse(MediaCovers.of("GB")!!.fitTo(0.7f)!!.vertical)
        assertFalse(MediaCovers.of("Genesis")!!.fitTo(0.5f)!!.vertical)
        assertFalse(MediaCovers.of("N64")!!.fitTo(1.0f)!!.vertical)
        val f = MediaCovers.of("GB")!!.fitTo(0.7f)!!
        // The stripe keeps its width in heights of the front.
        val t = MediaCovers.of("GB")!!
        assertEquals(t.win[0] * t.frontAspect, f.win[0] * f.aspect, 0.002f)
    }

    @Test
    fun scansAreStillCutByTheirOwnBanner() {
        // CartShells cuts a box scan's printed banner by the old window; the new frames' windows don't describe scans.
        val ps2 = MediaCovers.of("PS2")!!
        assertEquals(0.1059f, ps2.scanCut[1], 0.0001f); assertEquals(1f, ps2.scanCut[3], 0.0001f)
        val ps3 = MediaCovers.of("PS3")!!
        assertEquals(ps3.win.toList(), ps3.scanCut.toList())
        // A whole box scan is never framed twice; a 2:3 cover is.
        assertTrue(isBoxFront(ps3, 0.787f)); assertFalse(isBoxFront(ps3, 2f / 3f))
    }
}
