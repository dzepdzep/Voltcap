package com.shelf.launcher.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Update 408: Android `.trashed-*` placeholders are never games; stale index records are dropped; files stay untouched. */
class TrashedRoms408Test {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun nameRules() {
        assertTrue(RomScanner.isTrashedOrHiddenName(".trashed-1792009458-Pokemon Silver Version (GB Compatible).gbc"))
        assertTrue(RomScanner.isTrashedOrHiddenName(".pending-1792009458-Pokemon Silver.gbc"))
        assertTrue(RomScanner.isTrashedOrHiddenName("._Pokemon Silver.gbc"))
        assertFalse(RomScanner.isTrashedOrHiddenName("Pokemon Silver Version (GB Compatible).gbc"))
        assertFalse(RomScanner.isTrashedOrHiddenName("trashed-ok.gbc"))
    }

    @Test fun pathRules() {
        assertTrue(RomScanner.isTrashedPath("/storage/emulated/0/ROMs/gbc/.trashed-1792009458-Pokemon Silver.gbc"))
        assertTrue(RomScanner.isTrashedPath("/roms/.trashed-1-x/Game.gbc"))
        assertFalse(RomScanner.isTrashedPath("/storage/emulated/0/ROMs/gbc/Pokemon Silver.gbc"))
        assertFalse(RomScanner.isTrashedPath(null))
    }

    @Test fun scanSkipsTrashedAndKeepsRealFilesAndSiblings() {
        val dir = File(tmp.root, "gbc").apply { mkdirs() }
        val real = File(dir, "Pokemon Silver Version (GB Compatible).gbc").apply { writeText("a") }
        val trashed = File(dir, ".trashed-1792009458-Pokemon Silver Version (GB Compatible).gbc").apply { writeText("b") }
        val res = RomScanner.scan(tmp.root.path)
        assertEquals(1, res.games.size)
        assertEquals(real.absolutePath, res.games[0].romPath)
        assertTrue("scan must never modify or delete files", trashed.exists() && real.exists())
        assertEquals("b", trashed.readText())
    }

    @Test fun staleIndexRecordIsDroppedOnRead() {
        val good = Game(name = "Pokemon Silver", platform = "GBC", hue = 1, sourceType = SourceType.ROM, romPath = "/r/gbc/Pokemon Silver.gbc")
        val bad = Game(name = ".trashed 1792009458 Pokemon Silver", platform = "GBC", hue = 2, sourceType = SourceType.ROM,
            romPath = "/r/gbc/.trashed-1792009458-Pokemon Silver.gbc")
        val f = tmp.newFile("rom_library.json").apply { writeText(com.google.gson.Gson().toJson(listOf(good, bad))) }
        val out = RomLibraryPrefetch.read(f)
        assertEquals(listOf("Pokemon Silver"), out.map { it.name })
    }
}
