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
}
