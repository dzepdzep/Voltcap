package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #79: RomHacks — fan-hack detection from library names and file paths, positives and negatives. */
class RomHacksTest {

    // ---- positives ----

    @Test
    fun knownPokemonHackWithTagIsDistinctive() {
        val h = RomHacks.fanHack("Pokemon Radical Red (Hack)", null)
        assertNotNull(h)
        assertEquals("Pokemon Radical Red", h!!.ownTitle)
        assertTrue(h.distinctive)
        assertEquals("Pokemon Radical Red", RomHacks.artSearchName("Pokemon Radical Red (Hack)"))
    }

    @Test
    fun tildeHackTagIsAHack() {
        val h = RomHacks.fanHack("Super Mario World ~Hack~", null)
        assertNotNull(h)
        assertEquals("Super Mario World", h!!.ownTitle)
        assertFalse(h.distinctive)
    }

    @Test
    fun squareBracketHackTagIsAHack() {
        val h = RomHacks.fanHack("Super Mario World [Hack]", null)
        assertNotNull(h)
        assertEquals("Super Mario World", h!!.ownTitle)
        assertFalse(h.distinctive)
    }

    @Test
    fun tagOnlyHackHasNoArtSearchName() {
        val name = "Mario's Picross X (Hack)"
        val h = RomHacks.fanHack(name, null)
        assertNotNull(h)
        assertEquals("Mario's Picross X", h!!.ownTitle)
        assertFalse(h.distinctive)
        // Searching by the base title would find the base game: the placeholder is used instead.
        assertNull(RomHacks.artSearchName(name))
    }

    @Test
    fun hackFoundFromPathAlone() {
        val h = RomHacks.fanHack(null, "/storage/roms/gba/Pokemon_-_Radical_Red_v4.1_(Hack)_(USA).gba")
        assertNotNull(h)
        assertEquals("Pokemon - Radical Red", h!!.ownTitle)
        assertTrue(h.distinctive)
    }

    @Test
    fun windowsSeparatorsInPath() {
        assertNotNull(RomHacks.fanHack(null, "C:\\roms\\snes\\Super Mario World [Hack].sfc"))
    }

    @Test
    fun kaizoWordInTitleIsDistinctiveEvenWithTildeTag() {
        val h = RomHacks.fanHack("~Hack~ Kaizo Mario World (Japan)", null)
        assertNotNull(h)
        assertEquals("Kaizo Mario World", h!!.ownTitle)
        assertTrue(h.distinctive)
    }

    @Test
    fun hackByAuthorAndRandomizerTags() {
        assertNotNull(RomHacks.fanHack("Super Metroid [Hack by Drewseph]", null))
        assertNotNull(RomHacks.fanHack("Zelda - A Link to the Past (Randomizer)", null))
        assertNotNull(RomHacks.fanHack("Pokemon FireRed (Nuzlocke)", null))
    }

    @Test
    fun ownTitleDropsVersionAndTags() {
        assertEquals("Pokemon - Radical Red", RomHacks.ownTitle("Pokemon_-_Radical_Red_v4.1_(Hack)_(USA)"))
        assertEquals("Pokemon Unbound", RomHacks.ownTitle("Pokemon Unbound Ver 2.1.1 (Hack)"))
    }

    @Test
    fun ownTitleNeverBlank() {
        assertEquals("(Hack)", RomHacks.ownTitle("(Hack)"))
    }

    @Test
    fun namesTheHackOnlyForDistinctiveHacks() {
        val distinctive = RomHacks.fanHack("Pokemon Radical Red (Hack)", null)!!
        assertTrue(RomHacks.namesTheHack(distinctive, listOf("Pokemon Radical Red")))
        assertFalse(RomHacks.namesTheHack(distinctive, listOf("Pokemon FireRed Version")))
        val tagOnly = RomHacks.fanHack("Pokemon Emerald (Hack)", null)!!
        assertFalse(RomHacks.namesTheHack(tagOnly, listOf("Pokemon Emerald")))
    }

    @Test
    fun goodToolsHackedDumpIsModifiedButNotAFanHack() {
        val name = "Super Mario Bros. (World) [h1]"
        assertNull(RomHacks.fanHack(name, null))
        assertTrue(RomHacks.isModified(name, null))
        assertTrue(RomHacks.isModified(null, "/roms/nes/Super Mario Bros. (World) [h2C].nes"))
    }

    @Test
    fun fanHackOfGameOnlyForRoms() {
        val rom = Game(name = "Pokemon Radical Red (Hack)", platform = "gba", hue = 0, sourceType = SourceType.ROM,
            romPath = "/roms/gba/Pokemon Radical Red (Hack).gba")
        assertNotNull(RomHacks.fanHack(rom))
        val app = rom.copy(sourceType = SourceType.APP, romPath = null, packageName = "com.example.hack")
        assertNull(RomHacks.fanHack(app))
    }

    // ---- negatives ----

    @Test
    fun officialPokemonTitlesAreNotHacks() {
        val official = listOf(
            "Pokemon - Silver Version (USA, Europe)",
            "Pokemon - Crystal Version (USA, Europe) (Rev 1)",
            "Pokemon - Emerald Version (USA, Europe)",
            "Pokemon - Black Version 2 (USA, Europe)",
            "Pokemon Mystery Dungeon - Explorers of Sky (USA)",
            "Pokemon - Edicion Rubi (Spain)",
            "Pocket Monsters - Crystal Version (Japan)"
        )
        for (t in official) {
            assertNull(t, RomHacks.fanHack(t, null))
            assertFalse(t, RomHacks.isModified(t, null))
            assertEquals(t, RomHacks.artSearchName(t))
        }
    }

    @Test
    fun hackSubstringInOfficialTitleIsNotAHack() {
        assertNull(RomHacks.fanHack("Hacker Evolution (USA)", null))
        assertNull(RomHacks.fanHack(".hack__Infection Part 1 (USA)", null))
        assertNull(RomHacks.fanHack(".hack//Infection Part 1 (USA)", null))
        assertNull(RomHacks.fanHack("Hack 'n' Slash", null))
    }

    @Test
    fun technicalPatchesAreNotFanHacks() {
        assertNull(RomHacks.fanHack("Super Mario 64 (USA) (Widescreen Hack)", null))
        assertNull(RomHacks.fanHack("Shadow of the Colossus (USA) (60fps Hack)", null))
        assertNull(RomHacks.fanHack("Metroid Prime (USA) (16:9 Hack)", null))
    }

    @Test
    fun translationsAndDumpFlagsAreNotHacks() {
        assertNull(RomHacks.fanHack("Mother 3 (Japan) [T+Eng]", null))
        assertFalse(RomHacks.isModified("Super Mario World (USA) [!]", null))
        assertFalse(RomHacks.isModified("Super Mario World (USA) [b1]", null))
    }

    @Test
    fun pokemonHackNameWithoutPokemonIsNotAHack() {
        // "Unbound", "Prism", "Eclipse" only count when the title also says Pokémon.
        assertNull(RomHacks.fanHack("Unbound Saga (USA)", null))
        assertNull(RomHacks.fanHack("Prism - Light the Way (USA)", null))
    }

    @Test
    fun blankInputs() {
        assertNull(RomHacks.fanHack(null, null))
        assertNull(RomHacks.fanHack("", ""))
        assertNull(RomHacks.fanHack("   ", null))
    }
}
