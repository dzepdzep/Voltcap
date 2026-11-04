package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Update 453 (P5): ROMs in recycle / trash / recovery folders are never games; real ROMs beside them are kept, nothing is touched. */
class TrashFolders453Test {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun trashFolderNames() {
        listOf("\$RECYCLE.BIN", "\$Recycle.Bin", "RECYCLER", "System Volume Information", "LOST.DIR", ".Trash", ".Trashes", ".Trash-1000")
            .forEach { assertTrue(it, RomScanner.isTrashFolder(it)) }
        listOf("gba", "Recycle", "Recycler", "lost", "Trash", ".hack", "Trash Panic", "LOST", ".Trash-x")
            .forEach { assertFalse(it, RomScanner.isTrashFolder(it)) }
    }

    @Test fun scanSkipsRecycleBinKeepsRealRoms() {
        val gba = File(tmp.root, "gba").apply { mkdirs() }
        val real = File(gba, "Advance Wars.gba").apply { writeText("a") }
        val bin = File(tmp.root, "\$RECYCLE.BIN/S-1-5-21-1").apply { mkdirs() }
        val deleted = File(bin, "\$RA1B2C3.gba").apply { writeText("b") }
        val lost = File(tmp.root, "LOST.DIR").apply { mkdirs() }
        File(lost, "1234.gba").writeText("c")
        val res = RomScanner.scan(tmp.root.path)
        assertEquals(listOf(real.absolutePath), res.games.map { it.romPath })
        assertTrue("never touched", deleted.exists() && deleted.readText() == "b")
    }

    /** Old code: a record inside \$RECYCLE.BIN (indexed before this update) stayed in the library. */
    @Test fun staleRecordInRecycleBinIsDropped() {
        assertTrue(RomScanner.isTrashedPath("/storage/1234-ABCD/\$RECYCLE.BIN/S-1-5/\$RA1B2C3.gba"))
        assertTrue(RomScanner.isTrashedPath("/storage/1234-ABCD/.Trash-1000/files/Advance Wars.gba"))
        assertFalse(RomScanner.isTrashedPath("/storage/1234-ABCD/ROMs/gba/Advance Wars.gba"))
        assertFalse(RomScanner.isTrashedPath("/storage/1234-ABCD/ROMs/ps2/.hack GU Vol 1.iso"))
        assertFalse(RomScanner.isTrashedPath("/roms/Trash Panic/Trash Panic.iso"))
    }
}
