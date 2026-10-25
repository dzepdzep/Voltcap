package com.shelf.launcher.ui

import com.shelf.launcher.data.AppearanceSettings
import com.shelf.launcher.data.CardArtType
import com.shelf.launcher.data.Features
import com.shelf.launcher.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Update 326: the R1 compatibility audit, automated. Every R1 tile is asked (r1TileFit) for every home layout × every
 * game look × every value of the switches its rules read (only where a rule reads them), and the answers are checked
 * against what the layouts really draw. It also writes the full matrix to build/reports/r1/matrix.csv (layout × look ×
 * tile → SHOW / HIDE / GREY, with the default switches) for the compatibility report.
 */
class R1Compat326Test {

    private fun ctx(a: AppearanceSettings, second: Boolean = true) = R1Ctx(
        a, secondDisplay = second, bluetooth = true, lights = true, romm = false, raAccounts = 1,
        themeRecolorable = true, chargeLimitWorks = true
    )

    /**
     * Layout Studio's background comes from its layout file (CustomSceneNow, parsed with org.json, which the JVM tests
     * only have as Android's empty stub): the two tiles that read it are left out for that one layout here.
     */
    private fun testable(tile: String, l: HomeLayout) = !(l == HomeLayout.CUSTOM && (tile == "background" || tile == "herofit"))

    private fun fitName(f: R1Fit): String = when (f) {
        R1Fit.Show -> "SHOW"
        R1Fit.Hide -> "HIDE"
        is R1Fit.Grey -> "GREY"
    }

