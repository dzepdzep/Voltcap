package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Update 460b (D173): two copies of one game file (internal storage / SD card) share their saved scans — nothing else does. */
class GameCopies460Test {
    private val sd = "/storage/3361-6133/ROMs/n64/Pokemon Snap (USA).z64"
    private val internal = "/storage/emulated/0/ROMs/n64/Pokemon Snap (USA).z64"

    @Test fun sameFileOnAnotherStorage() {
        assertEquals(GameCopies.copyKey(sd), GameCopies.copyKey(internal))
        assertEquals(sd, GameCopies.siblingOf(internal, listOf(sd, "/storage/3361-6133/ROMs/n64/Pokemon Stadium (USA).z64")))
    }

    @Test fun neverAnotherGameRegionOrHack() {
        val keys = listOf(
            "/storage/3361-6133/ROMs/n64/Pokemon Snap (Europe).z64",
            "/storage/3361-6133/ROMs/n64/Pokemon Snap - Custom Levels (Basement) (Hack).z64",
            "/storage/3361-6133/ROMs/snes/Pokemon Snap (USA).z64"
        )
        assertNull(GameCopies.siblingOf(internal, keys))
        assertNull(GameCopies.siblingOf(internal, listOf(internal)))
        assertNull(GameCopies.copyKey("com.example.app"))
    }

    @Test fun wholePathBelowTheStorage() {
        // two games' discs with the same folder and file name never match
        assertNull(GameCopies.siblingOf("/storage/emulated/0/ROMs/psx/Final Fantasy VII/CD1/game.chd",
            listOf("/storage/3361-6133/ROMs/psx/Final Fantasy IX/CD1/game.chd")))
        // another folder layout on the card: not treated as the same file
        assertNull(GameCopies.siblingOf(internal, listOf("/storage/3361-6133/Games/n64/Pokemon Snap (USA).z64")))
        // USB drive / sdcard alias are storages too
        assertEquals(GameCopies.copyKey(internal), GameCopies.copyKey("/sdcard/ROMs/n64/Pokemon Snap (USA).z64"))
        assertEquals(GameCopies.copyKey(internal), GameCopies.copyKey("/mnt/media_rw/ABCD-1234/ROMs/n64/Pokemon Snap (USA).z64"))
        // revision tags differ in the name
        assertNull(GameCopies.siblingOf("/storage/emulated/0/ROMs/n64/Pokemon Stadium (USA) (Rev 1).z64",
            listOf("/storage/3361-6133/ROMs/n64/Pokemon Stadium (USA).z64")))
        // not on a known storage: never shares
        assertNull(GameCopies.copyKey("/data/local/tmp/Pokemon Snap (USA).z64"))
    }

    @Test fun differentDumpSizeNeverShares() {
        org.junit.Assert.assertFalse(GameCopies.sameSize(16_777_216L, 33_554_432L))
        org.junit.Assert.assertTrue(GameCopies.sameSize(16_777_216L, 16_777_216L))
        org.junit.Assert.assertTrue(GameCopies.sameSize(0L, 16_777_216L))
    }

    @Test fun yourPickForTheCopyStillWins() {
        val vm = java.io.File(listOf("../src_copy", "src/main/java/com/shelf/launcher", "app/src/main/java/com/shelf/launcher").first { java.io.File(it).exists() }, "LauncherViewModel.kt").readText()
        val f = vm.substring(vm.indexOf("fun shareScansAcrossCopies"))
        assert(f.contains("PhysicalOverrides.apply(k, raw)")) // this copy's own picks applied on top
        assert(f.contains("if (physicalRaw.containsKey(k) || now[k] != null) continue")) // a copy with its own scans keeps them
        assert(f.contains("GameCopies.sameSize("))
    }
}
