package com.shelf.launcher.ui

import com.shelf.launcher.data.AgeRating
import com.shelf.launcher.data.ArtProvenance
import com.shelf.launcher.data.PackagingFactsStore
import com.shelf.launcher.data.SteamStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 305: verified packaging details (ratings, Steam's store record), picture provenance, fit checks. */
class Packaging305Test {
    @Test fun ratingsAreOnlyRealOnes() {
        assertEquals("USK 16", PackagingFactsStore.rating("usk", "16", "Steam")!!.label)
        assertEquals("PEGI 12", PackagingFactsStore.rating("PEGI", "12+", "ScreenScraper")!!.label)
        assertEquals("ESRB E10+", PackagingFactsStore.rating("esrb", "e10", "Steam")!!.label)
        assertEquals("ESRB M", PackagingFactsStore.rating("ESRB", "m", "Steam")!!.label)
        // Not a rating body's rating, or not one of its real values: nothing.
        assertNull(PackagingFactsStore.rating("steam_germany", "16", "Steam"))
        assertNull(PackagingFactsStore.rating("SS", "12", "ScreenScraper"))
        assertNull(PackagingFactsStore.rating("USK", "15", "Steam"))
        assertNull(PackagingFactsStore.rating("PEGI", "16 Jahre", "Steam"))
        assertNull(PackagingFactsStore.rating("ESRB", "Mature 17+", "Steam"))
        assertNull(PackagingFactsStore.rating("USK", "", "Steam"))
    }

    @Test fun pickPrefersTheChosenBody() {
        val list = listOf(AgeRating("ESRB", "M", "Steam"), AgeRating("PEGI", "18", "Steam"), AgeRating("USK", "18", "Steam"))
        assertEquals("USK", PackagingFactsStore.pick(list, "USK", true)!!.system)
        assertEquals("ESRB", PackagingFactsStore.pick(list, "ESRB", false)!!.system)
        val noUsk = list.filter { it.system != "USK" }
        assertEquals("PEGI", PackagingFactsStore.pick(noUsk, "USK", true)!!.system)
        assertNull(PackagingFactsStore.pick(noUsk, "USK", false))
        assertNull(PackagingFactsStore.pick(list, null, true)) // ratings off
        assertNull(PackagingFactsStore.pick(emptyList(), "USK", true))
    }

    @Test fun steamStoreRecordIsRead() {
        val body = """{"1091500":{"success":true,"data":{"name":"Cyberpunk 2077",
            "short_description":"Cyberpunk 2077 is an open-world, action-adventure RPG &amp; more.<br>Set in Night City.",
            "developers":["CD PROJEKT RED"],"publishers":["CD PROJEKT RED"],
            "release_date":{"coming_soon":false,"date":"9 Dec, 2020"},
            "categories":[{"id":2,"description":"Single-player"},{"id":22,"description":"Steam Achievements"},{"id":28,"description":"Full controller support"}],
            "screenshots":[{"id":0,"path_thumbnail":"https://x/t0.jpg","path_full":"https://x/f0.jpg"}],
            "ratings":{"usk":{"rating":"18","use_age_gate":"true","required_age":"18"},
                       "esrb":{"rating":"m","descriptors":"Blood and Gore"},
                       "steam_germany":{"rating_generated":"1","rating":"18","banned":"0"}}}}}"""
        val f = SteamStore.parse(1091500, body)!!
        assertEquals("CD PROJEKT RED", f.publisher); assertEquals(2020, f.year)
        assertEquals(listOf("Single-player", "Full controller support"), f.features)
        assertEquals(listOf("https://x/f0.jpg"), f.shots)
        assertEquals(setOf("USK 18", "ESRB M"), f.ratings.map { it.label }.toSet())
        assertEquals("Cyberpunk 2077 is an open-world, action-adventure RPG & more. Set in Night City.", f.description)
        assertNull(SteamStore.parse(1091500, """{"1091500":{"success":false}}"""))
        assertNull(SteamStore.parse(5, "not json"))
    }

    @Test fun provenanceRoundTrips() {
        val i = ArtProvenance.Info("The Cover Project", ArtProvenance.Kind.CUSTOM, "Some\tone", "Nintendo Switch case")
        val back = ArtProvenance.decode(ArtProvenance.encode(i))!!
        assertEquals("The Cover Project", back.source); assertEquals(ArtProvenance.Kind.CUSTOM, back.kind)
        assertEquals("Some one", back.credit); assertEquals("Nintendo Switch case", back.madeFor)
        assertTrue(back.summary.contains("custom design"))
        assertNull(ArtProvenance.decode("x"))
    }

    @Test fun discLabelFitIsChecked() {
        val g = com.shelf.launcher.data.Game("Halo 3", "Xbox360", 0, com.shelf.launcher.data.SourceType.ROM, "/x/Halo 3.iso")
        assertNull(ArtFit.warning("Disc label", 1000, 1000, g))
        assertNotNull(ArtFit.warning("Disc label", 1600, 900, g))
        val n64 = com.shelf.launcher.data.Game("Mario", "N64", 0, com.shelf.launcher.data.SourceType.ROM, "/x/Mario.z64")
        val (want, _) = ArtFit.expected("Disc label", n64)!!
        assertTrue(want in 0.5f..3f) // (the N64 label: the drawn shell's 57 × 63.5 mm, or the photo shell's window)
    }

    @Test fun wrapBleedIsCarried() {
        val c = com.shelf.launcher.data.FullScanCuts
        assertEquals(0.4f, c.parse("0.4000,0.5000,0.0200")!!.first, 0.0001f)
        assertEquals(0.02f, c.parseBleed("0.4000,0.5000,0.0200"), 0.0001f)
        assertEquals(0f, c.parseBleed("0.4000,0.5000"), 0.0001f)
        assertEquals(0f, c.parseBleed("0.4,0.5,0.5"), 0.0001f) // (more than 12 %: not a bleed)
        assertEquals("0.4000,0.5000", c.encode(0.4f, 0.5f))
        val url = "file:///data/user_art/wrap305.png"
        c.set(url, 0.45f to 0.55f, 0.015f)
        try {
            val m = BoxTexture.model(url, BoxPart.FRONT, "Switch")
            assertTrue(m.startsWith("voltcrop:front@0.4500,0.5500,0.0150:"))
            assertEquals(0.015f, BoxTexture.bleedOf(m), 0.0001f)
            assertEquals(0.45f, BoxTexture.cutOf(m)!!.first, 0.0001f)
        } finally { c.set(url, null) }
        assertEquals(0f, BoxTexture.bleedOf(BoxTexture.model(url, BoxPart.FRONT, "Switch")), 0.0001f)
    }

    @Test fun yourLabelReplacesTheScrapedPhoto() {
        val key = "/test/Mario 305 (USA).z64"
        com.shelf.launcher.data.PhysicalOverrides.set(key, "Disc label", "file:///mine/label.png")
        try {
            val scraped = listOf("Box" to "https://s/box.png", "Disc / cartridge" to "https://s/cart.png", "Disc label" to "https://s/label.png")
            val m = com.shelf.launcher.data.PhysicalOverrides.apply(key, scraped).toMap()
            assertEquals("file:///mine/label.png", m["Disc label"])
            assertNull(m["Disc / cartridge"]) // (the drawn cartridge wears your label)
            assertEquals("https://s/box.png", m["Box"])
            // A photo you picked yourself stays.
            com.shelf.launcher.data.PhysicalOverrides.set(key, "Disc / cartridge", "file:///mine/cart.png")
            assertEquals("file:///mine/cart.png", com.shelf.launcher.data.PhysicalOverrides.apply(key, scraped).toMap()["Disc / cartridge"])
        } finally { com.shelf.launcher.data.PhysicalOverrides.clear(key) }
    }
}
