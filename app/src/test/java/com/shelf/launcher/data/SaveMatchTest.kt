package com.shelf.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Ignore
import org.junit.Test

/**
 * #79: SaveMatch — a save file's name to a library game: exact file name, tag key, loose title; ambiguous names are
 * left unassigned. (stripRegionTags uses android.util.LruCache: needs unitTests.isReturnDefaultValues = true.)
 */
class SaveMatchTest {

    private fun rom(name: String, platform: String, path: String) =
        Game(name = name, platform = platform, hue = 0, sourceType = SourceType.ROM, romPath = path)

    private val redUsa = rom("Pokemon - Red Version (USA, Europe)", "gb", "/roms/gb/Pokemon - Red Version (USA, Europe).gb")
    private val redJp = rom("Pokemon - Red Version (Japan)", "gb", "/roms/gb/Pokemon - Red Version (Japan).gb")
    private val picross = rom("Mario's Picross X (Hack)", "gb", "/roms/gb/Mario's Picross X (Hack).gb")
    private val tetrisGb = rom("Tetris (World)", "gb", "/roms/gb/Tetris (World).gb")
    private val tetrisNes = rom("Tetris (World)", "nes", "/roms/nes/Tetris (World).nes")

    @Test
    fun exactFileNameMatch() {
        val m = SaveMatch.match("Pokemon - Red Version (USA, Europe)", "gb", SaveMatch.index(listOf(redUsa, redJp)))
        assertSame(redUsa, m.game)
        assertEquals(0, m.ambiguous)
        assertSame(redUsa, SaveMatch.best("Pokemon - Red Version (USA, Europe)", "gb", SaveMatch.index(listOf(redUsa, redJp))))
    }

    @Test
    fun exactMatchIgnoresCaseAndAccents() {
        val m = SaveMatch.match("POKéMON - RED VERSION (USA, EUROPE)", "gb", SaveMatch.index(listOf(redUsa, redJp)))
        assertSame(redUsa, m.game)
    }

    @Test
    fun tagKeyMatchesUnderscoreForApostrophe() {
        val m = SaveMatch.match("Mario_s Picross X (Hack)", "gb", SaveMatch.index(listOf(picross, redUsa)))
        assertSame(picross, m.game)
    }

    @Test
    fun looseTitleMatchingTwoGamesIsAmbiguous() {
        val m = SaveMatch.match("Pokemon - Red Version", "gb", SaveMatch.index(listOf(redUsa, redJp)))
        assertNull(m.game)
        assertEquals(2, m.ambiguous)
        assertNull(SaveMatch.best("Pokemon - Red Version", "gb", SaveMatch.index(listOf(redUsa, redJp))))
    }

    @Test
    fun looseTitleMatchingOneGameIsAssigned() {
        val m = SaveMatch.match("Pokemon - Red Version", "gb", SaveMatch.index(listOf(redUsa, tetrisGb)))
        assertSame(redUsa, m.game)
    }

    @Test
    fun sameFileNameOnSameConsoleTakesTheFirstCopy() {
        val copy = rom("Tetris (World)", "gb", "/sdcard/backup/Tetris (World).gb")
        val m = SaveMatch.match("Tetris (World)", "gb", SaveMatch.index(listOf(tetrisGb, copy)))
        assertSame(tetrisGb, m.game)
        assertEquals(0, m.ambiguous)
    }

    @Test
    fun saveConsoleWinsOverOtherConsoles() {
        assertSame(tetrisNes, SaveMatch.match("Tetris (World)", "nes", SaveMatch.index(listOf(tetrisGb, tetrisNes))).game)
        assertSame(tetrisGb, SaveMatch.match("Tetris (World)", "gb", SaveMatch.index(listOf(tetrisGb, tetrisNes))).game)
        // Game Boy and Game Boy Color are one family.
        val tetrisGbc = rom("Tetris DX (World)", "gbc", "/roms/gbc/Tetris DX (World).gbc")
        assertSame(tetrisGbc, SaveMatch.match("Tetris DX (World)", "gb", SaveMatch.index(listOf(tetrisGbc))).game)
    }

    @Test
    fun severalGamesOnOtherConsolesAreNotAssigned() {
        val m = SaveMatch.match("Tetris (World)", null, SaveMatch.index(listOf(tetrisGb, tetrisNes)))
        assertNull(m.game)
        assertEquals(2, m.ambiguous)
        val m2 = SaveMatch.match("Tetris (World)", "snes", SaveMatch.index(listOf(tetrisGb, tetrisNes)))
        assertNull(m2.game)
        assertEquals(2, m2.ambiguous)
    }

