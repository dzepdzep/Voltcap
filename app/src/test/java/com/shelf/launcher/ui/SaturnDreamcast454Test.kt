package com.shelf.launcher.ui

import com.shelf.launcher.data.RomScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 454 (static only — no Saturn / Dreamcast games on the USB devices): their games are recognised, drawn as discs
 * (Dreamcast on its own GD-ROM) and every region has a case.
 */
class SaturnDreamcast454Test {
    @Test fun foldersAreRecognised() {
        assertEquals("Saturn", RomScanner.platformForFolderName("saturn"))
        assertEquals("Dreamcast", RomScanner.platformForFolderName("dreamcast"))
        assertEquals("Dreamcast", RomScanner.platformForFolderName("dc"))
    }

    @Test fun discMediaAndCases() {
        assertEquals(MediaShell.DISC_GD, MediaShell.of("Dreamcast"))
        assertTrue(MediaShell.of("Saturn")!!.isDisc)
        CaseRegion.values().forEach { r ->
            assertNotNull("Saturn $r", CaseCatalog.of("Saturn", r))
            assertNotNull("Dreamcast $r", CaseCatalog.of("Dreamcast", r))
        }
    }
}
