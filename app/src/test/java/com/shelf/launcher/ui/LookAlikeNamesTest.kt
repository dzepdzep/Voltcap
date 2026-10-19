package com.shelf.launcher.ui

import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Update 255 (#74 / #101): look-alike names keep what tells them apart, and only those. */
class LookAlikeNamesTest {
    private fun rom(name: String, platform: String = "gbc") = Game(name, platform, 0, SourceType.ROM, romPath = "/r/$platform/$name.gbc")

    private val silver = "Pokemon Silver Version (USA, Europe) (SGB Enhanced) (GB Compatible)"
    private val patched = "$silver [patched]"

    @Test fun patchedCopyKeepsItsTag() {
        val m = lookAlikeNames(listOf(rom(silver), rom(patched)), hideAll = false)
        assertEquals(stripRegionTags(patched) + " [patched]", m[patched])
        assertFalse("the plain one has nothing of its own", m.containsKey(silver))
    }

    @Test fun twoRegionsShowTheirRegion() {
        val m = lookAlikeNames(listOf(rom("Tetris DX (World)"), rom("Tetris DX (Japan)")), hideAll = false)
        assertEquals("Tetris DX (World)", m["Tetris DX (World)"])
        assertEquals("Tetris DX (Japan)", m["Tetris DX (Japan)"])
    }

    @Test fun differentConsolesAreNotLookAlikes() {
        val m = lookAlikeNames(listOf(rom("Tetris (World)", "gb"), rom("Tetris (Japan)", "nes")), hideAll = false)
        assertTrue(m.isEmpty())
    }

    @Test fun uniqueNamesAndSameFileTwiceAreLeftAlone() {
        val m = lookAlikeNames(listOf(rom("Wario Land 3 (World)"), rom("Shantae (USA)"), rom("Shantae (USA)")), hideAll = false)
        assertTrue(m.isEmpty())
    }

    @Test fun appsAreIgnored() {
        val a = Game("Retro (Free)", "android", 0, SourceType.APP, packageName = "a.b")
        val b = Game("Retro (Pro)", "android", 0, SourceType.APP, packageName = "a.c")
        assertTrue(lookAlikeNames(listOf(a, b), hideAll = true).isEmpty())
    }

    @Test fun emptyLibrary() { assertTrue(lookAlikeNames(emptyList(), hideAll = false).isEmpty()) }
}
