package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 478 (D1): GameNative's frontend `.steam` exports become PC / Steam games during the library scan. */
class GameNative478Test {
    @Test fun anExportHoldsJustTheAppId() {
        assertEquals(620, RomScanner.parseSteamExport("620"))
        assertEquals(620, RomScanner.parseSteamExport("620\n"))
        assertEquals(620, RomScanner.parseSteamExport("﻿ 620 \r\n"))
        assertNull(RomScanner.parseSteamExport(""))
        assertNull(RomScanner.parseSteamExport("0"))
        assertNull(RomScanner.parseSteamExport("-5"))
        assertNull(RomScanner.parseSteamExport("abc"))
        assertNull(RomScanner.parseSteamExport("620 Portal"))
        assertNull(RomScanner.parseSteamExport("99999999999"))
    }

    @Test fun aScanFindsExportsAsSteamGamesOnceAndSkipsBadOnes() {
        val root = kotlin.io.path.createTempDirectory("gn478").toFile()
        try {
            File(root, "PC").mkdirs()
            File(root, "PC/Half-Life 2.steam").writeText("220")
            File(root, "PC/Portal 2.steam").writeText("620")
            File(root, "PC/broken.steam").writeText("not an id")
            File(root, "Copy").mkdirs()
            File(root, "Copy/Portal 2.steam").writeText("620")   // the same AppID exported twice
            val games = RomScanner.scanAll(listOf(root.absolutePath), deep = false).games.filter { it.sourceType == SourceType.STEAM }
            assertEquals(listOf("Half-Life 2", "Portal 2"), games.map { it.name })
            assertTrue(games.all { it.platform == "Steam" && it.romPath == null })
            assertEquals(listOf("steam:220", "steam:620"), games.map { it.stableKey })
        } finally { root.deleteRecursively() }
    }
}
