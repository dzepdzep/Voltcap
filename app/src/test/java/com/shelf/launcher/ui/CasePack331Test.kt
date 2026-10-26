package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Update 334: the matching case pack (update 331) was taken back at Toni's request — the case fronts and backs are the
 * template pack's again. Its pictures that only it used stay in the repository unused (R8's resource shrinking leaves them
 * out of the APK); its manifests stay in tools/media_templates/case_pack_331 for reference.
 */
class CasePack331Test {
    @Test
    fun theOldCasesAreBack() {
        // The PS2 front: its band on top, the art window below it to the foot (the template pack's).
        val ps2 = MediaCovers.of("PS2")!!
        assertEquals(0.1059f, ps2.win[1], 0.0001f)
        assertEquals(1f, ps2.win[3], 0.0001f)
        // Consoles the pack had added are Voltcap's plain clear case again.
        assertNull(MediaCovers.of("PS3"))
        assertNull(MediaCovers.of("Xbox360"))
        assertNotNull(MediaBacks.of("PS2"))
    }
}