    @Test
    fun exactlyOneGameOnAnotherConsoleIsAssigned() {
        assertSame(tetrisGb, SaveMatch.match("Tetris (World)", "other", SaveMatch.index(listOf(tetrisGb))).game)
        assertSame(tetrisGb, SaveMatch.match("Tetris (World)", null, SaveMatch.index(listOf(tetrisGb))).game)
    }

    @Test
    fun noMatchAndEmptyLibrary() {
        val none = SaveMatch.match("The Legend of Zelda", "nes", SaveMatch.index(listOf(redUsa, tetrisNes)))
        assertNull(none.game)
        assertEquals(0, none.ambiguous)
        val empty = SaveMatch.match("Tetris (World)", "gb", emptyList())
        assertNull(empty.game)
        assertEquals(0, empty.ambiguous)
    }

    @Test
    fun allowedFilterExcludesGames() {
        val m = SaveMatch.match("Tetris (World)", "gb", SaveMatch.index(listOf(tetrisGb, tetrisNes))) { it.platform == "nes" }
        assertSame(tetrisNes, m.game)
        val none = SaveMatch.match("Tetris (World)", "gb", SaveMatch.index(listOf(tetrisGb))) { false }
        assertNull(none.game)
        assertEquals(0, none.ambiguous)
    }

    @Test
    fun gamesWithoutRomPathAreNotIndexed() {
        val app = Game(name = "Tetris", platform = "android", hue = 0, sourceType = SourceType.APP, packageName = "com.tetris")
        assertEquals(0, SaveMatch.index(listOf(app)).size)
        assertEquals(1, SaveMatch.index(listOf(app, tetrisGb)).size)
    }

    @Test
    fun tooShortTitlesNeverLooseMatch() {
        val ab = rom("AB (USA)", "nes", "/roms/nes/AB (USA).nes")
        assertNull(SaveMatch.match("AB", "nes", SaveMatch.index(listOf(ab))).game)
    }

    @Test
    fun splitCoreRecognisesRetroArchCores() {
        assertEquals("gambatte" to "Pokemon - Red Version", SaveMatch.splitCore("Gambatte_Pokemon - Red Version"))
        assertEquals("gb", SaveMatch.platformOfCore("gambatte"))
        assertEquals("mgba" to "Pokemon Emerald", SaveMatch.splitCore("mGBA_Pokemon Emerald"))
        // Not a core / nothing after it.
        assertEquals(null to "Super_Mario_World", SaveMatch.splitCore("Super_Mario_World"))
        assertEquals(null to "snes9x_A", SaveMatch.splitCore("snes9x_A"))
        assertNull(SaveMatch.platformOfCore(null))
        assertNull(SaveMatch.platformOfCore("notacore"))
    }

    @Test
    fun foldAndTagKey() {
        assertEquals("pokemon", SaveMatch.fold("  Pokémon "))
        assertEquals(SaveMatch.tagKey("Mario's Picross X (Hack)"), SaveMatch.tagKey("Mario_s Picross X (Hack)"))
    }

    @Test
    fun samePlatform() {
        assertEquals(true, SaveMatch.samePlatform("gc", "GameCube"))
        assertEquals(true, SaveMatch.samePlatform("gb", "gbc"))
        assertEquals(false, SaveMatch.samePlatform("gb", "gba"))
        assertEquals(false, SaveMatch.samePlatform(null, "gb"))
        assertEquals(false, SaveMatch.samePlatform("other", "other"))
    }

    /**
     * Suspected bug (SaveMatch.keysMatch, SaveMatch.kt:112): a save titled with a sequel number after a space
     * ("Final Fantasy 2") loose-matches the first game ("Final Fantasy (USA)"), because "x " prefixes are accepted for
     * save-slot suffixes and the digit guard only covers the no-space case. Expected: no match.
     */
    @Test
    fun sequelSaveDoesNotLandOnFirstGame() {
        val ff1 = rom("Final Fantasy (USA)", "nes", "/roms/nes/Final Fantasy (USA).nes")
        assertNull(SaveMatch.match("Final Fantasy 2", "nes", SaveMatch.index(listOf(ff1))).game)
    }
}
