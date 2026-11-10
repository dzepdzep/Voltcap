package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 520: what reaches a 0.6.69 device from a 0.6.67 one (and back) through Drive cover sync / settings sync / exports — only
 * the address handling (SsMedia), with FAKE values. Drive itself is not exercised here (no account is used in tests).
 */
class DriveMix520Test {
    private val dirty = "https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=FAKEDEV1&devpassword=FAKE_PW_1&softname=Voltcap&ssid=p1&sspassword=pw2&systemeid=57&jeuid=19282&media=box-2D(us)&maxwidth=640"
    private val clean = "https://neoclone.screenscraper.fr/api2/mediaJeu.php?systemeid=57&jeuid=19282&media=box-2D(us)&maxwidth=640"
    private val signed = "$clean&vsig=AbC123def456"

    /** 0.6.67 → 0.6.69: an art override as CloudSync writes it (a JSON object per game) arrives cleaned, whatever its form. */
    @Test fun olderDevicesAddressesArriveCleaned() {
        val json = """{"cover":"$dirty","wide":"${dirty.replace("/", "\\/")}"}"""
        val inbound = SsMedia.cleanText(json)
        assertFalse(SsMedia.hasCredentials(inbound))
        assertTrue(inbound.contains(clean))
        // Twice is the same as once (a device that receives its own upload again changes nothing).
        assertEquals(inbound, SsMedia.cleanText(inbound))
    }

    /** 0.6.69 → anything: its own addresses (clean, or signed by the service) pass through unchanged — never re-written. */
    @Test fun newerDevicesAddressesAreStable() {
        assertEquals(clean, SsMedia.clean(clean))
        assertEquals(signed, SsMedia.clean(signed))
        assertEquals("Box\t$signed", SsMedia.cleanText("Box\t$signed"))
    }

    /**
     * What a 0.6.67 device gets from a 0.6.69 one: an address with no key in it. 0.6.67 requests addresses exactly as stored
     * (checked in the v0.6.67 source: no interceptor adds the key), so ScreenScraper sees no developer key: that picture can't
     * load there until the device is updated. Recorded here so the limitation stays visible; nothing in 0.6.69 can change it.
     */
    @Test fun anOlderDeviceGetsAnAddressWithoutAKey() {
        val received = SsMedia.cleanText(dirty)
        assertFalse(received.contains("devid=") || received.contains("devpassword="))
    }

    /** Values that aren't ScreenScraper addresses (metadata text, other art hosts, play time) are never touched. */
    @Test fun otherDataIsUntouched() {
        for (v in listOf("Pokémon Silver — 1999 · Game Freak", "https://cdn2.steamgriddb.com/grid/abc.png", "12345", """{"t":"s","v":"x"}"""))
            assertEquals(v, SsMedia.cleanText(v))
    }
}
