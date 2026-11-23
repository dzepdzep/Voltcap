package com.shelf.launcher.ui

import com.shelf.launcher.data.DockPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 714a (owner Task 1, Row-position collisions: "saved customizations kept, never silently erased"): a new Tile home Row
 * place (R1 Row tile / Settings "Where the tiles sit") used to delete the round buttons' hand-placed spot by the tiles.
 * Now it is kept aside for the place it was made at and comes back there; other widgets are untouched.
 */
class TileRowPlaceSpots714aTest {
    private fun src(rel: String): String =
        listOf(File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")).first { it.exists() }.readText()

    private val sc = HomeWidgets.SHORTCUTS
    private fun pos(s: String) = HomeWidgets.parse(s)

    @Test fun spotIsKeptForItsPlaceAndComesBack() {
        val start = HomeWidgets.format(mapOf(sc to (0.25f to 0.75f), HomeWidgets.MUSIC to (0.9f to 0.1f)))
        // Middle -> Top: the buttons leave their old screen spot (no collision with the moved tiles) ...
        val atTop = tileRowPlaceSpots(start, DockPosition.MIDDLE, DockPosition.TOP)
        assertNull("buttons at their normal spot at Top", pos(atTop)[sc])
        // ... but the spot is not erased: it is kept for Middle, and every other widget stays.
        assertEquals(0.25f to 0.75f, pos(atTop)[tileRowSpotKey(DockPosition.MIDDLE)])
        assertEquals(0.9f to 0.1f, pos(atTop)[HomeWidgets.MUSIC])
        // Top -> Bottom -> Middle: back at Middle the hand-placed spot returns, nothing is left aside.
        val atBottom = tileRowPlaceSpots(atTop, DockPosition.TOP, DockPosition.BOTTOM)
        val back = tileRowPlaceSpots(atBottom, DockPosition.BOTTOM, DockPosition.MIDDLE)
        assertEquals(0.25f to 0.75f, pos(back)[sc])
        assertEquals(setOf(sc, HomeWidgets.MUSIC), pos(back).keys)
    }

    @Test fun eachPlaceKeepsItsOwnSpot() {
        var s = HomeWidgets.format(mapOf(sc to (0.2f to 0.8f)))           // placed at Middle
        s = tileRowPlaceSpots(s, DockPosition.MIDDLE, DockPosition.TOP)
        s = HomeWidgets.format(pos(s) + (sc to (0.6f to 0.9f)))           // placed again at Top (Edit Home)
        s = tileRowPlaceSpots(s, DockPosition.TOP, DockPosition.MIDDLE)
        assertEquals(0.2f to 0.8f, pos(s)[sc])
        s = tileRowPlaceSpots(s, DockPosition.MIDDLE, DockPosition.TOP)
        assertEquals(0.6f to 0.9f, pos(s)[sc])
    }

    @Test fun samePlaceOrNoSpotChangesNothingSaved() {
        val start = HomeWidgets.format(mapOf(sc to (0.25f to 0.75f)))
        assertEquals(start, tileRowPlaceSpots(start, DockPosition.TOP, DockPosition.TOP))
        assertTrue(pos(tileRowPlaceSpots("", DockPosition.TOP, DockPosition.BOTTOM)).isEmpty())
        // A spot reset at its place (Edit Home Reset) leaves no stale copy for that place.
        val stale = HomeWidgets.format(mapOf(tileRowSpotKey(DockPosition.TOP) to (0.1f to 0.1f)))
        assertTrue(pos(tileRowPlaceSpots(stale, DockPosition.TOP, DockPosition.MIDDLE)).isEmpty())
    }

    @Test fun pickersPassTheNewPlaceAndNoLongerEraseTheSpot() {
        val qs = src("ui/QuickSettings.kt")
        val fn = qs.substringAfter("internal fun tileRowPlaceReset(").substringBefore("\n}\n")
        assertFalse("the old code deleted the spot", fn.contains("HomeWidgets.reset(HomeWidgets.SHORTCUTS)"))
        assertTrue(fn.contains("tileRowPlaceSpots(now, a.tileDockPosition, to)"))
        assertTrue(src("ui/SettingsDialog.kt").contains("tileRowPlaceReset(appearance, it)"))
        assertTrue(src("ui/LauncherScreen.kt").contains("tileRowPlaceReset(state.appearance, d)"))
    }
}
