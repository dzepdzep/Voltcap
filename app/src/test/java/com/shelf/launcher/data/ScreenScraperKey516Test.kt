package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Update 516: users give only their ScreenScraper login; the developer key is Voltcap's own (built in). */
class ScreenScraperKey516Test {
    private val builtIn = "voltcapDev" to "secret"

    @Test fun blankSavedKeyUsesTheBuiltInOne() {
        assertEquals(builtIn, ScreenScraperDevKey.resolve("", "", builtIn))
        assertEquals(builtIn, ScreenScraperDevKey.resolve(null, null, builtIn))
        assertEquals(builtIn, ScreenScraperDevKey.resolve("  ", "x", builtIn))
    }

    @Test fun aKeySomeoneTypedStillWins() {
        assertEquals("mine" to "pw", ScreenScraperDevKey.resolve(" mine ", "pw", builtIn))
    }

    @Test fun noKeyAnywhereMeansNotUsable() {
        assertNull(ScreenScraperDevKey.resolve("", "", null))
        assertNull(ScreenScraperDevKey.of("id", ""))
        assertNull(ScreenScraperDevKey.of(null, "pw"))
    }
}

/** Update 516: IGDB uses Voltcap's own Twitch app when nobody typed a key. */
class IgdbKey516Test {
    private val builtIn = IgdbCredentials("voltcapClient", "secret")

    @Test fun blankSavedKeyUsesTheBuiltInOne() {
        assertEquals(builtIn, IgdbBuiltInKey.resolve("", "", builtIn))
        assertEquals(builtIn, IgdbBuiltInKey.resolve("", null, builtIn))
    }

    @Test fun aKeySomeoneTypedStillWins() {
        assertEquals(IgdbCredentials("mine", "pw"), IgdbBuiltInKey.resolve("mine", " pw ", builtIn))
    }

    @Test fun noKeyAnywhereMeansOff() {
        assertNull(IgdbBuiltInKey.resolve("", "", null))
    }
}
