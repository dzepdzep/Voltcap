package com.shelf.launcher.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Native runtimes (owner 25 Nov 23:41): Peanuts Native on the existing Switch platform, Banana Native on the existing Switch 2
 * platform, by their documented frontend entries; no duplicate platform, no change to existing game records. Synthetic files only.
 */
class NativeRuntimesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun catalogListsEachRuntimeOnceOnItsExistingPlatform() {
        val switch = EmulatorCatalog.standalone.getValue("Switch")
        val switch2 = EmulatorCatalog.standalone.getValue("Switch2")
        assertEquals(1, switch.count { it.packageName == NativeRuntimes.PEANUTS_PACKAGE })
        assertEquals(1, switch2.count { it.packageName == NativeRuntimes.BANANA_PACKAGE })
        // never on the other console, never twice anywhere
        assertFalse(switch.any { it.packageName == NativeRuntimes.BANANA_PACKAGE })
        assertFalse(switch2.any { it.packageName == NativeRuntimes.PEANUTS_PACKAGE })
        val all = EmulatorCatalog.standalone.values.flatten().map { it.packageName }
        assertEquals(1, all.count { it == NativeRuntimes.PEANUTS_PACKAGE }); assertEquals(1, all.count { it == NativeRuntimes.BANANA_PACKAGE })
        // the platform keys already in use - no new "Switch 2" / "NS2" platform
        assertFalse(EmulatorCatalog.standalone.keys.any { it != "Switch2" && it.replace(" ", "").equals("Switch2", true) })
        assertEquals("Nintendo Switch 2", ConsoleNames.display("Switch2"))
    }

    @Test fun launchProfilesUseTheDocumentedFrontendEntry() {
        val peanuts = assertNotNull1(EmulatorCatalog.profileFor(NativeRuntimes.PEANUTS_PACKAGE))
        assertEquals(listOf("dev.peanuts.launch.LaunchActivity"), peanuts.activities)
        assertEquals("game", peanuts.romExtra); assertEquals(RomFormat.FILE_PATH, peanuts.format); assertFalse(peanuts.alsoAsData)
        val banana = assertNotNull1(EmulatorCatalog.profileFor(NativeRuntimes.BANANA_PACKAGE))
        assertEquals(listOf("dev.bananative.android.Launch"), banana.activities)
        assertEquals("game", banana.romExtra); assertEquals(RomFormat.FILE_PATH, banana.format)
        assertFalse(banana.treeAtGameFolder); assertFalse(peanuts.treeAtGameFolder)
    }

    @Test fun pickerOffersThemForTheirConsoleOnly() {
        assertTrue(EmulatorRegistry.suggestionsFor("Switch").any { it.packageName == NativeRuntimes.PEANUTS_PACKAGE })
        assertTrue(EmulatorRegistry.suggestionsFor("Switch2").any { it.packageName == NativeRuntimes.BANANA_PACKAGE })
        // a Switch 2 console kept under its folder's own spelling still gets Banana Native
        assertTrue(EmulatorRegistry.suggestionsFor("Switch 2").any { it.packageName == NativeRuntimes.BANANA_PACKAGE })
        assertTrue(EmulatorRegistry.suggestionsFor("switch_2").any { it.packageName == NativeRuntimes.BANANA_PACKAGE })
        assertFalse(EmulatorRegistry.suggestionsFor("Switch").any { it.packageName == NativeRuntimes.BANANA_PACKAGE })
        assertFalse(EmulatorRegistry.suggestionsFor("Switch2").any { it.packageName == NativeRuntimes.PEANUTS_PACKAGE })
        assertFalse(EmulatorRegistry.suggestionsFor("PS2").any { NativeRuntimes.isNative(it.packageName) })
    }

    @Test fun romForSendsTheNupBesideAListedDump() {
        val dir = File(tmp.root, "switch").apply { mkdirs() }
        val nsp = File(dir, "Game.nsp").apply { writeText("n") }
        // no .nup: unchanged (the runtime says what it needs)
        assertEquals(nsp.path, NativeRuntimes.romFor(NativeRuntimes.PEANUTS_PACKAGE, nsp.path))
        assertFalse(NativeRuntimes.compatible(nsp.path))
        val nup = File(dir, "Game.nup").apply { writeText("u") }
        assertEquals(nup.path, NativeRuntimes.romFor(NativeRuntimes.PEANUTS_PACKAGE, nsp.path))
        assertEquals(nup.path, NativeRuntimes.romFor(NativeRuntimes.BANANA_PACKAGE, nsp.path))
        assertTrue(NativeRuntimes.compatible(nsp.path))
        // a .nup and a game folder go as they are
        assertEquals(nup.path, NativeRuntimes.romFor(NativeRuntimes.BANANA_PACKAGE, nup.path))
        assertEquals(dir.path, NativeRuntimes.romFor(NativeRuntimes.BANANA_PACKAGE, dir.path))
        // every other emulator: the game's own file, untouched
        assertEquals(nsp.path, NativeRuntimes.romFor("dev.eden.eden_emulator", nsp.path))
        assertEquals(nsp.path, NativeRuntimes.romFor(null, nsp.path))
    }

    @Test fun nupFilesAreGamesOfTheirConsoleFolderAndExistingRecordsStay() {
        val roms = File(tmp.root, "ROMs").apply { mkdirs() }
        File(roms, "switch").mkdirs(); File(roms, "Switch2").mkdirs()
        File(roms, "switch/Kept Game.nsp").writeText("k")
        File(roms, "Switch2/Kart.xci").writeText("x")
        val before = RomScanner.scanAll(listOf(roms.path), false).games
        File(roms, "Switch2/Bananza.nup").writeText("b")
        File(roms, "switch/Native Only.nup").writeText("p")
        val after = RomScanner.scanAll(listOf(roms.path), false).games
        // the games that were there keep their console and file
        before.forEach { g -> assertTrue(g.name, after.any { it.romPath == g.romPath && it.platform == g.platform && it.name == g.name }) }
        val bananza = after.single { it.romPath!!.endsWith("Bananza.nup") }
        assertEquals("Switch2", bananza.platform)
        assertEquals("Switch2", after.single { it.romPath!!.endsWith("Kart.xci") }.platform)
        assertEquals("Switch", after.single { it.romPath!!.endsWith("Native Only.nup") }.platform)
        // no new console appears for them
        assertEquals(before.map { it.platform }.toSet(), after.map { it.platform }.toSet())
    }

    @Test fun aNupBesideTheSameGamesDumpDoesNotReplaceItsRecord() {
        val roms = File(tmp.root, "ROMs").apply { mkdirs() }
        File(roms, "switch").mkdirs()
        File(roms, "switch/Kept Game.nsp").writeText("k")
        val before = RomScanner.scanAll(listOf(roms.path), false).games.single()
        File(roms, "switch/Kept Game.nup").writeText("u")
        val after = RomScanner.scanAll(listOf(roms.path), false).games
        // the .nsp record (its stable key, artwork and settings) is still the listed one
        assertTrue(after.any { it.romPath == before.romPath && it.stableKey == before.stableKey })
        assertEquals(1, after.count { it.name == before.name && it.platform == before.platform })
    }

    private fun <T> assertNotNull1(v: T?): T { assertNotNull(v); return v!! }
}
