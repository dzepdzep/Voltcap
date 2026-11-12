package com.shelf.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sprint 528: QA covers (art=covers) are matched to the QA library's games by name - letters and digits, any case. */
class QaFixture528Test {
    @Test fun coverFilesMatchGameNames() {
        assertEquals("sonicthehedgehog", QaFixture.coverKey("Sonic The Hedgehog"))
        assertEquals(QaFixture.coverKey("Sonic the Hedgehog (USA, Europe)"), QaFixture.coverKey("Sonic The Hedgehog"))
        assertEquals(QaFixture.coverKey("Super Mario Bros."), QaFixture.coverKey("Super Mario Bros"))
        assertEquals("pokemonsilver", QaFixture.coverKey("Pokemon Silver"))
    }
}