    /** The tile ids QuickSettings.kt builds (R1Tile("id", …) and styleTile("id", …)). */
    private fun builtTileIds(): Set<String> {
        val src = listOf(
            File("src/main/java/com/shelf/launcher/ui/QuickSettings.kt"),
            File("app/src/main/java/com/shelf/launcher/ui/QuickSettings.kt")
        ).firstOrNull { it.exists() } ?: return emptySet()
        val text = src.readText()
        return (Regex("""R1Tile\("([a-z0-9_]+)"""").findAll(text).map { it.groupValues[1] } +
            Regex("""styleTile\("([a-z0-9_]+)"""").findAll(text).map { it.groupValues[1] }).toSet()
    }

    private fun <T> withFeature(p: Features.Pref<T>, v: T, block: () -> Unit) {
        val before = p.value
        p.value = v
        try { block() } finally { p.value = before }
    }

    @Test fun everyBuiltTileHasItsOwnRule() {
        val ids = builtTileIds()
        if (ids.isEmpty()) return // source not reachable from this working directory
        val missing = ids.filter { !it.startsWith("dev_") && it !in R1_RULED_TILES }
        assertTrue("R1 tiles without a rule in r1TileFit (R1TileRules.kt): $missing", missing.isEmpty())
        // And the other way round: no rule for a tile that isn't built any more (the matrix would list a ghost tile —
        // the device walk found "drawnmedia", gone since 325).
        val ghosts = R1_RULED_TILES.filter { it !in ids }
        assertTrue("Rules for tiles QuickSettings.kt no longer builds: $ghosts", ghosts.isEmpty())
    }

    /** Every layout × look × the switches each rule reads: no tile throws, and the invariants hold. */
    @Test fun exhaustiveLayoutLookSwitchCombinations() {
        val tiles = R1_RULED_TILES.toList()
        var checked = 0
        for (l in HomeLayout.values()) for (look in CardArtType.values()) {
            val carouselChoices = if (carouselApplies(l)) listOf(false, true) else listOf(false)
            for (carousel in carouselChoices) for (spineLook in listOf(false, true)) for (gameWindow in listOf(true, false)) {
                val set = if (carousel) setOf(l.name) else emptySet()
                withFeature(Features.carouselLayouts, set) {
                    withFeature(Features.spineLook, spineLook) {
                        withFeature(Features.gameWindow, gameWindow) {
                            val a = AppearanceSettings(homeLayout = l, cardArtType = look)
                            val x = ctx(a)
                            for (t in tiles.filter { testable(it, l) }) {
                                val f = r1TileFit(t, x)
                                checked++
                                // A look tile never shows where the layout draws its games its own way.
                                if (t in setOf("gamelook", "cartlook", "artdrop") && layoutOwnLook(l) != null) {
                                    assertEquals("$t on $l / $look", R1Fit.Hide, f)
                                }
                                // Outline: only where the frame is drawn.
                                if (t == "outline") {
                                    val drawn = !l.styleHome || ((l == HomeLayout.SIDE || l == HomeLayout.STACKS) && carousel)
                                    assertEquals("outline on $l (carousel $carousel)", if (drawn) R1Fit.Show else R1Fit.Hide, f)
                                }
                                // Endless: everywhere but Hero tiles.
                                if (t == "endless") assertEquals("endless on $l", if (l == HomeLayout.HERO_TILES) R1Fit.Hide else R1Fit.Show, f)
                                // The game window's style is greyed (with a reason), never hidden, while the window is off.
                                if (t == "popupstyle") assertTrue("popupstyle", if (gameWindow) f == R1Fit.Show else f is R1Fit.Grey)
                            }
                        }
                    }
                }
            }
        }
        assertTrue(checked > 10_000)
    }

    @Test fun badgesFollowTheLayoutsThatDrawThem() {
        // Hero carousel draws badges on its covers (317al): the tile must be there to switch them.
        assertEquals(R1Fit.Show, r1TileFit("badges", ctx(AppearanceSettings(homeLayout = HomeLayout.HERO))))
        withFeature(Features.ribbonCart, true) {
            assertEquals(R1Fit.Show, r1TileFit("badges", ctx(AppearanceSettings(homeLayout = HomeLayout.POCKET_RIBBON))))
            assertEquals(R1Fit.Show, r1TileFit("progressbar", ctx(AppearanceSettings(homeLayout = HomeLayout.POCKET_RIBBON))))
        }
        withFeature(Features.ribbonCart, false) {
            assertEquals(R1Fit.Hide, r1TileFit("badges", ctx(AppearanceSettings(homeLayout = HomeLayout.POCKET_RIBBON))))
            assertEquals(R1Fit.Hide, r1TileFit("progressbar", ctx(AppearanceSettings(homeLayout = HomeLayout.POCKET_RIBBON))))
        }
        for (l in listOf(HomeLayout.DISCS, HomeLayout.CARDS, HomeLayout.SHELF, HomeLayout.POCKET_CLASSIC)) {
            assertEquals("badges on $l", R1Fit.Hide, r1TileFit("badges", ctx(AppearanceSettings(homeLayout = l))))
        }
    }

    @Test fun deviceTilesDependOnlyOnTheDevice() {
        val a = AppearanceSettings()
        assertEquals(R1Fit.Hide, r1TileFit("dual", ctx(a, second = false)))
        assertEquals(R1Fit.Show, r1TileFit("dual", ctx(a, second = true)))
        for (l in HomeLayout.values()) {
            assertEquals(R1Fit.Show, r1TileFit("wifi", ctx(AppearanceSettings(homeLayout = l))))
            assertEquals(R1Fit.Show, r1TileFit("dev_FAN", ctx(AppearanceSettings(homeLayout = l))))
        }
    }

    /** The Disc / Case / Spine picked for every game apply exactly where their tiles show (LookStyles.liveFor). */
    @Test fun lookStylePicksApplyOnlyWhereTheirTilesShow() {
        for (l in HomeLayout.values()) for (look in CardArtType.values()) {
            val a = AppearanceSettings(homeLayout = l, cardArtType = look)
            val (disc, case, spine) = LookStyles.liveFor(a)
            val x = ctx(a)
            assertEquals("disc $l/$look", r1TileFit("discstyle", x) != R1Fit.Hide, disc)
            assertEquals("case $l/$look", r1TileFit("casestyle", x) != R1Fit.Hide, case)
            assertEquals("spine $l/$look", r1TileFit("spinestyle", x) != R1Fit.Hide, spine)
        }
        // A case picked in the Cases look stops applying in the Covers look and comes back with the Cases look.
        withFeature(Features.caseStyle, "PS2") {
            LookStyles.sync(LookStyles.liveFor(AppearanceSettings(homeLayout = HomeLayout.CLASSIC, cardArtType = CardArtType.CASE)))
            assertTrue(LookStyles.caseForAll())
            LookStyles.sync(LookStyles.liveFor(AppearanceSettings(homeLayout = HomeLayout.CLASSIC, cardArtType = CardArtType.COVER)))
            assertTrue(!LookStyles.caseForAll())
            assertEquals("PS2", Features.caseStyle.value) // the pick itself is kept
            LookStyles.sync(LookStyles.liveFor(AppearanceSettings(homeLayout = HomeLayout.CLASSIC, cardArtType = CardArtType.CASE)))
            assertTrue(LookStyles.caseForAll())
        }
        LookStyles.sync(Triple(true, true, true))
    }

    /** Writes the matrix for the report (default switches). */
    @Test fun writeMatrix() {
        val out = File("build/reports/r1").apply { mkdirs() }
        val tiles = R1_RULED_TILES.sorted()
        val sb = StringBuilder("layout,look," + tiles.joinToString(",") + "\n")
        for (l in HomeLayout.values()) for (look in CardArtType.values()) {
            val x = ctx(AppearanceSettings(homeLayout = l, cardArtType = look))
            sb.append(l.name).append(',').append(look.name)
            for (t in tiles) sb.append(',').append(if (testable(t, l)) fitName(r1TileFit(t, x)) else "N/A")
            sb.append('\n')
        }
        File(out, "matrix.csv").writeText(sb.toString())
        assertTrue(File(out, "matrix.csv").length() > 0)
    }
}
