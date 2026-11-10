package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Update 491 (staging only): R0 / R1 / R2 of docs/R1_PLAN_490.md and the settings search jump. */
class Batch491Test {
    private fun src(rel: String): String = listOf(
        File("src/main/java/com/shelf/launcher/$rel"), File("app/src/main/java/com/shelf/launcher/$rel")
    ).first { it.exists() }.readText()

    // ---- R0: the saved order ----

    @Test fun r0SavedOrderNeverMovesAndNewTilesGoLast() {
        val saved = listOf("music", "wifi", "layout", "dnd", "gamesize", "theme", "dev_fan", "battery", "settings")
        val built = listOf("wifi", "bt", "dnd", "music", "layout", "layoutopts", "spacing", "gamesize", "theme", "battery", "settings", "dev_fan")
        val out = r1Order(saved, built)
        // every saved tile keeps its place relative to the others
        assertEquals(saved, out.filter { it in saved })
        // the new ones come after all of them, in the order they are built
        assertEquals(listOf("bt", "layoutopts", "spacing"), out.drop(saved.size))
        // both pages: each page keeps its saved order, its new tiles last on that page
        for (device in listOf(false, true)) {
            val page = out.filter { R1Pages.onDevicePage(it) == device }
            val savedPage = saved.filter { R1Pages.onDevicePage(it) == device }
            assertEquals(savedPage, page.take(savedPage.size))
            assertTrue(page.drop(savedPage.size).none { it in saved })
        }
    }

    @Test fun r0FreshInstallKeepsTheBuiltOrderAndGoneTilesDrop() {
        val built = listOf("wifi", "bt", "dnd", "lights", "music", "saves", "layout", "layoutopts", "spacing")
        // no saved order (a fresh install, or the Thor that never rearranged): the older tiles keep their places, the new last
        val noSaved = listOf("wifi", "bt", "dnd", "layout", "layoutopts", "spacing", "magnifier", "settings")
        assertEquals(listOf("wifi", "bt", "dnd", "layout", "magnifier", "settings", "layoutopts", "spacing"), r1Order(emptyList(), noSaved))
        assertEquals(built, r1Order(emptyList(), built))
        // a saved id that isn't built here (another layout's tile, a retired tile) is skipped, not shown; duplicates once
        assertEquals(listOf("dnd", "wifi", "bt", "lights", "music", "saves", "layout", "layoutopts", "spacing"),
            r1Order(listOf("dnd", "look3d", "wifi", "dnd"), built))
    }

    @Test fun r0HiddenTilesStayHiddenAndTheTopRowIsUnchanged() {
        val q = src("ui/QuickSettings.kt")
        // the order no longer decides visibility: the hidden set filters after it, untouched
        assertTrue(q.contains("val ordered = r1Order(order, allTiles.map { it.id }).mapNotNull { byId[it] }"))
        assertTrue(q.contains("else -> pageTiles.filter { it.id !in hidden }"))
        assertTrue(!q.contains("out.add(at + 1, t)"))
        // fresh installs: the first five tiles built are the same as before 490; the new ones are built after Layout
        val ids = Regex("R1Tile\\(\"([a-z0-9_]+)\"").findAll(q).map { it.groupValues[1] }.toList()
        assertEquals(listOf("wifi", "bt", "dnd", "lights", "music"), ids.take(5))
        assertTrue(ids.indexOf("layoutopts") > ids.indexOf("layout") && ids.indexOf("spacing") > ids.indexOf("layoutopts"))
    }

    // ---- R1: Layout options ----

