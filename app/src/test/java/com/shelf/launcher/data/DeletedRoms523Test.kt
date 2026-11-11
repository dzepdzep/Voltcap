package com.shelf.launcher.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Batch 523 (owner: deleted games stayed after a rescan): a playlist / cue sheet left behind without its discs is no game. */
class DeletedRoms523Test {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun playlistWithoutItsDiscsIsNotAGame() {
        val dir = File(tmp.root, "psx").apply { mkdirs() }
        val d1 = File(dir, "Final Fantasy VII (Disc 1).chd").apply { writeText("1") }
        val d2 = File(dir, "Final Fantasy VII (Disc 2).chd").apply { writeText("2") }
        val m3u = File(dir, "Final Fantasy VII.m3u").apply { writeText("${d1.name}\n${d2.name}\n") }
        File(dir, "Crash Bandicoot.chd").writeText("c")
        assertEquals(2, RomScanner.scan(tmp.root.path).games.size)
        d1.delete(); d2.delete()                                   // the owner deletes the game's discs; the playlist stays
        val after = RomScanner.scan(tmp.root.path).games
        assertEquals(listOf("Crash Bandicoot"), after.map { it.name })
        assertTrue("never deleted or changed", m3u.exists() && m3u.readText().contains("Disc 1"))
    }

    @Test fun cueSheetWithoutItsTracksIsNotAGame() {
        val dir = File(tmp.root, "psx").apply { mkdirs() }
        val bin = File(dir, "Spyro.bin").apply { writeText("b") }
        val cue = File(dir, "Spyro.cue").apply { writeText("FILE \"Spyro.bin\" BINARY\n  TRACK 01 MODE2/2352\n") }
        assertEquals(1, RomScanner.scan(tmp.root.path).games.size)
        bin.delete()
        assertEquals(0, RomScanner.scan(tmp.root.path).games.size)
        assertTrue(cue.exists())
        // a sheet that names nothing readable is still listed (nothing to judge it by)
        File(dir, "Odd.cue").writeText("REM nothing\n")
        assertEquals(1, RomScanner.scan(tmp.root.path).games.size)
    }

    @Test fun aPartlyDeletedSetKeepsTheDiscsThatAreThere() {
        val dir = File(tmp.root, "psx").apply { mkdirs() }
        val d1 = File(dir, "MGS (Disc 1).chd").apply { writeText("1") }
        val d2 = File(dir, "MGS (Disc 2).chd").apply { writeText("2") }
        File(dir, "MGS.m3u").writeText("${d1.name}\n${d2.name}\n")
        d2.delete()
        val g = RomScanner.scan(tmp.root.path).games
        assertEquals(1, g.size); assertEquals("MGS", g[0].name)
    }
}
