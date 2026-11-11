package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Batch 522: DroidDeck launch files are validated Steam games; one game added as both a DroidDeck shortcut and a file counts once. */
class GameClients522Test {
    @Test fun droidDeckIds() {
        assertEquals("1245620", GameClients.droidDeckId("1245620"))
        assertEquals("1245620", GameClients.droidDeckId("droiddeck://game/1245620\n"))
        assertEquals("18446744073709551615", GameClients.droidDeckId("18446744073709551615"))
        assertNull(GameClients.droidDeckId(""))
        assertNull(GameClients.droidDeckId("0"))
        assertNull(GameClients.droidDeckId("steam://run/620"))
        assertNull(GameClients.droidDeckId("18446744073709551616")) // over 64 bits
        assertNull(GameClients.droidDeckId("12; rm -rf /"))
    }

    @Test fun aScanListsValidDroidDeckFilesInSteamAndSkipsBadOnes() {
        val root = kotlin.io.path.createTempDirectory("dd522").toFile()
        try {
            File(root, "DroidDeck").mkdirs()
            File(root, "DroidDeck/Elden Ring.droiddeck").writeText("1245620")
            File(root, "DroidDeck/Portal 2.droiddeck").writeText("﻿droiddeck://game/620\n")
            File(root, "DroidDeck/broken.droiddeck").writeText("not an id")
            File(root, "DroidDeck/empty.droiddeck").writeText("")
            val games = RomScanner.scanAll(listOf(root.absolutePath), deep = false).games.filter { it.romPath?.endsWith(".droiddeck") == true }
            assertEquals(listOf("Elden Ring", "Portal 2"), games.map { it.name })
            assertTrue(games.all { it.platform == "Steam" && it.sourceType == SourceType.ROM })
            // the key stays the file (what Play hands back to DroidDeck): the same as before this batch, so play time etc. keep
            assertTrue(games.all { it.stableKey == it.romPath })
        } finally { root.deleteRecursively() }
    }

    @Test fun aDroidDeckShortcutAndItsFileAreOneGame() {
        val file = Game(name = "Elden Ring", platform = "Steam", hue = 0, sourceType = SourceType.ROM, romPath = "/roms/DroidDeck/Elden Ring.droiddeck")
        val other = Game(name = "Hades", platform = "Steam", hue = 0, sourceType = SourceType.ROM, romPath = "/roms/DroidDeck/Hades.droiddeck")
        val rom = Game(name = "Elden Ring", platform = "PS5", hue = 0, sourceType = SourceType.ROM, romPath = "/roms/ps5/Elden Ring.iso")
        val pinned = listOf(
            Game(name = "ELDEN RING", platform = "Steam", hue = 0, sourceType = SourceType.ROM, romPath = "shortcut:${GameClients.DROIDDECK}/game_1245620"),
            Game(name = "Hades", platform = "Other app", hue = 0, sourceType = SourceType.ROM, romPath = "shortcut:com.other.app/hades")
        )
        assertEquals(listOf(other, rom), GameClients.withoutPinnedDroidDeckTwins(listOf(file, other, rom), pinned))
        assertEquals(listOf(file), GameClients.withoutPinnedDroidDeckTwins(listOf(file), emptyList()))
    }
}

/** Batch 522: GameNative's other stores' exports (.epic / .gog / .amazon / .pcgame) - checked against its FrontendSyncManager. */
class GameNativeStores522Test {
    @Test fun eachStoreItsOwnSourceKeyAndCategory() {
        val root = kotlin.io.path.createTempDirectory("gn522").toFile()
        try {
            File(root, "PC").mkdirs()
            File(root, "PC/Alan Wake.epic").writeText("17")
            File(root, "PC/Witcher.gog").writeText("1207664643")
            File(root, "PC/Broken.gog").writeText("0")                  // GameNative writes 0 for a non-numeric GOG id
            File(root, "PC/Quake.amazon").writeText("42")
            File(root, "PC/My Game.pcgame").writeText("9001")
            File(root, "PC/Portal 2.steam").writeText("620")
            File(root, "PC/Same id.epic").writeText("620")              // an Epic id equal to a Steam AppID is another game
            val games = RomScanner.scanAll(listOf(root.absolutePath), deep = false).games.filter { it.sourceType == SourceType.STEAM }
            val by = games.associateBy { it.name }
            assertEquals(6, games.size)
            assertEquals("EPIC", by["Alan Wake"]!!.gameNativeSource); assertEquals(17, by["Alan Wake"]!!.storeGameId)
            assertEquals("Epic", by["Alan Wake"]!!.platform); assertNull(by["Alan Wake"]!!.steamAppId)
            assertEquals("gamenative:gog:1207664643", by["Witcher"]!!.stableKey)
            assertEquals("AMAZON", by["Quake"]!!.gameNativeSource); assertEquals("Amazon", by["Quake"]!!.platform)
            assertEquals("CUSTOM_GAME", by["My Game"]!!.gameNativeSource); assertEquals("PC", by["My Game"]!!.platform)
            assertEquals("steam:620", by["Portal 2"]!!.stableKey)
            assertEquals("gamenative:epic:620", by["Same id"]!!.stableKey)
            assertNull(by["Broken"])
        } finally { root.deleteRecursively() }
    }
}