    @Test fun r1LayoutOptionsOpenTheLayoutsOwnGroup() {
        assertEquals("Case stacks options", layoutOptionsTarget(HomeLayout.STACKS))
        assertEquals("Cube menu options", layoutOptionsTarget(HomeLayout.CUBE))
        // layouts whose options fill the page: the Layout options page itself
        assertEquals("Layout options", layoutOptionsTarget(HomeLayout.CLASSIC))
        for (l in HomeLayout.values()) {
            val t = layoutOptionsTarget(l)
            val e = SettingsIndex.all.first { it.label == t }
            assertEquals("Home", e.section); assertEquals("Layout options", e.subpage)
        }
        val q = src("ui/QuickSettings.kt")
        // the Layout tile keeps its tap (next layout) and its hold (the list), the list gains the options button
        assertTrue(q.contains("longAction = { showLayoutList = true }) {"))
        assertTrue(q.contains("optionsLabel = \"Options for \${a.homeLayout.label}…\""))
        assertEquals(R1Fit.Show, r1TileFit("layoutopts", ctx(HomeLayout.SHELF)))
    }

    // ---- R2: Game spacing ----

    private fun ctx(l: HomeLayout) = R1Ctx(AppearanceSettings(homeLayout = l), secondDisplay = true, bluetooth = true, lights = true,
        romm = false, raAccounts = 1, themeRecolorable = true, chargeLimitWorks = true)

    @Test fun r2SpacingUsesEachLayoutsOwnSliderPref() {
        val five = mapOf(HomeLayout.CLASSIC to Features.rowSpacing, HomeLayout.TILES to Features.tilesSpacing,
            HomeLayout.SHELF to Features.shelfSpacing, HomeLayout.COVERDETAILS to Features.cdSpacing, HomeLayout.STACKS to Features.stacksSpacing,
            // (Batch 520, K1: the layouts whose own Spacing is already a percentage — the same pref, their own range.)
            HomeLayout.CASEFLOW to Features.cfSpacing, HomeLayout.BOX to Features.boxSpacing, HomeLayout.STORAGE to Features.stSpacing,
            HomeLayout.FILESELECT to Features.fsSpacing)
        for (l in HomeLayout.values()) {
            val p = R1Spacing.prefFor(l)
            if (l in five) assertTrue("$l", p === five[l]) else assertNull("$l", p)
            assertEquals("$l", if (l in five) R1Fit.Show else R1Fit.Hide, r1TileFit("spacing", ctx(l)))
        }
        // the same ranges as the Settings sliders
        assertTrue(src("ui/SettingsDialog.kt").contains("rowSpacing.value = it.coerceIn(50, 300) }, min = 50, max = 300"))
        assertTrue(src("ui/StyleHomeSettings.kt").contains("Features.stacksSpacing.value = it.coerceIn(50, 250) }, min = 50, max = 250"))
        assertEquals(300, R1Spacing.maxFor(HomeLayout.CLASSIC)); assertEquals(250, R1Spacing.maxFor(HomeLayout.STACKS))
        // separate from game size and the carousel's spacing
        val q = src("ui/QuickSettings.kt")
        val tile = q.substring(q.indexOf("add(R1Tile(\"spacing\""), q.indexOf("add(R1Tile(\"spacing\"") + 400)
        assertTrue(!tile.contains("carouselSize") && !tile.contains("GameSize") && !tile.contains("carouselSpacing"))
        // tap steps, wraps round; a saved in-between value goes to the next step up
        assertEquals(100, R1Spacing.next(80)); assertEquals(80, R1Spacing.next(150)); assertEquals(120, R1Spacing.next(105))
        assertEquals(80, R1Spacing.next(250))
    }

    // ---- Settings search: the exact group ----

    @Test fun searchJumpSettlesBeforeScrollingAndOpensAGroupResult() {
        val j = src("ui/SettingsJump.kt")
        val hit = j.substring(j.indexOf("LaunchedEffect(hit)"), j.indexOf("return Modifier"))
        // the jump is claimed (other groups fold) before the scroll, and the scroll + focus run after the page settles
        assertTrue(hit.indexOf("SettingsJump.pending = null") < hit.indexOf("req.bringIntoView()"))
        assertTrue(hit.contains("delay(JUMP_SETTLE_MS)") && hit.contains("focusReq.requestFocus()"))
        assertTrue(src("ui/FoldGroup.kt").contains("jumpAnchor(title, onHit = { foldOpen[key] = true })"))
    }
}
