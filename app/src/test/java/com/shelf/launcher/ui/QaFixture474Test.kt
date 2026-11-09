package com.shelf.launcher.ui

import com.shelf.launcher.LauncherUiState
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.SourceType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Update 474: QA fixtures are display-only and off unless asked for (the hooks file gates the intent in TestHooks): the same
 * games, keys, consoles and order; nothing in the real state is replaced while no fixture is on.
 */
class QaFixture474Test {
    private val games = listOf(
        Game("Zelda-like Quest", "SNES", 1, SourceType.ROM, romPath = "/r/a.sfc"),
        Game("Alpha Racer", "N64", 2, SourceType.ROM, romPath = "/r/b.z64"),
        Game("Middle Tales", "GBA", 3, SourceType.ROM, romPath = "/r/c.gba")
    )

    @After fun off() { QaFixture.spec.value = null; QaFixture.artDir.value = null }

    @Test fun offByDefaultAndOffReturnsTheRealStateItself() {
        val s = LauncherUiState(games = games)
        assertNull(QaFixture.spec.value)
        assertSame(s, QaFixture.dress(s))
        assertNull(QaFixture.parse("off")); assertNull(QaFixture.parse(""))
    }

    @Test fun parseKeepsRealForWhatIsNotNamed() {
        val sp = QaFixture.parse("names=long, meta=none")!!
        assertEquals(QaFixture.Spec("long", "real", "none"), sp)
        assertEquals("names=long,art=real,meta=none", sp.text)
    }

    @Test fun longNamesAreWholeAndKeysOrderConsolesStay() {
        QaFixture.spec.value = QaFixture.Spec(names = "long", meta = "none")
        val d = QaFixture.dress(LauncherUiState(games = games))
        assertEquals(games.map { it.stableKey to it.platform }, d.games.map { it.stableKey to it.platform })
        assertTrue(d.games.all { it.name.length > 60 && !it.name.endsWith("…") })
        assertTrue(d.metadata.isEmpty() && d.screenshots.isEmpty())
    }

    @Test fun dressedNamesKeepTheAlphabeticalOrder() {
        // (k474f, KONKR: long names that sorted differently made A open another game's window — Paper Mario for Pokemon Stadium 2.)
        for (mode in listOf("long", "short")) {
            val m = QaFixture.names(games, mode)
            val real = games.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).map { it.stableKey }
            val dressed = games.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { m[it.stableKey]!! }).map { it.stableKey }
            assertEquals(mode, real, dressed)
        }
        assertEquals("Aaa", QaFixture.code(0)); assertEquals("Aba", QaFixture.code(26)); assertEquals("Baa", QaFixture.code(676))
    }

    @Test fun artShapesAreTheAdvertisedOnes() {
        assertEquals(960 to 540, QaFixture.shape("wide")); assertEquals(600 to 900, QaFixture.shape("portrait"))
        assertEquals(64 to 96, QaFixture.shape("lowres")); assertNull(QaFixture.shape("real"))
    }
}
