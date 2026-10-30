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

    /** Update 409 (P5): a dot-name that is a real game, or a lookalike, is kept; only the known placeholders go. */
    @Test fun legitimateDotNamesAndVariantsAreKept() {
        assertFalse(RomScanner.isTrashedOrHiddenName(".hack//Infection.iso"))
        assertFalse(RomScanner.isTrashedOrHiddenName(".hack GU Vol 1.iso"))
        assertFalse(RomScanner.isTrashedOrHiddenName("Game (trashed edition).gbc"))
        assertFalse(RomScanner.isTrashedOrHiddenName("pending-ok.gbc"))
        assertFalse(RomScanner.isTrashedOrHiddenName(".trashedgame.gbc"))   // no "-": not Android's pattern
        assertTrue(RomScanner.isTrashedOrHiddenName(".DS_Store"))
        assertFalse(RomScanner.isTrashedPath("/roms/ps2/.hack GU Vol 1.iso"))
        assertFalse(RomScanner.isTrashedPath("/roms/gbc/Pokemon Silver (Rev 1).gbc"))
        val dir = File(tmp.root, "ps2").apply { mkdirs() }
        File(dir, ".hack GU Vol 1.iso").writeText("x")
        File(dir, "Pokemon (Rev A).iso").writeText("y")
        File(dir, ".pending-1-Pokemon (Rev A).iso").writeText("z")
        val names = RomScanner.scan(tmp.root.path).games.map { File(it.romPath!!).name }.sorted()
        assertEquals(listOf(".hack GU Vol 1.iso", "Pokemon (Rev A).iso"), names)
    }
}
