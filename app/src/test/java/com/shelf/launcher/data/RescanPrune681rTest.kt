package com.shelf.launcher.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 681r (owner, 17 Nov): "a console folder with only ONE game - I delete it, Rescan library, and it is still listed; the
 * category stays." Synthetic files in a temporary folder only. The library is what [RomReconcile.apply] makes of each scan,
 * started from the list before it (as LauncherViewModel.scanAllFolders does); "restart" = the list saved as JSON and read back.
 */
class RescanPrune681rTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun scanInto(prev: List<Game>, roots: List<String>, explicit: Boolean = true): List<Game> =
        RomReconcile.apply(prev, RomScanner.scanAll(roots, false), roots, explicit) ?: prev
    private fun platforms(games: List<Game>) = games.map { it.platform }.distinct().sorted()
    private fun restart(games: List<Game>): List<Game> {
        val json = com.google.gson.Gson().toJson(games)
        return com.google.gson.Gson().fromJson(json, Array<Game>::class.java).toList()
    }

    private fun library(): Pair<File, File> {
        val roms = File(tmp.root, "ROMs").apply { mkdirs() }
        File(roms, "psx").mkdirs(); File(roms, "xbox360").mkdirs()
        File(roms, "psx/Crash Bandicoot.chd").writeText("c")
        File(roms, "psx/Spyro.chd").writeText("s")
        val halo = File(roms, "xbox360/Halo 3.iso").apply { writeText("h") }
        return roms to halo
    }

    @Test fun deletingTheOnlyGameOfAConsoleRemovesItAndItsCategory() {
        val (roms, halo) = library()
        var lib = scanInto(emptyList(), listOf(roms.path))
        assertTrue("Xbox360" in platforms(lib)); assertEquals(2, platforms(lib).size)
        halo.delete()
        lib = scanInto(lib, listOf(roms.path))
        assertFalse(lib.any { it.name.contains("Halo") })
        assertFalse("the console with no games is no category", "Xbox360" in platforms(lib))
        assertEquals(2, lib.size)
        // rescan twice: still gone, nothing else lost
        lib = scanInto(lib, listOf(roms.path)); assertEquals(2, lib.size)
        // restart: the saved list is the corrected one
        assertEquals(lib.map { it.stableKey }.toSet(), restart(lib).map { it.stableKey }.toSet())
        // the selected console falls back to All; a collection or the Android list is never dropped by this
        assertNull(RomReconcile.platformFilterAfter("Xbox360", platforms(lib), isCollection = false, isAndroidGames = false))
        val ps = platforms(lib).single()
        assertEquals(ps, RomReconcile.platformFilterAfter(ps, platforms(lib), false, false))
        assertEquals("c:Mine", RomReconcile.platformFilterAfter("c:Mine", platforms(lib), isCollection = true, isAndroidGames = false))
    }

    @Test fun theOnlyGameOfTheOnlyConsoleFolderAsItsOwnRoot() {
        // the console folder is itself a ROM folder, and its one game the whole library
        val x = File(tmp.root, "xbox360").apply { mkdirs() }
        val halo = File(x, "Halo 3.iso").apply { writeText("h") }
        var lib = scanInto(emptyList(), listOf(x.path)); assertEquals(1, lib.size)
        halo.delete()
        lib = scanInto(lib, listOf(x.path), explicit = true)
        assertTrue("an explicit rescan that read the folder empties it", lib.isEmpty())
        // an automatic scan finding nothing is no verdict (storage not ready at boot)
        halo.writeText("h"); val back = scanInto(emptyList(), listOf(x.path)); halo.delete()
        assertEquals(back, scanInto(back, listOf(x.path), explicit = false))
    }

    @Test fun deletingOneOfSeveralGamesInAFolder() {
        val (roms, _) = library()
        var lib = scanInto(emptyList(), listOf(roms.path))
        File(roms, "psx/Spyro.chd").delete()
        lib = scanInto(lib, listOf(roms.path))
        assertEquals(setOf("Crash Bandicoot", "Halo 3"), lib.map { it.name }.toSet())
    }

    @Test fun deletingAWholeGameFolderAndAWholeConsoleFolder() {
        val (roms, _) = library()
        val ps3 = File(roms, "ps3/Demon's Souls [BLUS30443]").apply { mkdirs() }
        File(ps3, "PS3_GAME").mkdirs(); File(ps3, "PS3_DISC.SFB").writeText("d")
        var lib = scanInto(emptyList(), listOf(roms.path))
        assertTrue(lib.any { it.platform == "PS3" })
        ps3.deleteRecursively()
        lib = scanInto(lib, listOf(roms.path))
        assertFalse(lib.any { it.platform == "PS3" })
        // the console folder that was a ROM folder of its own, deleted with everything in it: its games are gone
        val x = File(roms, "xbox360")
        lib = scanInto(scanInto(emptyList(), listOf(roms.path + "/psx", x.path)), listOf(roms.path + "/psx", x.path))
        assertEquals(3, lib.size)
        x.deleteRecursively()
        lib = scanInto(lib, listOf(roms.path + "/psx", x.path))
        assertEquals(setOf("Crash Bandicoot", "Spyro"), lib.map { it.name }.toSet())
    }

    @Test fun anUnreadableCardOrAMissingVolumeNeverDeletesItsGames() {
        val (roms, _) = library()
        // a second ROM folder on a card that is not there: its storage (the mount point) holds nothing of its own
        val card = "/storage/0000-NOTMOUNTED/ROMs"
        val cardGame = Game("Gran Turismo 4", "PS2", 1, SourceType.ROM, romPath = "$card/ps2/Gran Turismo 4.iso")
        var lib = scanInto(emptyList(), listOf(roms.path)) + cardGame
        assertEquals(RomScanner.RootState.UNREADABLE, RomScanner.rootState(File(card)))
        lib = scanInto(lib, listOf(roms.path, card))
        assertTrue("the card's game stays while the card is out", lib.any { it.stableKey == cardGame.stableKey })
        // every folder unreadable: no verdict at all
        assertNull(RomReconcile.apply(lib, RomScanner.ScanResult(emptyList(), emptySet(), unreadable = listOf(card)), listOf(card), explicit = true))
    }

    @Test fun aFolderTheWalkCouldNotOpenKeepsWhatWasListedUnderIt() {
        val (roms, halo) = library()
        val lib = scanInto(emptyList(), listOf(roms.path))
        // (a permission taken back on one console folder: the scan reports it as failed and finds nothing under it)
        val failed = RomScanner.ScanResult(lib.filter { it.platform != "Xbox360" }, emptySet(), failedDirs = listOf(halo.parentFile!!.path))
        val after = RomReconcile.apply(lib, failed, listOf(roms.path), explicit = true)!!
        assertTrue(after.any { it.name == "Halo 3" })
        assertEquals(3, after.size)
    }

    @Test fun reAddingTheGameBringsBackTheSameIdentity() {
        val (roms, halo) = library()
        var lib = scanInto(emptyList(), listOf(roms.path))
        val key = lib.first { it.name == "Halo 3" }.stableKey
        halo.delete(); lib = scanInto(lib, listOf(roms.path))
        halo.writeText("h"); lib = scanInto(lib, listOf(roms.path))
        // saves, art, metadata, favourites, history are keyed by this: the same file brings them all back
        assertEquals(key, lib.first { it.name == "Halo 3" }.stableKey)
    }

    @Test fun aRootThatWasDeletedOnReadableStorageIsGoneNotUnreadable() {
        val gone = File(tmp.root, "ROMs/xbox360")
        assertEquals(RomScanner.RootState.GONE, RomScanner.rootState(gone))
        assertEquals(RomScanner.RootState.UNREADABLE, RomScanner.rootState(File("/storage/ABCD-1234/ROMs")))
        assertEquals(RomScanner.RootState.OK, RomScanner.rootState(tmp.root))
    }

    @Test fun yourOwnCollectionSurvivesEmptyAndGetsItsGameBack() {
        // (owner 18 Nov: "preserve empty manually created collections") - the collection's members live in its own store, keyed
        // by the game's path; a rescan never writes that store.
        val (roms, halo) = library()
        var lib = scanInto(emptyList(), listOf(roms.path))
        val key = lib.first { it.name == "Halo 3" }.stableKey
        val mine = mapOf("Mine" to setOf(key))
        fun col(games: List<Game>) = buildCollections(games, mine, emptyMap(), userOn = true, franchisesOn = false, statusOn = false)
            .single { it.key == "col:Mine" }
        assertEquals(setOf(key), col(lib).keys)
        halo.delete(); lib = scanInto(lib, listOf(roms.path))
        assertTrue("the emptied collection is still there", col(lib).keys.isEmpty())
        assertEquals("its membership is untouched", setOf(key), mine.getValue("Mine"))
        halo.writeText("h"); lib = scanInto(lib, listOf(roms.path))
        assertEquals("re-added: the game is in it again", setOf(key), col(lib).keys)
        // and a selected collection is never dropped by the empty-console rule
        assertEquals("col:Mine", RomReconcile.platformFilterAfter("col:Mine", emptyList(), isCollection = true, isAndroidGames = false))
    }
}
