package com.shelf.launcher.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 517 (security): ScreenScraper addresses are stored / exported without credentials; the key is only added to download.
 * The addresses below have the exact shape ScreenScraper answered with on the RP Classic (0.6.68 QA app); the values are FAKE.
 */
class SsMedia517Test {
    private val dev = "FAKEDEV1"; private val pw = "FAKE_PW_123"; private val user = "player1"; private val upw = "userpw!9"
    private fun ss(file: String, media: String, ssid: String = "", sspw: String = "") =
        "https://neoclone.screenscraper.fr/api2/$file?devid=$dev&devpassword=$pw&softname=Voltcap&ssid=$ssid&sspassword=$sspw&systemeid=4&jeuid=1&media=$media"
    private val box = ss("mediaJeu.php", "box-2D(us)") + "&maxwidth=600"
    private val boxClean = "https://neoclone.screenscraper.fr/api2/mediaJeu.php?systemeid=4&jeuid=1&media=box-2D(us)&maxwidth=600"

    @Test fun cleanRemovesEveryCredentialAndKeepsTheRest() {
        assertEquals(boxClean, SsMedia.clean(box))
        assertEquals(boxClean, SsMedia.clean(SsMedia.clean(box)))
        val withUser = ss("mediaManuelJeu.php", "manuel(us)", user, upw)
        val c = SsMedia.clean(withUser)
        listOf(dev, pw, upw, "devid", "devpassword", "ssid", "sspassword", "softname").forEach { assertFalse(it, c.contains(it)) }
        assertTrue(c.contains("media=manuel(us)") && c.contains("jeuid=1"))
    }

    @Test fun otherAddressesAreUntouched() {
        val other = "https://example.com/a.png?devpassword=keep&x=1"
        assertEquals(other, SsMedia.clean(other))
        assertEquals("not an address", SsMedia.clean("not an address"))
    }

    @Test fun textForms_tabLists_gsonJson_escapedSlashes() {
        // physical_media: "label\taddress" lines.
        val list = "Box\t$box\nBack of the box\t${ss("mediaJeu.php", "box-2D-back(us)")}"
        val cleanList = SsMedia.cleanText(list)
        assertFalse(SsMedia.hasCredentials(cleanList)); assertTrue(SsMedia.hasCredentials(list))
        assertTrue(cleanList.startsWith("Box\t$boxClean\nBack of the box\t"))
        // art_cache: Gson writes "=" and "&" as = / &.
        val gson = com.google.gson.Gson()
        val json = gson.toJson(GameArt(gridUrl = box, heroUrl = ss("mediaJeu.php", "fanart")))
        assertTrue(json.contains("\\u003d")); assertTrue(SsMedia.hasCredentials(json))
        val cleaned = SsMedia.cleanText(json)
        assertFalse(SsMedia.hasCredentials(cleaned)); assertFalse(cleaned.contains(pw)); assertFalse(cleaned.contains(dev))
        val back = gson.fromJson(cleaned, GameArt::class.java)
        assertEquals(boxClean, back.gridUrl)
        assertEquals(SsMedia.clean(ss("mediaJeu.php", "fanart")), back.heroUrl)
        // JSON with escaped slashes (ScreenScraper's own answers, org.json output).
        val esc = "{\"url\":\"${box.replace("/", "\\/")}\"}"
        val e2 = SsMedia.cleanText(esc)
        assertFalse(e2.contains(pw)); assertEquals(boxClean, com.google.gson.JsonParser.parseString(e2).asJsonObject.get("url").asString)
    }

    @Test fun downloadsGetYourOwnKeyOnlyOnTheWayOut() {
        val mine = ScreenScraperCredentials("mydev", "mydevpw", "me", "mypw")
        val r = SsMedia.resolve(boxClean.toHttpUrl(), mine)
        assertEquals("mydev", r.queryParameter("devid")); assertEquals("mydevpw", r.queryParameter("devpassword"))
        assertEquals("me", r.queryParameter("ssid")); assertEquals("mypw", r.queryParameter("sspassword"))
        assertEquals("box-2D(us)", r.queryParameter("media"))
        // An old stored address with the leaked key: the leaked values never go out, yours replace them.
        val r2 = SsMedia.resolve(box.toHttpUrl(), mine)
        assertFalse(r2.toString().contains(pw)); assertFalse(r2.toString().contains(dev))
        // Not a media download: unchanged.
        val api = "https://api.screenscraper.fr/api2/jeuInfos.php?systemeid=4".toHttpUrl()
        assertEquals(api, SsMedia.resolve(api, mine))
        // Batch 518: the service's media signature is kept when stored, and dropped for a direct download with your key.
        val signed = "$boxClean&vsig=AbC_-123"
        assertEquals(signed, SsMedia.clean(signed))
        assertEquals(null, SsMedia.resolve(signed.toHttpUrl(), mine).queryParameter("vsig"))
        // Nobody signed in: the clean address (no key from anywhere).
        assertEquals(boxClean, SsMedia.resolve(box.toHttpUrl(), null).toString())
    }

    @Test fun serviceMarkerOnlyWhenTheBuildHasTheService() {
        assertEquals(VoltcapService.configured, ScreenScraperDevKey.builtIn != null)
        assertEquals(VoltcapService.configured, IgdbBuiltInKey.builtIn != null)
        ScreenScraperDevKey.builtIn?.let { assertEquals(VoltcapService.SERVICE_ID, it.first) }
    }
}
