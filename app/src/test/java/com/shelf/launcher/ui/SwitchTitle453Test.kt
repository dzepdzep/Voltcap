package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Update 453 (Thor, Case stacks): a Switch dump's tags were shown as part of its name. */
class SwitchTitle453Test {
    @Test fun switchDumpTagsAreNotPartOfTheName() {
        assertEquals("Donkey Kong Country Returns HD", stripRegionTags("Donkey Kong Country Returns HD [01009D901BC56000][Base]"))
        assertEquals("Fire Emblem Three Houses", stripRegionTags("Fire Emblem Three Houses [010055D009F78000][v0]"))
    }

    @Test fun otherTagsStayAsBefore() {
        assertEquals("Final Fantasy VII (Disc 2)", stripRegionTags("Final Fantasy VII (Disc 2) (USA)"))
        assertEquals("Super Mario World", stripRegionTags("Super Mario World (USA) [!]"))
        assertEquals("Game (Base)", stripRegionTags("Game (Base)"))                 // round brackets: not a dump tag
        assertEquals("Game [0100ABC]", stripRegionTags("Game [0100ABC]"))           // not a 16-digit title ID
    }
}
